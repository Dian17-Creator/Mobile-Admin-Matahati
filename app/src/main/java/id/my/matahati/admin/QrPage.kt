package id.my.matahati.admin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QrPage : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Pastikan izin lokasi diberikan
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                100
            )
        }

        val session = SessionManager(applicationContext)
        SessionManager.SessionCache.tempPassword = session.getTempPassword()

        setContent {
            QrUi()
        }
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "QR Page Preview"
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrUi() {
    val context = LocalContext.current
    val session = SessionManager(context.applicationContext)
    val scope = rememberCoroutineScope()

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var expiryTime by remember { mutableStateOf<Date?>(null) }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var remainingTime by remember { mutableStateOf("") }
    var qrUpdatedMessage by remember { mutableStateOf("") }


    // ✅ QR auto-refresh yang sinkron dengan backend
    LaunchedEffect(Unit) {
        var currentToken: String? = null

        while (true) {
            // Ambil QR pertama kali
            fetchAndGenerateQR(context) { bitmap, expTime, latitude, longitude, error, token ->
                qrBitmap = bitmap
                expiryTime = expTime
                lat = latitude
                lng = longitude
                errorMessage = error
                loading = false
                currentToken = token // ✅ simpan token dari server
            }

            // Hitung mundur selama masa aktif token
            repeat(60) {
                expiryTime?.let {
                    val diff = it.time - System.currentTimeMillis()
                    if (diff <= 0) return@repeat
                    val minutes = diff / 60000
                    val seconds = (diff % 60000) / 1000
                    remainingTime = "${minutes}m ${seconds}s"
                }

                // ✅ Cek token setiap 10 detik
                if (it % 10 == 0 && currentToken != null) {
                    val used = checkTokenStatus(currentToken!!)
                    if (used) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                context,
                                "✅ QR diperbarui otomatis!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }

                        // 🔥 Langsung update QR di UI thread biar cepat
                        withContext(Dispatchers.Main) {
                            loading = true
                        }

                        fetchAndGenerateQR(context) { bitmap, expTime, latitude, longitude, error, token ->
                            qrBitmap = bitmap
                            expiryTime = expTime
                            lat = latitude
                            lng = longitude
                            errorMessage = error
                            loading = false
                            currentToken = token
                        }

                        delay(1000) // beri sedikit jeda biar server sempat generate QR baru
                        return@repeat
                    }
                }
            }
        }
    }

    // 🌈 Tampilan utama dua bagian (atas QR, bawah info)
    Column(modifier = Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f)
                .background(Color(0xFFB63352)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp), // 🔹 Kurangi padding
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4C4C59)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    CardWaktu()
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp), // 🔹 Kurangi padding
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4C4C59)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    CardLokasi(lat = lat, lng = lng)
                }

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    loading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 32.dp)
                        ) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Memuat QR Code...", color = Color.White)
                        }
                    }

                    errorMessage != null -> {
                        Text(
                            text = errorMessage ?: "",
                            color = Color.Red,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    qrBitmap != null -> {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(25.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .aspectRatio(1f)
                                .padding(16.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap!!.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.FillBounds
                                )
                            }
                        }

                        // 🟢 Tambahkan di sini
                        if (qrUpdatedMessage.isNotEmpty()) {
                            Text(
                                text = qrUpdatedMessage,
                                color = Color.White,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // ===== Bagian Bawah =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f) // 🔹 Sesuaikan weight
                .padding(horizontal = 2.dp, vertical = 16.dp), // 🔹 Kurangi padding
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Informasi QR
            expiryTime?.let {
                val dateText =
                    SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(it)
                Text(
                    "QR valid until: $dateText",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Text(
                "Time remaining: $remainingTime",
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                "GPS Coordinates: ${lat ?: 0.0}, ${lng ?: 0.0}",
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        fetchAndGenerateQR(context) { bitmap, expTime, latitude, longitude, error, token ->
                            qrBitmap = bitmap
                            expiryTime = expTime
                            lat = latitude
                            lng = longitude
                            errorMessage = error
                            loading = false
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 15.dp),
                shape = RoundedCornerShape(5.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4C4C59)
                )
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Refresh",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 🔹 Komponen waktu real-time di atas QR
 */
@Composable
fun CardWaktu() {
    val waktuSekarang = remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy - HH:mm:ss", Locale("id", "ID"))
            waktuSekarang.value = sdf.format(Date())
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp), // 🔹 Kurangi padding
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = waktuSekarang.value,
            color = Color.White,
            fontSize = 14.sp, // 🔹 Sesuaikan font size
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 📍 Komponen lokasi terkini (koordinat dan alamat)
 */
@Composable
fun CardLokasi(lat: Double?, lng: Double?) {
    val context = LocalContext.current
    var alamat by remember { mutableStateOf("Mendapatkan lokasi...") }

    LaunchedEffect(lat, lng) {
        if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
            try {
                val geocoder = Geocoder(context, Locale("id", "ID"))
                val hasil = geocoder.getFromLocation(lat, lng, 1)
                alamat = hasil?.firstOrNull()?.getAddressLine(0) ?: "Lokasi tidak ditemukan"
            } catch (e: Exception) {
                alamat = "Gagal mendapatkan nama lokasi"
            }
        } else {
            alamat = "Menunggu lokasi GPS..."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp), // 🔹 Kurangi padding
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📍 Lokasi Sekarang",
            color = Color.White,
            fontSize = 12.sp, // 🔹 Sesuaikan font size
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = alamat,
            color = Color.White,
            fontSize = 10.sp, // 🔹 Sesuaikan font size
            textAlign = TextAlign.Center,
            maxLines = 2, // 🔹 Batasi jumlah baris
            overflow = TextOverflow.Ellipsis
        )
    }
}

@SuppressLint("MissingPermission")
suspend fun getDeviceLocation(context: Context): Pair<Double, Double>? {
    return try {
        val fused = LocationServices.getFusedLocationProviderClient(context)

        // 🔹 coba last known location dulu (PALING STABIL)
        val last = fused.lastLocation.await()
        if (last != null && last.latitude != 0.0 && last.longitude != 0.0) {
            return Pair(last.latitude, last.longitude)
        }

        // 🔹 fallback ke current location
        val current = fused.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).await()

        if (current != null && current.latitude != 0.0 && current.longitude != 0.0) {
            return Pair(current.latitude, current.longitude)
        }

        null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ✅ Fungsi perbaikan: sekarang selalu mengembalikan token yang valid ke pemanggil
suspend fun fetchAndGenerateQR(
    context: android.content.Context,
    onResult: (Bitmap?, Date?, Double?, Double?, String?, String?) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                //.url("https://absensi.matahati.my.id/qrabsen.php?action=refresh_token")
                .url("https://absensi.karyatra.cloud/qrabsen.php?action=refresh_token")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw IOException("Empty response")
            val json = JSONObject(body)

            if (!json.has("token")) {
                onResult(null, null, null, null, "Token not found in response", null)
                return@withContext
            }

            val token = json.getString("token") // ✅ ambil token dari backend
            val expiryStr = json.optString("expiry", "")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val expiryTime = sdf.parse(expiryStr)

            // Ambil lokasi device
            var coords = getDeviceLocation(context)
            var retry = 0
            while (coords == null && retry < 5) {
                delay(1000)
                coords = getDeviceLocation(context)
                retry++
            }

            if (coords == null) {
                onResult(null, null, null, null, "Menunggu GPS aktif...", null)
                return@withContext
            }

            val lat = coords.first
            val lng = coords.second

            try {
                val formBody = okhttp3.FormBody.Builder()
                    .add("lat", lat.toString())
                    .add("lng", lng.toString())
                    .add("token", token)
                    .build()

                val updateRequest = Request.Builder()
                    //.url("https://absensi.matahati.my.id/qrabsen.php")
                    .url("https://absensi.karyatra.cloud/qrabsen.php")
                    .post(formBody)
                    .build()

                client.newCall(updateRequest).execute().close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Buat QR baru dengan payload JSON
            val session = SessionManager(context)
            val userId = session.getUserId()

            val payload = JSONObject().apply {
                put("token", token)
                put("lat", lat)
                put("lng", lng)
                put("userId", userId)
            }

            val qrText = payload.toString()
            val bitmap = generateQrBitmap(qrText)

            onResult(bitmap, expiryTime, lat, lng, null, token) // ✅ kirim token ke caller
        } catch (e: Exception) {
            onResult(null, null, null, null, "Gagal memuat QR: ${e.message}", null)
        }
    }
}

// 🔹 FUNGSI GENERATE QR YANG DIPERBAIKI
fun generateQrBitmap(text: String, size: Int = 1000): Bitmap {
    val bitMatrix: BitMatrix = MultiFormatWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        size,
        size,
        null
    )
    val width = bitMatrix.width
    val height = bitMatrix.height
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bmp
}

suspend fun checkTokenStatus(token: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
//                .url("https://absensi.matahati.my.id/check_token_status.php?token=" +
//                        java.net.URLEncoder.encode(token, "UTF-8"))
                .url("https://absensi.karyatra.cloud/check_token_status.php?token=" +
                        java.net.URLEncoder.encode(token, "UTF-8"))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext false
            val json = JSONObject(body)

            // Server harus kembalikan { "success": true, "fused": 1 } kalau sudah dipakai
            if (json.optBoolean("success", false)) {
                return@withContext json.optInt("fused", 0) == 1
            }

            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

@Composable
fun ActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {

        Card(
            modifier = Modifier
                .size(56.dp)
                .clickable { onClick() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color(0xFFB63352), // 🔥 MAROON
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = Color(0xFFB63352), // 🔥 MAROON
            maxLines = 2,
            lineHeight = 14.sp
        )
    }
}
