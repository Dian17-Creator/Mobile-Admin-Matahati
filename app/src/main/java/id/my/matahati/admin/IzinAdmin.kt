package id.my.matahati.admin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.LocalDate

class IzinAdmin : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Fix utama agar imePadding berfungsi di Compose
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ✅ Minta izin lokasi
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                200
            )
        }

        setContent { IzinAdminUi() }
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "Izin Preview"
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IzinAdminUi() {
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
    var loading by remember { mutableStateOf(false) }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // 📍 Ambil lokasi otomatis
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
                    withContext(Dispatchers.IO) {
                        try {
                            val client = OkHttpClient()
                            val url =
                                "https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lng}&format=json&addressdetails=1"

                            val request = Request.Builder()
                                .url(url)
                                .addHeader("User-Agent", "MatahatiApp/1.0 (mailto:admin@matahati.my.id)")
                                .build()

                            val response = client.newCall(request).execute()
                            val json = response.body?.string()
                            if (response.isSuccessful && json != null) {
                                val obj = JSONObject(json)
                                val displayName = obj.optString("display_name", "")
                                withContext(Dispatchers.Main) {
                                    placeName = if (displayName.isNotBlank()) displayName else "$lat, $lng"
                                }
                            } else {
                                withContext(Dispatchers.Main) { placeName = "$lat, $lng" }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { placeName = "$lat, $lng" }
                        }
                    }
                } else placeName = "Lokasi tidak tersedia"
            } else placeName = "Izin lokasi belum diberikan"
        } catch (e: Exception) {
            placeName = "Lokasi tidak diketahui"
        }
    }

    // 🔹 Ambil daftar user dari server
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val adminId = session.getUserId()
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://absensi.matahati.my.id/get_users_by_department.php?admin_id=$adminId&nocache=${System.currentTimeMillis()}")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                val jsonObject = JSONObject(body)

                if (jsonObject.optBoolean("success", false)) {
                    val dataArray = jsonObject.getJSONArray("data")
                    val tempList = mutableListOf<Pair<Int, String>>()
                    for (i in 0 until dataArray.length()) {
                        val obj = dataArray.getJSONObject(i)
                        val id = obj.optInt("nid", 0)
                        val name = obj.optString("cname", "")
                        if (id != 0 && name.isNotEmpty()) tempList.add(id to name)
                    }
                    withContext(Dispatchers.Main) { users = tempList }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Gagal memuat data karyawan", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Terjadi kesalahan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==================== UI ====================
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .align(Alignment.BottomCenter)
                .background(primaryColor)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.panaform),
                contentDescription = "Ilustrasi Izin Admin",
                modifier = Modifier
                    .height(130.dp)
                    .padding(bottom = 8.dp),
                contentScale = ContentScale.Fit
            )

            Text(
                text = "Form Izin",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    fontSize = 22.sp
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Form ini digunakan untuk membuat izin bagi karyawan dalam departemen Anda.",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF9FC))
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    var expanded by remember { mutableStateOf(false) }

                    // Dropdown karyawan
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedUserName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pilih Karyawan", fontSize = 13.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                focusedLabelColor = primaryColor,
                                cursorColor = primaryColor
                            ),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )

                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            users.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedUserId = id
                                        selectedUserName = name
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Alasan izin
                    OutlinedTextField(
                        value = alasan,
                        onValueChange = { alasan = it },
                        label = { Text("Alasan Izin", fontSize = 13.sp) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            focusedLabelColor = primaryColor,
                            cursorColor = primaryColor
                        )
                    )

                    Spacer(modifier = Modifier.height(15.dp))

                    // ✅ Tombol SIMPAN IZIN dengan redirect ke AdminMainActivity
                    Button(
                        onClick = {
                            if (selectedUserId == null || alasan.isEmpty()) {
                                Toast.makeText(context, "Lengkapi semua data!", Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    loading = true
                                    val success = submitIzinAdmin(
                                        userId = selectedUserId!!,
                                        alasan = alasan,
                                        lat = lat,
                                        lng = lng,
                                        adminId = session.getUserId(),
                                        placeName = placeName
                                    )
                                    loading = false

                                    if (success) {
                                        Toast.makeText(context, "Izin berhasil disimpan ✅", Toast.LENGTH_SHORT).show()
                                        // 🔹 Kembali ke halaman utama admin
                                        val intent = android.content.Intent(context, MainActivity::class.java)
                                        context.startActivity(intent)
                                        if (context is ComponentActivity) context.finish()
                                    } else {
                                        Toast.makeText(context, "Gagal menyimpan izin ❌", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Text(
                            if (loading) "Menyimpan..." else "SIMPAN IZIN",
                            fontSize = 14.sp,
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
 * 🔹 Kirim data izin ke server
 */
suspend fun submitIzinAdmin(
    userId: Int,
    alasan: String,
    lat: Double,
    lng: Double,
    adminId: Int,
    placeName: String
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val jsonBody = JSONObject().apply {
                put("userId", userId)
                put("requestDate", LocalDate.now().toString())
                put("reason", alasan)
                put("adminId", adminId)
                put("location", "$lat,$lng")
                put("placeName", placeName)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonBody.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("https://absensi.matahati.my.id/izin_admin.php")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            println("📩 Server response: $body")
            val json = JSONObject(body)
            json.optBoolean("success", false)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
