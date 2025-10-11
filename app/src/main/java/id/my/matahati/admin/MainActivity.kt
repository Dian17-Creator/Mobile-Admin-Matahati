package id.my.matahati.admin

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import id.my.matahati.admin.fragment.QrFragment
import id.my.matahati.admin.fragment.AbsenManualFragment

class MainActivity : AppCompatActivity() {

    private lateinit var session: SessionManager
    private var lastSelectedItemId: Int = R.id.nav_qr

    // ✅ Tambahan: launcher permission
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(applicationContext)

        val isLoggedIn = session.isLoggedIn()
        val rememberMe = session.isRememberMe()

        // 🔹 Jika belum login, arahkan ke halaman login
        if (!isLoggedIn) {
            startActivity(Intent(this, LoginPage::class.java))
            finish()
            return
        }

        // 🔹 Jika tidak centang “ingatkan saya”, reset session
        if (!rememberMe && !session.isLoggedIn()) {
            // Tidak hapus apa pun di sini, biarkan session tetap ada sampai logout manual
        }

        setContentView(R.layout.activity_main)

        // ✅ Tambahan: Daftarkan permission launcher di awal
        locationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (!granted) {
                    Toast.makeText(
                        this,
                        "Izin lokasi dibutuhkan agar fitur QR dan absen berfungsi.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        // ✅ Tambahan: Cek & minta izin lokasi langsung
        checkAndRequestLocationPermission()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // 🧩 Hapus efek oval/pill bawaan Material
        bottomNav.itemBackground = null
        bottomNav.itemRippleColor = null
        bottomNav.itemActiveIndicatorColor = null

        // Tampilkan fragment default (QR)
        replaceFragment(QrFragment())

        // Label hanya tampil di item yang aktif
        bottomNav.labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_SELECTED

        // Setup awal animasi ikon
        for (i in 0 until bottomNav.menu.size()) {
            val menuItem = bottomNav.menu.getItem(i)
            val itemView = bottomNav.findViewById<View>(menuItem.itemId)
            val icon = itemView?.findViewById<View>(com.google.android.material.R.id.icon)

            if (menuItem.itemId == bottomNav.selectedItemId) {
                icon?.scaleX = 1.25f
                icon?.scaleY = 1.25f
                lastSelectedItemId = menuItem.itemId
            } else {
                icon?.scaleX = 1f
                icon?.scaleY = 1f
            }
        }

        bottomNav.setOnItemSelectedListener { item ->
            val selectedView = bottomNav.findViewById<View>(item.itemId)
            val selectedIcon = selectedView?.findViewById<View>(com.google.android.material.R.id.icon)

            // animasi membesar untuk ikon aktif
            selectedIcon?.let {
                ObjectAnimator.ofPropertyValuesHolder(
                    it,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1.25f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.25f)
                ).apply {
                    duration = 180
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
            }

            // animasi kembali normal untuk ikon sebelumnya
            if (lastSelectedItemId != item.itemId) {
                val prevView = bottomNav.findViewById<View>(lastSelectedItemId)
                val prevIcon = prevView?.findViewById<View>(com.google.android.material.R.id.icon)
                prevIcon?.let {
                    ObjectAnimator.ofPropertyValuesHolder(
                        it,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f)
                    ).apply {
                        duration = 180
                        interpolator = AccelerateDecelerateInterpolator()
                        start()
                    }
                }
            }

            // Ganti fragment sesuai menu
            when (item.itemId) {
                R.id.nav_qr -> replaceFragment(QrFragment())
                R.id.nav_absen -> replaceFragment(AbsenManualFragment())
            }

            lastSelectedItemId = item.itemId
            true
        }
    }

    // ✅ Tambahan: Fungsi pengecekan izin lokasi
    private fun checkAndRequestLocationPermission() {
        val fineLocationPermission = Manifest.permission.ACCESS_FINE_LOCATION
        val coarseLocationPermission = Manifest.permission.ACCESS_COARSE_LOCATION

        val fineGranted = ContextCompat.checkSelfPermission(this, fineLocationPermission) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, coarseLocationPermission) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            locationPermissionLauncher.launch(fineLocationPermission)
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
