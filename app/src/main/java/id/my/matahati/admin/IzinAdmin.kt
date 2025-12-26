package id.my.matahati.admin

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.location.LocationServices
import id.my.matahati.absensi.utils.NetworkUtils
import id.my.matahati.admin.data.OfflineIzin
import id.my.matahati.admin.worker.enqueueIzinSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class IzinAdmin : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Minta izin kamera & lokasi
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                200
            )
        }

        setContent { IzinAdminScreen() }
    }
}

// Fungsi untuk skala adaptif
@Composable
fun rememberAdaptiveScale(baseWidthDp: Float = 411f): Float {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    return (screenWidthDp / baseWidthDp).coerceIn(0.8f, 1.2f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IzinAdminScreen() {
    val context = LocalContext.current
    val session = SessionManager(context.applicationContext)
    val scope = rememberCoroutineScope()
    val primaryColor = Color(0xFFB63352)

    var selectedUserName by remember { mutableStateOf("") }
    var selectedUserId by remember { mutableStateOf<Int?>(null) }
    var alasan by remember { mutableStateOf("") }
    var users by remember { mutableStateOf(listOf<Pair<Int, String>>()) }
    var lat by remember { mutableStateOf(0.0) }
    var lng by remember { mutableStateOf(0.0) }
    var placeName by remember { mutableStateOf("Mencari lokasi...") }
    val category = remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoBase64 by remember { mutableStateOf("") }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val scaleFactor = rememberAdaptiveScale()

    // Ambil lokasi otomatis
    LaunchedEffect(Unit) {
        try {
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                val loc = fusedLocationClient.lastLocation.await()
                if (loc != null) {
                    lat = loc.latitude
                    lng = loc.longitude

                    // Ubah jadi alamat readable
                    withContext(Dispatchers.IO) {
                        val client = OkHttpClient()
                        val url =
                            "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=json&addressdetails=1"
                        val req = Request.Builder()
                            .url(url)
                            .addHeader("User-Agent", "MatahatiApp/1.0")
                            .build()
                        val res = client.newCall(req).execute()
                        val body = res.body?.string()
                        if (res.isSuccessful && body != null) {
                            val obj = JSONObject(body)
                            val displayName = obj.optString("display_name", "$lat, $lng")
                            withContext(Dispatchers.Main) { placeName = displayName }
                        } else {
                            placeName = "$lat, $lng"
                        }
                    }
                } else placeName = "Lokasi tidak tersedia"
            }
        } catch (e: Exception) {
            placeName = "Lokasi tidak diketahui"
        }
    }

    // Ambil daftar user
    // Ambil daftar user (BENAR)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val adminId = session.getUserId()
                val client = OkHttpClient()

                val request = Request.Builder()
                    .url("https://absensi.matahati.my.id/get_users_by_department.php?admin_id=$adminId")
                    .get()
                    .addHeader("X-DEVICE-ID", MyApp.DEVICE_ID) // optional, aman
                    .build()

                val response = client.newCall(request).execute()
                val respBody = response.body?.string() ?: return@withContext

                val json = JSONObject(respBody)
                if (json.optBoolean("success", false)) {
                    val arr = json.getJSONArray("data")
                    val temp = mutableListOf<Pair<Int, String>>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        temp.add(o.getInt("nid") to o.getString("cname"))
                    }
                    withContext(Dispatchers.Main) { users = temp }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Kamera launcher
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let {
            photoBitmap = it
            val out = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 80, out)
            photoBase64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }

    // ================= UI =====================
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((400.dp * scaleFactor).coerceAtLeast(250.dp))
                .align(Alignment.BottomCenter)
                .background(primaryColor)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = (24.dp * scaleFactor))
                .padding(top = (40.dp * scaleFactor), bottom = (24.dp * scaleFactor)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.panaform),
                contentDescription = null,
                modifier = Modifier
                    .height((130.dp * scaleFactor).coerceAtLeast(80.dp))
                    .padding(bottom = (8.dp * scaleFactor)),
                contentScale = ContentScale.Fit
            )

            Text(
                text = "Form Izin (Admin)",
                fontWeight = FontWeight.Bold,
                fontSize = (22.sp * scaleFactor),
                color = Color.Black,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Gunakan form ini untuk membuat izin manual bagi karyawan.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                fontSize = (14.sp * scaleFactor),
                modifier = Modifier.padding(
                    horizontal = (20.dp * scaleFactor),
                    vertical = (8.dp * scaleFactor)
                )
            )

            Spacer(modifier = Modifier.height((20.dp * scaleFactor)))

            // CARD FORM
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = (16.dp * scaleFactor)),
                shape = RoundedCornerShape((20.dp * scaleFactor)),
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF9FC))
            ) {
                Column(
                    modifier = Modifier
                        .padding((20.dp * scaleFactor))
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    var expandedUser by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = expandedUser,
                        onExpandedChange = { expandedUser = !expandedUser }
                    ) {
                        OutlinedTextField(
                            value = selectedUserName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pilih Karyawan", fontSize = (13.sp * scaleFactor)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUser) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                focusedLabelColor = primaryColor,
                                cursorColor = primaryColor
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = expandedUser,
                            onDismissRequest = { expandedUser = false }
                        ) {
                            users.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedUserId = id
                                        selectedUserName = name
                                        expandedUser = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height((10.dp * scaleFactor)))


                    // 🔽 DROPDOWN KATEGORI (DIPERBAIKI TOTAL)
                    var expandedKategori by remember { mutableStateOf(false) }
                    val kategoriList = listOf("izin", "sakit")

                    ExposedDropdownMenuBox(
                        expanded = expandedKategori,
                        onExpandedChange = { expandedKategori = !expandedKategori }
                    ) {

                        OutlinedTextField(
                            value = category.value,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Kategori", fontSize = (13.sp * scaleFactor)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = expandedKategori
                                )
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                focusedLabelColor = primaryColor,
                                cursorColor = primaryColor
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = expandedKategori,
                            onDismissRequest = { expandedKategori = false }
                        ) {
                            kategoriList.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        category.value = item
                                        expandedKategori = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = alasan,
                        onValueChange = { alasan = it },
                        label = { Text("Alasan Izin", fontSize = (13.sp * scaleFactor)) },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = (10.dp * scaleFactor)),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            focusedLabelColor = primaryColor,
                            cursorColor = primaryColor
                        )
                    )

                    // Tombol Kamera
                    Button(
                        onClick = { cameraLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4C4C59),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(45.dp)
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = "Camera")
                        Spacer(Modifier.width(8.dp))
                        Text("Ambil Foto", fontSize = 13.sp)
                    }

                    if (photoBitmap != null) {
                        Spacer(modifier = Modifier.height((16.dp * scaleFactor)))
                        Image(
                            painter = rememberAsyncImagePainter(photoBitmap),
                            contentDescription = "Preview Foto",
                            modifier = Modifier
                                .size((200.dp * scaleFactor))
                                .align(Alignment.CenterHorizontally)
                                .padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height((16.dp * scaleFactor)))

                    Button(
                        onClick = {
                            if (selectedUserId == null || alasan.isEmpty() || photoBase64.isEmpty()) {
                                Toast.makeText(context, "Lengkapi semua data!", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {

                                    // validasi dulu
                                    if (selectedUserId == null || alasan.isBlank() || photoBase64.isBlank()) {
                                        Toast.makeText(context, "Lengkapi semua data!", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }

                                    loading = true

                                    // =========================
                                    // 🔌 OFFLINE MODE
                                    // =========================
                                    if (!NetworkUtils.isOnline(context)) {

                                        val offlineData = OfflineIzin(
                                            userId = selectedUserId!!,
                                            adminId = session.getUserId(),
                                            date = LocalDate.now().toString(),
                                            lat = lat.toString(),          // 🔥 WAJIB
                                            lng = lng.toString(),          // 🔥 WAJIB
                                            placeName = placeName,
                                            category = category.value,
                                            reason = alasan,
                                            photoBase64 = photoBase64
                                        )

                                        MyApp.db.offlineIzinDao().insert(offlineData)

                                        enqueueIzinSyncWorker(context)

                                        loading = false

                                        Toast.makeText(
                                            context,
                                            "📡 Izin disimpan offline. Akan dikirim otomatis saat online.",
                                            Toast.LENGTH_LONG
                                        ).show()

                                        return@launch
                                    }

                                    // =========================
                                    // 🌐 ONLINE MODE
                                    // =========================
                                    val result = submitIzinAdminWithPhoto(
                                        userId = selectedUserId!!,
                                        alasan = alasan,
                                        category = category.value,
                                        lat = lat,
                                        lng = lng,
                                        adminId = session.getUserId(),
                                        placeName = placeName,
                                        photoBase64 = photoBase64
                                    )

                                    loading = false

                                    Toast.makeText(context, result, Toast.LENGTH_LONG).show()

                                    if (result.startsWith("✅")) {
                                        context.startActivity(Intent(context, MainActivity::class.java))
                                        (context as? Activity)?.finish()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((50.dp * scaleFactor)),
                        shape = RoundedCornerShape((25.dp * scaleFactor))
                    ) {
                        Text(
                            if (loading) "Mengirim..." else "KIRIM IZIN",
                            fontSize = (13.sp * scaleFactor),
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
}

/**
 * 🔹 Kirim data izin ke server (dengan foto base64)
 */
suspend fun submitIzinAdminWithPhoto(
    userId: Int,
    alasan: String,
    lat: Double,
    lng: Double,
    adminId: Int,
    placeName: String,
    category: String,
    photoBase64: String
): String = withContext(Dispatchers.IO) {
    try {
        val json = JSONObject().apply {
            put("userId", userId)
            put("requestDate", LocalDate.now().toString())
            put("reason", alasan)
            put("category", category)
            put("adminId", adminId)
            put("location", "$lat,$lng")
            put("placeName", placeName)
            put("photoBase64", photoBase64)
        }

        val body = json
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://absensi.matahati.my.id/izin_admin.php")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-DEVICE-ID", MyApp.DEVICE_ID) // 🔒 DEVICE GATE
            .build()

        val client = OkHttpClient()
        val response = client.newCall(request).execute()
        val respBody = response.body?.string().orEmpty()

        val obj = JSONObject(respBody)
        val success = obj.optBoolean("success", false)
        val message = obj.optString("message", "Terjadi kesalahan")

        if (success) {
            "✅ $message"
        } else {
            "❌ $message"
        }

    } catch (e: Exception) {
        "⚠️ Error jaringan: ${e.message}"
    }
}

class SyncIzinAdminWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val dao = MyApp.db.offlineIzinDao()
            val list = dao.getAll()
            if (list.isEmpty()) return@withContext Result.success()

            val client = OkHttpClient()

            for (izin in list) {
                val json = JSONObject().apply {
                    put("userId", izin.userId)
                    put("adminId", izin.adminId)
                    put("requestDate", izin.date)
                    put("location", "${izin.lat},${izin.lng}")
                    put("placeName", izin.placeName)
                    put("category", izin.category)
                    put("reason", izin.reason)
                    put("photoBase64", izin.photoBase64)
                }

                val request = Request.Builder()
                    .url("https://absensi.matahati.my.id/izin_admin.php")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("Accept", "application/json")
                    .addHeader("X-DEVICE-ID", MyApp.DEVICE_ID)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    dao.deleteById(izin.id)
                }
            }

            // broadcast ke UI
            applicationContext.sendBroadcast(
                Intent("SYNC_IZIN_ADMIN_SUCCESS")
            )

            Result.success()

        } catch (e: Exception) {
            Result.retry()
        }
    }
}

