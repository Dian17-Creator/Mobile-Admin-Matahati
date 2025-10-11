package id.my.matahati.admin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.background

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
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
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
    name = "Halaman QR Preview"
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

            // countdown detik demi detik
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
                .background(
                    color = Color(0xFFFF6F51)
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                loading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFFF6F51))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Memuat QR Code...")
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
                    // QR Code dengan sedikit bayangan biar menonjol
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(8.dp),
                        modifier = Modifier
                            .padding(24.dp)
                            .size(300.dp)
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
                Text(
                    "QR valid until : $dateText",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Time remaining : $remainingTime", fontSize = 14.sp)

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "GPS Coordinates : ${lat ?: 0.0}, ${lng ?: 0.0}",
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

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
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
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
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}


/**
 * 📍 Ambil lokasi real-time
 */
@SuppressLint("MissingPermission")
suspend fun getDeviceLocation(context: android.content.Context): Pair<Double, Double>? {
    return try {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val location = fused.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        ).await()
        if (location != null) Pair(location.latitude, location.longitude) else null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


 //🔁 Fetch token dari server dan buat QR

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

            // 🔹 Ambil koordinat dengan retry agar tidak 0,0
            var coords = getDeviceLocation(context)
            var retry = 0
            while (coords == null && retry < 5) {
                delay(1000)
                coords = getDeviceLocation(context)
                retry++
            }

            val lat = coords?.first ?: 0.0
            val lng = coords?.second ?: 0.0

            // 🔹 Kirim lat/lng ke server untuk update lokasi token di tabel mtoken
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

            // 🔹 Format disesuaikan dengan verify.php (untuk scanner)
            val payload = JSONObject().apply {
                put("token", token)
                put("lat", lat)
                put("lng", lng)
                put("userId", userId)
            }

            // 🔹 Generate QR Code
            val qrText = payload.toString()
            val bitmap = generateQrBitmap(qrText)

            // Kirim hasil ke UI
            onResult(bitmap, expiryTime, lat, lng, null)
        } catch (e: Exception) {
            onResult(null, null, null, null, "Gagal memuat QR: ${e.message}")
        }
    }
}


//🧩 Buat QR bitmap
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

// 🔹 Kirim lat/lng ke server untuk update posisi QR di database
fun updateServerTokenLocation(token: String, lat: Double, lng: Double) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val client = OkHttpClient()
            val form = okhttp3.FormBody.Builder()
                .add("lat", lat.toString())
                .add("lng", lng.toString())
                .add("token", token)
                .build()

            val request = Request.Builder()
                .url("https://absensi.matahati.my.id/qrabsen.php")
                .post(form)
                .build()

            client.newCall(request).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
