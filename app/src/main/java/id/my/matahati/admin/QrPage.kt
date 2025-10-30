package id.my.matahati.admin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.location.Geocoder
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

    // 🔁 Auto refresh QR tiap 60 detik
    LaunchedEffect(Unit) {
        while (true) {
            fetchAndGenerateQR(context) { bitmap, expTime, latitude, longitude, error ->
                qrBitmap = bitmap
                expiryTime = expTime
                lat = latitude
                lng = longitude
                errorMessage = error
                loading = false
            }

            repeat(60) {
                expiryTime?.let {
                    val diff = it.time - System.currentTimeMillis()
                    if (diff <= 0) return@repeat
                    val minutes = diff / 60000
                    val seconds = (diff % 60000) / 1000
                    remainingTime = "${minutes}m ${seconds}s"
                }
                delay(1000)
            }
        }
    }

    // 🌈 Tampilan utama dua bagian (atas QR, bawah info)
    Column(modifier = Modifier.fillMaxSize()) {

        // ===== Bagian Atas =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f)
                .background(Color(0xFFFF6F51)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 🔹 Card waktu (jam real-time)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4C4C59)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    CardWaktu()
                }

                // 🔹 Card lokasi sekarang
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4C4C59)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    CardLokasi(lat = lat, lng = lng)
                }

                //Spacer(modifier = Modifier.height(4.dp))

                // 🔹 QR Code
                when {
                    loading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Memuat QR Code...", color = Color.White)
                        }
                    }

                    errorMessage != null -> {
                        Text(
                            text = errorMessage ?: "",
                            color = Color.Red,
                            fontSize = 14.sp
                        )
                    }

                    qrBitmap != null -> {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(20.dp),
                            colors = CardDefaults.cardColors(   // 🔹 Tambahkan baris ini
                                containerColor = Color.White    // 🔹 Ubah warna background menjadi putih
                            ),
                            modifier = Modifier
                                .padding(26.dp)
                                .size(400.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
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
                .weight(0.45f)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            expiryTime?.let {
                val dateText =
                    SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(it)
                Text("QR valid until : $dateText", fontSize = 14.sp, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Time remaining : $remainingTime", fontSize = 14.sp)

            Spacer(modifier = Modifier.height(4.dp))
            Text("GPS Coordinates : ${lat ?: 0.0}, ${lng ?: 0.0}", fontSize = 14.sp)

            Spacer(modifier = Modifier.height(24.dp))

            // 🔘 Tombol Refresh dan Logout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            fetchAndGenerateQR(context) { bitmap, expTime, latitude, longitude, error ->
                                qrBitmap = bitmap
                                expiryTime = expTime
                                lat = latitude
                                lng = longitude
                                errorMessage = error
                                loading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "REFRESH TOKEN",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        if (session.isRememberMe()) {
                            session.clearSession()
                        } else {
                            session.clearLoginButKeepTemp()
                        }
                        val intent = Intent(context, LoginPage::class.java)
                        context.startActivity(intent)
                        if (context is ComponentActivity) context.finish()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF0000),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "LOGOUT",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            // 🔘 Tombol Absen Manual & Izin Manual
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        val intent = Intent(context, AbsenManual::class.java)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1976D2),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "ABSEN MANUAL",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Button(
                    onClick = {
                        val intent = Intent(context, IzinAdmin::class.java)
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "IZIN",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = waktuSekarang.value,
            color = Color.White,
            fontSize = 16.sp,
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📍 Lokasi Sekarang",
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = alamat,
            color = Color.White,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@SuppressLint("MissingPermission")
suspend fun getDeviceLocation(context: android.content.Context): Pair<Double, Double>? {
    return try {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val location = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        if (location != null) Pair(location.latitude, location.longitude) else null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun fetchAndGenerateQR(
    context: android.content.Context,
    onResult: (Bitmap?, Date?, Double?, Double?, String?) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://absensi.matahati.my.id/qrabsen.php?action=refresh_token")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw IOException("Empty response")
            val json = JSONObject(body)

            if (!json.has("token")) {
                onResult(null, null, null, null, "Token not found in response")
                return@withContext
            }

            val token = json.getString("token")
            val expiryStr = json.optString("expiry", "")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val expiryTime = sdf.parse(expiryStr)

            var coords = getDeviceLocation(context)
            var retry = 0
            while (coords == null && retry < 5) {
                delay(1000)
                coords = getDeviceLocation(context)
                retry++
            }

            val lat = coords?.first ?: 0.0
            val lng = coords?.second ?: 0.0

            try {
                val formBody = okhttp3.FormBody.Builder()
                    .add("lat", lat.toString())
                    .add("lng", lng.toString())
                    .add("token", token)
                    .build()

                val updateRequest = Request.Builder()
                    .url("https://absensi.matahati.my.id/qrabsen.php")
                    .post(formBody)
                    .build()

                client.newCall(updateRequest).execute().close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

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
            onResult(bitmap, expiryTime, lat, lng, null)
        } catch (e: Exception) {
            onResult(null, null, null, null, "Gagal memuat QR: ${e.message}")
        }
    }
}

fun generateQrBitmap(text: String, size: Int = 512): Bitmap {
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
