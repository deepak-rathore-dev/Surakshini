package com.drxrathore.surakshini

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SettingsActivity : AppCompatActivity() {

    private val AUDIO_PERMISSION_CODE = 202
    private lateinit var switchAudioAI: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val btnManageLocations = findViewById<Button>(R.id.btnManageLocations)
        btnManageLocations.setOnClickListener {
            val intent = Intent(this, ManageLocationsActivity::class.java)
            startActivity(intent)
        }

        val btnManageContacts = findViewById<Button>(R.id.btnManageContacts)
        btnManageContacts.setOnClickListener {
            val intent = Intent(this, TrustedContactsActivity::class.java)
            startActivity(intent)
        }

        val btnSignOut = findViewById<Button>(R.id.btnSignOut)
        btnSignOut.setOnClickListener {
            val sharedPref = getSharedPreferences("SurakshiniPrefs", Context.MODE_PRIVATE)
            sharedPref.edit().putBoolean("isLoggedIn", false).apply()

            Toast.makeText(this, "Signed out securely", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        switchAudioAI = findViewById(R.id.switchAudioAI)

        val sharedPreferences = getSharedPreferences("SurakshiniPrefs", Context.MODE_PRIVATE)
        val isAudioAIEnabled = sharedPreferences.getBoolean("isAudioAIEnabled", false)
        switchAudioAI.isChecked = isAudioAIEnabled

        switchAudioAI.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkAudioPermission()
            } else {
                saveAudioState(false)
                Toast.makeText(this, "Audio AI Disarmed", Toast.LENGTH_SHORT).show()
                // Keep this to kill the mic if the user turns it off during an active anomaly
                stopAudioService()
            }
        }
    }

    private fun checkAudioPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_CODE)
        } else {
            saveAudioState(true)
            // UPDATED: Just arming the system, not starting the mic permanently
            Toast.makeText(this, "Audio AI Armed (Will wake on anomaly)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveAudioState(true)
                // UPDATED: Just arming the system
                Toast.makeText(this, "Mic Access Granted. Audio AI Armed.", Toast.LENGTH_SHORT).show()
            } else {
                switchAudioAI.isChecked = false
                saveAudioState(false)
                Toast.makeText(this, "Permission Denied. Audio AI requires mic access.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveAudioState(isEnabled: Boolean) {
        val sharedPreferences = getSharedPreferences("SurakshiniPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("isAudioAIEnabled", isEnabled)
        editor.apply()
    }

    private fun stopAudioService() {
        val serviceIntent = Intent(this, AudioListenService::class.java)
        stopService(serviceIntent)
    }
}