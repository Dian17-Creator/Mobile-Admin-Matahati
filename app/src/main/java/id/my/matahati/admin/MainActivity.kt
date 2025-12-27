package id.my.matahati.admin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import id.my.matahati.admin.data.Cleaner

class MainActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Bersihkan cache saat aplikasi dibuka
        Cleaner.clearAppCache(this)

        session = SessionManager(applicationContext)

        // ✅ Cek login
        val isLoggedIn = session.isLoggedIn()
        val rememberMe = session.isRememberMe()

        if (!isLoggedIn) {
            // Belum login → arahkan ke halaman login
            startActivity(Intent(this, LoginPage::class.java))
            finish()
            return
        }

        // ✅ Cek & minta izin lokasi
        locationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) {
                    Toast.makeText(
                        this,
                        "Izin lokasi dibutuhkan agar fitur QR berfungsi.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        checkAndRequestLocationPermission()

        // ✅ Langsung arahkan ke halaman QR
        val intent = Intent(this, AbsensiWajahAdmin::class.java)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    // Fungsi izin lokasi
    private fun checkAndRequestLocationPermission() {
        val fineLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        val coarseLocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION

        val fineGranted =
            ContextCompat.checkSelfPermission(this, fineLocationPermission) == PackageManager.PERMISSION_GRANTED
        val coarseGranted =
            ContextCompat.checkSelfPermission(this, coarseLocationPermission) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            locationPermissionLauncher.launch(fineLocationPermission)
        }
    }
}
