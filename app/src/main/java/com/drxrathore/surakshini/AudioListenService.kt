package com.drxrathore.surakshini

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream

class AudioListenService : Service() {

    private val CHANNEL_ID = "AudioAILocalChannel"
    private var serviceJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private val SAMPLE_RATE = 16000
    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private lateinit var audioInterpreter: Interpreter

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        try {
            audioInterpreter = Interpreter(loadModelFile("surakshini_audio_ai.tflite"))
            Log.d("AudioAI", "✅ Audio Model Loaded Successfully")
        } catch (e: Exception) {
            Log.e("AudioAI", "❌ Error loading model: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(101, notification)

        startListening()

        return START_STICKY
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            BUFFER_SIZE
        )

        audioRecord?.startRecording()

        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            val audioBuffer = ShortArray(SAMPLE_RATE)

            while (isActive) {
                val readResult = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0

                if (readResult > 0) {
                    val features = extractAudioFeatures(audioBuffer)

                    val input = arrayOf(features)
                    val output = Array(1) { FloatArray(3) }
                    audioInterpreter.run(input, output)

                    val noiseProb = output[0][0]
                    val verbalProb = output[0][1]
                    val screamProb = output[0][2]

                    Log.d("AudioAI", "Noise: %.2f | Verbal: %.2f | Scream: %.2f".format(noiseProb, verbalProb, screamProb))

                    if (screamProb > 0.60f || verbalProb > 0.60f) {
                        Log.d("AudioAI", "🚨 DISTRESS DETECTED! TRIGGERING SOS!")
                        triggerEmergencyAppWakeup()

                        delay(10000)
                    }
                }
                delay(100)
            }
        }
    }

    private fun extractAudioFeatures(buffer: ShortArray): FloatArray {
        val features = FloatArray(40)
        val chunkSize = buffer.size / 40

        for (i in 0 until 40) {
            var sum = 0f
            for (j in 0 until chunkSize) {
                sum += Math.abs(buffer[i * chunkSize + j].toInt())
            }
            val avgAmplitude = (sum / chunkSize) / 32767.0f
            features[i] = avgAmplitude * 5.0f
        }
        return features
    }

    private fun triggerEmergencyAppWakeup() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        intent.putExtra("AUDIO_SOS_TRIGGERED", true)
        startActivity(intent)
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        return inputStream.channel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioInterpreter.close()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Surakshini Audio AI")
            .setContentText("Actively monitoring for distress sounds...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Standard Android mic icon
            .setColor(Color.parseColor("#EF5350"))
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Audio AI", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }
}