package com.drxrathore.surakshini

import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManageLocationsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_manage_locations)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        val etName = findViewById<EditText>(R.id.etLocationName)
        val etLat = findViewById<EditText>(R.id.etLatitude)
        val etLon = findViewById<EditText>(R.id.etLongitude)
        val btnSave = findViewById<Button>(R.id.btnSaveLocation)
        loadSavedLocations()

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val latStr = etLat.text.toString().trim()
            val lonStr = etLon.text.toString().trim()

            if (name.isEmpty() || latStr.isEmpty() || lonStr.isEmpty()) {
                Toast.makeText(this@ManageLocationsActivity, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val lat = latStr.toDouble()
                val lon = lonStr.toDouble()

                lifecycleScope.launch(Dispatchers.IO) {
                    val dao = AppDatabase.getDatabase(this@ManageLocationsActivity).routeDao()

                    val newLocation = LearnedRoute(
                        routeName = name,
                        centerLatitude = lat,
                        centerLongitude = lon,
                        safeRadiusMeters = 100.0
                    )

                    dao.saveLearnedRoute(newLocation)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ManageLocationsActivity, "$name saved successfully!", Toast.LENGTH_SHORT).show()
                        etName.text.clear()
                        etLat.text.clear()
                        etLon.text.clear()

                        loadSavedLocations()
                    }
                }
            } catch (e: NumberFormatException) {
                Toast.makeText(this@ManageLocationsActivity, "Invalid coordinates. Use numbers only.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSavedLocations() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(this@ManageLocationsActivity).routeDao()
            val savedLocations = dao.getPermanentRoutes()

            withContext(Dispatchers.Main) {
                val container = findViewById<android.widget.LinearLayout>(R.id.llLocationsContainer)
                container.removeAllViews()

                for (location in savedLocations) {
                    val itemView = layoutInflater.inflate(R.layout.item_location, container, false)

                    itemView.findViewById<android.widget.TextView>(R.id.tvLocName).text = location.routeName
                    itemView.findViewById<android.widget.TextView>(R.id.tvLocCoords).text =
                        "Lat: ${location.centerLatitude} | Lon: ${location.centerLongitude}"

                    itemView.findViewById<android.widget.ImageView>(R.id.btnDeleteLocation).setOnClickListener {
                        deleteLocationFromDb(location)
                    }

                    container.addView(itemView)
                }
            }
        }
    }

    private fun deleteLocationFromDb(location: LearnedRoute) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(this@ManageLocationsActivity).routeDao()
            dao.deleteLearnedRoute(location)

            withContext(Dispatchers.Main) {
                Toast.makeText(this@ManageLocationsActivity, "${location.routeName} deleted", Toast.LENGTH_SHORT).show()
                loadSavedLocations()
            }
        }
    }
}