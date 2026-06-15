package com.drxrathore.surakshini

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.*
import kotlin.math.sqrt
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import org.tensorflow.lite.Interpreter

// --- SMS & Evidence Imports ---
import android.os.Build
import android.telephony.SmsManager
import android.media.MediaRecorder
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HomeActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var latestX = 0.0f
    private var latestY = 0.0f
    private var latestZ = 0.0f
    private var wasStruggleDetectedRecently = false
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var lastAcceleration = SensorManager.GRAVITY_EARTH

    private var isAlertCooldown = false
    private var isListeningForScream = false
    private val STRUGGLE_THRESHOLD = 10.0f
    private val struggleTimestamps = mutableListOf<Long>()

    private var currentLat = 0.0
    private var currentLon = 0.0

    private lateinit var gpsInterpreter: org.tensorflow.lite.Interpreter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private lateinit var tvLiveSensorData: TextView
    private lateinit var tvFallStatus: TextView
    private lateinit var tvMotionStatus: TextView

    private lateinit var cameraExecutor: ExecutorService
    private var mediaRecorder: MediaRecorder? = null
    private var frontPhotoUri: Uri? = null
    private var backPhotoUri: Uri? = null
    private var audioUrl: String = ""
    private var frontPhotoUrl: String = ""
    private var backPhotoUrl: String = ""

    private var safeZones: List<LearnedRoute> = emptyList()

    private var activeDialog: android.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        isListeningForScream = false
        stopAudioService()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvLiveSensorData = findViewById(R.id.tvLiveSensorData)
        tvFallStatus = findViewById(R.id.tvFallStatus)
        tvMotionStatus = findViewById(R.id.tvMotionStatus)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        try {
            gpsInterpreter = org.tensorflow.lite.Interpreter(loadModelFile("surakshini_gps_ai.tflite"))
        } catch (e: Exception) { Log.e("AI", "GPS Model fail") }

        cameraExecutor = Executors.newSingleThreadExecutor()

        checkPermissions()
        runLearningAgent()

        findViewById<android.widget.ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        handleAudioTrigger(intent)

        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(this@HomeActivity).routeDao()
            val presentationZone = LearnedRoute(
                routeName = "Presentation Room",
                centerLatitude = 13.1729659,
                centerLongitude = 77.5365695,
                safeRadiusMeters = 500.0
            )
            dao.saveLearnedRoute(presentationZone)
            val routes = dao.getPermanentRoutes()
            withContext(Dispatchers.Main) {
                safeZones = routes
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAudioTrigger(intent)
    }

    private fun handleAudioTrigger(intent: Intent?) {
        val isAudioTrigger = intent?.getBooleanExtra("AUDIO_SOS_TRIGGERED", false) ?: false
        if (isAudioTrigger) {
            intent?.removeExtra("AUDIO_SOS_TRIGGERED")

            if (!isAlertCooldown) {
                tvMotionStatus.text = "ACOUSTIC ANOMALY DETECTED!"
                tvMotionStatus.setTextColor(Color.parseColor("#EF5350"))
                triggerDangerAlert("VOCAL DISTRESS / SCREAM DETECTED!")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationTracking()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(this@HomeActivity).routeDao()
            val routes = dao.getPermanentRoutes()
            withContext(Dispatchers.Main) {
                safeZones = routes
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        activeDialog?.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        releaseAudioHardware()
        activeDialog?.dismiss()
        try { ProcessCameraProvider.getInstance(this).get().unbindAll() } catch (e: Exception) {}
    }

    private fun isCurrentLocationInSafeZone(): Boolean {
        for (zone in safeZones) {
            val results = FloatArray(1)
            Location.distanceBetween(currentLat, currentLon, zone.centerLatitude, zone.centerLongitude, results)
            if (results[0] <= zone.safeRadiusMeters) return true
        }
        return false
    }

    private fun startAudioServiceIfNeeded() {
        val sharedPreferences = getSharedPreferences("SurakshiniPrefs", Context.MODE_PRIVATE)
        if (!sharedPreferences.getBoolean("isAudioAIEnabled", false)) return

        try {
            val serviceIntent = Intent(this, AudioListenService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Log.d("Surakshini", "Mic WAKE UP Commanded!")
        } catch (e: Exception) { Log.e("AudioService", "Mic Wake Fail") }
    }

    private fun stopAudioService() {
        stopService(Intent(this, AudioListenService::class.java))
        Log.d("Surakshini", "Mic KILLED / SLEEPING.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            latestX = event.values[0]; latestY = event.values[1]; latestZ = event.values[2]
            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((latestX * latestX + latestY * latestY + latestZ * latestZ).toDouble()).toFloat()
            val delta = Math.abs(currentAcceleration - lastAcceleration)
            val currentTime = System.currentTimeMillis()

            if (delta > STRUGGLE_THRESHOLD && !isAlertCooldown) {

                wasStruggleDetectedRecently = true

                struggleTimestamps.add(currentTime)
                struggleTimestamps.removeAll { it < currentTime - 5000 }

                if (struggleTimestamps.size >= 2) {
                    struggleTimestamps.clear()

                    if (isCurrentLocationInSafeZone()) {
                        isListeningForScream = true
                        startAudioServiceIfNeeded()
                        tvFallStatus.text = "SAFE ZONE STRUGGLE! LISTENING (30s)..."
                        tvFallStatus.setTextColor(Color.parseColor("#FFA726"))

                        Handler(Looper.getMainLooper()).postDelayed({
                            isListeningForScream = false
                            if (!isAlertCooldown) {
                                stopAudioService()
                                tvFallStatus.text = "Safe Zone Active (Mic Sleeping)"
                                tvFallStatus.setTextColor(Color.parseColor("#00E676"))

                                tvMotionStatus.text = "✓ Safe Zone Active\nLat: %.4f | Lon: %.4f".format(currentLat, currentLon)
                                tvMotionStatus.setTextColor(Color.parseColor("#00E676"))
                            }
                        }, 30000)

                    } else {
                        isListeningForScream = true
                        startAudioServiceIfNeeded()
                        tvFallStatus.text = "DANGER ZONE STRUGGLE! LISTENING FOR SCREAM..."
                        tvFallStatus.setTextColor(Color.parseColor("#FFA726"))
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.SEND_SMS, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val permissionsToRequest = permissions.filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (permissionsToRequest.isNotEmpty()) ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 101)
        else startLocationTracking()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startLocationTracking()
    }

    private fun startLocationTracking() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).setMinUpdateIntervalMillis(5000).build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    currentLat = location.latitude
                    currentLon = location.longitude

                    val inSafeZone = isCurrentLocationInSafeZone()
                    val isNight = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in listOf(21..23, 0..5).flatten()

                    lifecycleScope.launch(Dispatchers.IO) {
                        val dao = AppDatabase.getDatabase(this@HomeActivity).routeDao()
                        val routePoint = RouteData(
                            timestamp = System.currentTimeMillis(),
                            latitude = currentLat,
                            longitude = currentLon,
                            isNightTime = isNight,
                            accelX = latestX,
                            accelY = latestY,
                            accelZ = latestZ,
                            accelTotal = currentAcceleration,
                            isStruggleDetected = wasStruggleDetectedRecently
                        )
                        dao.insertRoutePoint(routePoint)
                        wasStruggleDetectedRecently = false

                        withContext(Dispatchers.Main) {
                            if (inSafeZone) {
                                if (!isListeningForScream && !isAlertCooldown) {
                                    stopAudioService()
                                    tvMotionStatus.text = "✓ Safe Zone Active (Mic Sleeping)\nLat: %.4f | Lon: %.4f".format(currentLat, currentLon)
                                    tvMotionStatus.setTextColor(Color.parseColor("#00E676"))
                                } else if (isListeningForScream && !isAlertCooldown) {
                                    tvMotionStatus.text = "Safe Zone Active (MIC LISTENING)\nLat: %.4f | Lon: %.4f".format(currentLat, currentLon)
                                    tvMotionStatus.setTextColor(Color.parseColor("#FFA726"))
                                }
                            } else {
                                tvMotionStatus.text = "Outside Safe Zone\nLat: %.4f | Lon: %.4f".format(currentLat, currentLon)
                                tvMotionStatus.setTextColor(Color.parseColor("#FFA726"))
                            }
                        }
                    }
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    private fun runLearningAgent() { }

    private fun triggerDangerAlert(reason: String) {
        if (isAlertCooldown || isFinishing) return
        isAlertCooldown = true
        tvFallStatus.text = reason
        tvFallStatus.setTextColor(Color.parseColor("#CF6679"))
        showVerificationDialog()
    }

    private fun showVerificationDialog() {
        if (isFinishing) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_verification, null)
        activeDialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        activeDialog?.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        val tvCountdown = dialogView.findViewById<TextView>(R.id.tvCountdown)
        var timeLeft = 10
        val handler = Handler(Looper.getMainLooper())

        val runnable = object : Runnable {
            override fun run() {
                timeLeft--
                tvCountdown.text = timeLeft.toString()
                if (timeLeft > 0) {
                    handler.postDelayed(this, 1000)
                } else {
                    if (!isFinishing && !isDestroyed) {
                        activeDialog?.dismiss()
                    }
                    executeEmergencyProtocol()
                }
            }
        }
        handler.postDelayed(runnable, 1000)

        dialogView.findViewById<Button>(R.id.btnSendSosNow).setOnClickListener {
            handler.removeCallbacks(runnable)
            if (!isFinishing && !isDestroyed) {
                activeDialog?.dismiss()
            }
            executeEmergencyProtocol()
        }

        dialogView.findViewById<Button>(R.id.btnCancelSos).setOnClickListener {
            handler.removeCallbacks(runnable)
            if (!isFinishing && !isDestroyed) {
                activeDialog?.dismiss()
            }
            isAlertCooldown = false
            isListeningForScream = false

            stopAudioService()

            tvFallStatus.text = "✓ False Alarm Cancelled. Mic Sleeping."
            tvFallStatus.setTextColor(Color.parseColor("#B3B3B3"))

            if (isCurrentLocationInSafeZone()) {
                tvMotionStatus.text = "Safe Zone Active (Mic Sleeping)\nLat: %.4f | Lon: %.4f".format(currentLat, currentLon)
                tvMotionStatus.setTextColor(Color.parseColor("#00E676"))
            }
        }
        activeDialog?.show()
    }

    private fun executeEmergencyProtocol() {
        tvFallStatus.text = "SECURING EVIDENCE..."
        tvFallStatus.setTextColor(Color.parseColor("#F59E0B"))

        isListeningForScream = false
        stopAudioService()

        Handler(Looper.getMainLooper()).postDelayed({

            val audioFile = File(cacheDir, "sos_voice.mp4")
            try {
                mediaRecorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)
                    setAudioSamplingRate(44100)
                    setOutputFile(audioFile.absolutePath)
                    prepare()
                    start()
                }
            } catch (e: Exception) { Log.e("Evidence", "Mic Fail: ${e.message}") }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                val provider = cameraProviderFuture.get()
                takePhoto(provider, CameraSelector.DEFAULT_FRONT_CAMERA) { fUri ->
                    frontPhotoUri = fUri
                    Handler(Looper.getMainLooper()).postDelayed({
                        takePhoto(provider, CameraSelector.DEFAULT_BACK_CAMERA) { bUri ->
                            backPhotoUri = bUri
                            provider.unbindAll()

                            Handler(Looper.getMainLooper()).postDelayed({
                                releaseAudioHardware()
                                uploadEvidenceToSupabase(audioFile)
                            }, 5000)
                        }
                    }, 1200)
                }
            }, ContextCompat.getMainExecutor(this))

        }, 500) 
    }

    private fun releaseAudioHardware() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {}
    }

    private fun takePhoto(provider: ProcessCameraProvider, selector: CameraSelector, callback: (Uri) -> Unit) {
        val cap = ImageCapture.Builder().setTargetResolution(Size(640, 480)).build()
        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, cap)
            val file = File(cacheDir, "SOS_${System.currentTimeMillis()}.jpg")
            val opts = ImageCapture.OutputFileOptions.Builder(file).build()

            Handler(Looper.getMainLooper()).postDelayed({
                cap.takePicture(opts, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(res: ImageCapture.OutputFileResults) { callback(Uri.fromFile(file)) }
                    override fun onError(e: ImageCaptureException) { callback(Uri.EMPTY) }
                })
            }, 700)
        } catch (e: Exception) { callback(Uri.EMPTY) }
    }

    private fun uploadEvidenceToSupabase(audioFile: File) {
        tvFallStatus.text = "UPLOADING TO SECURE VAULT..."
        val ts = System.currentTimeMillis()
        val supabaseUrl = "https://smmnjdsjeffkkqhytydy.supabase.co"
        val anonKey = "sb_publishable_1V883NEbUXh63ilJT_9ppA_toq375bO"
        val client = okhttp3.OkHttpClient()

        var uploadCount = 0
        val checkDone = { uploadCount++; if (uploadCount == 3) dispatchFinalSMS() }

        fun doUpload(file: File, mime: String, name: String, onUrl: (String) -> Unit) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    if (!file.exists() || file.length() == 0L) { onUrl(""); return@launch }
                    val req = okhttp3.Request.Builder()
                        .url("$supabaseUrl/storage/v1/object/evidence/$name")
                        .post(file.asRequestBody(mime.toMediaTypeOrNull()))
                        .addHeader("Authorization", "Bearer $anonKey")
                        .addHeader("apikey", anonKey)
                        .build()

                    val res = client.newCall(req).execute()
                    if (res.isSuccessful) onUrl("$supabaseUrl/storage/v1/object/public/evidence/$name")
                    else onUrl("")
                } catch (e: Exception) { onUrl("") }
                withContext(Dispatchers.Main) { checkDone() }
            }
        }

        doUpload(audioFile, "audio/mp4", "${ts}_a.mp4") { audioUrl = it }
        frontPhotoUri?.path?.let { doUpload(File(it), "image/jpeg", "${ts}_f.jpg") { frontPhotoUrl = it } } ?: checkDone()
        backPhotoUri?.path?.let { doUpload(File(it), "image/jpeg", "${ts}_b.jpg") { backPhotoUrl = it } } ?: checkDone()
    }

    private fun dispatchFinalSMS() {
        lifecycleScope.launch(Dispatchers.IO) {
            val contacts = AppDatabase.getDatabase(this@HomeActivity).contactDao().getAllContacts()
            withContext(Dispatchers.Main) {
                if (contacts.isNotEmpty() && ActivityCompat.checkSelfPermission(this@HomeActivity, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(SmsManager::class.java) else SmsManager.getDefault()

                    val mapsLink = "https://www.google.com/maps?q=$currentLat,$currentLon"

                    val message = "🚨 SOS! My Location: $mapsLink\n\nFront Photo: $frontPhotoUrl\nBack Photo: $backPhotoUrl\nAudio Evidence: $audioUrl"

                    for (person in contacts) {
                        try {
                            val parts = smsManager.divideMessage(message)
                            smsManager.sendMultipartTextMessage(person.phoneNumber, null, parts, null, null)
                        } catch (e: Exception) {
                            Log.e("SMS", "Failed to send to ${person.phoneNumber}")
                        }
                    }
                    Toast.makeText(this@HomeActivity, "SOS Sent to Emergency Contacts!", Toast.LENGTH_LONG).show()
                }
                tvFallStatus.text = "SOS Complete. System Reset."
                isAlertCooldown = false
                isListeningForScream = false

                stopAudioService()
            }
        }
    }

    private fun loadModelFile(modelName: String): java.nio.MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        val inputStream = java.io.FileInputStream(fileDescriptor.fileDescriptor)
        return inputStream.channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    private fun minMaxNormalize(value: Double, min: Double, max: Double): Float = ((value - min) / (max - min)).toFloat()
}