package com.example.gpsexample

import android.Manifest
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import com.example.gpsexample.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var trackingLocation: Boolean = false
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvLocation.text = ""
        binding.tvLocation.movementMethod = ScrollingMovementMethod()

        // Meminta izin lokasi
        val locationPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    // Izin lokasi presisi diberikan
                }
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    // Hanya izin lokasi perkiraan yang diberikan
                }
                else -> {
                    // Tidak diberikan izin lokasi
                    binding.btnUpdate.isEnabled = false
                }
            }
        }

        // Meluncurkan permintaan izin lokasi
        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))

        // Menginisialisasi FusedLocationClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Memeriksa pengaturan lokasi
        val builder = LocationSettingsRequest.Builder()
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // Mengatur permintaan lokasi
            locationRequest = LocationRequest.create().apply {
                interval = TimeUnit.SECONDS.toMillis(20)
                fastestInterval = TimeUnit.SECONDS.toMillis(10)
                maxWaitTime = TimeUnit.SECONDS.toMillis(40)
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            // Menyiapkan callback lokasi
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    if (locationResult.locations.isNotEmpty()) {
                        val location = locationResult.lastLocation
                        val latitude = location.latitude.toString()
                        val longitude = location.longitude.toString()
                        logResultsToScreen("$latitude, $longitude")
                    }
                }
            }
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    // Menampilkan dialog untuk mengatur pengaturan lokasi jika diperlukan
                    exception.startResolutionForResult(this@MainActivity, 100)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Mengabaikan error
                }
            }
        }

        // Memeriksa izin lokasi
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // Mendapatkan lokasi terakhir yang diketahui
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            logResultsToScreen("${location?.latitude}, ${location?.longitude}")
        }

        // Menangani klik tombol untuk memulai/berhenti pembaruan lokasi
        binding.btnUpdate.setOnClickListener {
            if (!trackingLocation) {
                startLocationUpdates()
                trackingLocation = true
            } else {
                stopLocationUpdates()
                trackingLocation = false
            }
            updateButtonState(trackingLocation)
        }
    }

    // Menampilkan hasil lokasi ke layar
    private fun logResultsToScreen(output: String) {
        val outputWithPreviousLogs = "$output\n${binding.tvLocation.text}"
        binding.tvLocation.text = outputWithPreviousLogs
    }

    // Memperbarui teks tombol berdasarkan status pelacakan lokasi
    private fun updateButtonState(trackingLocation: Boolean) {
        if (trackingLocation) {
            binding.btnUpdate.text = getString(R.string.stop_update)
        } else {
            binding.btnUpdate.text = getString(R.string.start_update)
        }
    }

    // Memulai pembaruan lokasi
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        Log.d(TAG, "Request Location Updates")
    }

    // Berhenti dari pembaruan lokasi
    private fun stopLocationUpdates() {
        val removeTask = fusedLocationClient.removeLocationUpdates(locationCallback)
        removeTask.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "Location Callback removed.")
            } else {
                Log.d(TAG, "Failed to remove Location Callback.")
            }
        }
    }

    // Menangani saat aplikasi dihentikan (onPause)
    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }
}
