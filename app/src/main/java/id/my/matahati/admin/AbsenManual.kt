package id.my.matahati.admin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import id.my.matahati.absensi.utils.NetworkUtils
import id.my.matahati.admin.data.OfflineManualAbsen
import id.my.matahati.admin.worker.enqueueManualSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import android.location.Geocoder
import java.util.Locale

object LocationCache {
    var lat: Double? = null
    var lng: Double? = null
}

class AbsenManual : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AbsenManualScreen() }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AbsenManualScreen() {
    val context = LocalContext.current
    val session = remember { SessionManager(context) }
    val activity = context as ComponentActivity

    // ✅ LISTEN BROADCAST DARI WORKER
    DisposableEffect(Unit) {

        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                Toast.makeText(
                    context,
                    "✅ Koneksi kembali. Absen manual berhasil dikirim.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val filter = android.content.IntentFilter("MANUAL_ABSEN_SYNC_SUCCESS")

        // ✅ Android 13+ SAFE
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            android.content.Context.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    LaunchedEffect(Unit) {
        ensureToken(context)
    }

    val scope = rememberCoroutineScope()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val adminEmail = session.getUser()["email"] as? String ?: ""
    val adminPassword = session.getPassword() ?: session.getTempPassword() ?: ""

    var userEmail by rememberSaveable { mutableStateOf("") }
    var userPassword by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // 🆕 foto
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoBase64 by remember { mutableStateOf<String?>(null) }

    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            photoBitmap = bitmap
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
            photoBase64 = Base64.encodeToString(output.toByteArray(), Base64.DEFAULT)
        }
    }

    val primaryColor = Color(0xFFB63352)
    val focusManager = LocalFocusManager.current
    var lat by remember { mutableStateOf(LocationCache.lat) }
    var lng by remember { mutableStateOf(LocationCache.lng) }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scaleFactor = rememberAdaptiveScale()

    // permission lokasi
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                scope.launch(Dispatchers.IO) {
                    val loc = fusedLocationClient.awaitLocation(context)
                    if (loc != null) {
                        LocationCache.lat = loc.latitude
                        LocationCache.lng = loc.longitude
                        withContext(Dispatchers.Main) {
                            lat = loc.latitude
                            lng = loc.longitude
                        }
                    }
                }
            } else Toast.makeText(context, "Izin lokasi diperlukan!", Toast.LENGTH_SHORT).show()
        }
    )

    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val loc = fusedLocationClient.awaitLocation(context)
            loc?.let {
                LocationCache.lat = it.latitude
                LocationCache.lng = it.longitude
                lat = it.latitude
                lng = it.longitude
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // ==================== UI ====================
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
                .background(Color(0xFFB63352))
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
                contentDescription = "Ilustrasi Absen",
                modifier = Modifier
                    .height((130.dp * scaleFactor).coerceAtLeast(80.dp))
                    .padding(bottom = (8.dp * scaleFactor)),
                contentScale = ContentScale.Fit
            )

            Text(
                text = "Absen Manual",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    fontSize = (22.sp * scaleFactor)
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Silahkan lakukan absen manual jika mengalami kendala dengan absen scan QR",
                textAlign = TextAlign.Center,
                color = Color.Gray,
                fontSize = (14.sp * scaleFactor),
                modifier = Modifier.padding(
                    horizontal = (20.dp * scaleFactor),
                    vertical = (8.dp * scaleFactor)
                )
            )

            Spacer(modifier = Modifier.height((20.dp * scaleFactor)))

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
                    OutlinedTextField(
                        value = userEmail,
                        onValueChange = { userEmail = it },
                        label = { Text("User Email", fontSize = (13.sp * scaleFactor)) },
                        textStyle = LocalTextStyle.current.copy(fontSize = (13.sp * scaleFactor)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            focusedLabelColor = primaryColor,
                            cursorColor = primaryColor
                        ),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = {
                            focusManager.moveFocus(FocusDirection.Down)
                        })
                    )

                    OutlinedTextField(
                        value = userPassword,
                        onValueChange = { userPassword = it },
                        label = { Text("User Password", fontSize = (13.sp * scaleFactor)) },
                        textStyle = LocalTextStyle.current.copy(fontSize = (13.sp * scaleFactor)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true,
                        visualTransformation = if (passwordVisible)
                            VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image =
                                if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(image, contentDescription = null)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            focusedLabelColor = primaryColor,
                            cursorColor = primaryColor
                        ),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = {
                            focusManager.moveFocus(FocusDirection.Down)
                        })
                    )

                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Alasan", fontSize = (13.sp * scaleFactor)) },
                        textStyle = LocalTextStyle.current.copy(fontSize = (13.sp * scaleFactor)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .onFocusEvent { focusState ->
                                if (focusState.isFocused) {
                                    scope.launch {
                                        delay(1)
                                        bringIntoViewRequester.bringIntoView()
                                    }
                                }
                            }
                            .padding(bottom = 8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            focusedLabelColor = primaryColor,
                            cursorColor = primaryColor
                        ),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                        })
                    )

                    // 📸 Button Ambil Foto + Preview
                    Button(
                        onClick = { photoLauncher.launch(null) },
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
                        Image(
                            bitmap = photoBitmap!!.asImageBitmap(),
                            contentDescription = "Preview Foto",
                            modifier = Modifier
                                .size((200.dp * scaleFactor))
                                .align(Alignment.CenterHorizontally)
                                .padding(8.dp)
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                handleAbsenManual(
                                    context,
                                    adminEmail,
                                    adminPassword,
                                    userEmail,
                                    userPassword,
                                    reason,
                                    lat,
                                    lng,
                                    photoBase64,
                                    isLoadingSetter = { isLoading = it }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Text(
                            if (isLoading) "Mengirim..." else "KIRIM ABSEN MANUAL",
                            fontSize = (13.sp * scaleFactor),
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

/** ================= BACKEND & HELPER ================= */

suspend fun handleAbsenManual(
    context: Context,
    adminEmail: String,
    adminPassword: String,
    userEmail: String,
    userPassword: String,
    reason: String,
    lat: Double?,
    lng: Double?,
    photoBase64: String?,
    isLoadingSetter: (Boolean) -> Unit
) {
    if (userEmail.isBlank() || userPassword.isBlank()) {
        Toast.makeText(context, "Isi email & password user!", Toast.LENGTH_SHORT).show()
        return
    }
    if (lat == null || lng == null) {
        Toast.makeText(context, "Lokasi belum tersedia!", Toast.LENGTH_SHORT).show()
        return
    }

    // =========================
    // 🔌 OFFLINE MODE (BARU)
    // =========================
    if (!NetworkUtils.isOnline(context)) {

        val placeName = getPlaceName(context, lat, lng)
            ?: "${lat},${lng}"

        val offlineData = OfflineManualAbsen(
            userEmail = userEmail,
            userPassword = userPassword,
            reason = reason,
            lat = lat.toString(),
            lng = lng.toString(),
            placeName = placeName,
            photoBase64 = photoBase64 ?: "",
            createdAt = System.currentTimeMillis()
        )

        MyApp.db.offlineManualAbsenDao().insert(offlineData)

        enqueueManualSyncWorker(context)

        Toast.makeText(
            context,
            "📡 Absen manual disimpan offline. Akan dikirim otomatis saat online.",
            Toast.LENGTH_LONG
        ).show()

        return
    }

    // =========================
    // 🌐 ONLINE MODE (LOGIC LAMA – TIDAK DIUBAH)
    // =========================
    isLoadingSetter(true)

    // 🔹 Ambil nama lokasi (reverse geocode)
    val placeName = getPlaceName(context, lat, lng)
        ?: "${lat},${lng}" // fallback aman

    val result = sendManualCheckin(
        adminEmail,
        adminPassword,
        userEmail,
        userPassword,
        reason,
        lat,
        lng,
        placeName,
        photoBase64
    )

    withContext(Dispatchers.Main) {
        isLoadingSetter(false)
        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
        if (result.contains("berhasil", true)) {
            val intent = android.content.Intent(context, MainActivity::class.java)
            context.startActivity(intent)
            if (context is ComponentActivity) context.finish()
        }
    }
}

suspend fun sendManualCheckin(
    adminEmail: String,
    adminPassword: String,
    userEmail: String,
    userPassword: String,
    reason: String,
    lat: Double,
    lng: Double,
    placeName: String?,
    photoBase64: String?
): String = withContext(Dispatchers.IO) {
    try {
        //val url = "https://absensi.matahati.my.id/manual_checkin.php"
        val url = "https://absensi.karyatra.cloud/manual_checkin.php"
        val json = JSONObject().apply {
            put("admin_email", adminEmail)
            put("admin_password", adminPassword)
            put("user_email", userEmail)
            put("user_password", userPassword)
            put("reason", reason)
            put("lat", lat)
            put("lng", lng)
            if (!placeName.isNullOrBlank()) {
                put("cplacename", placeName)
            }
            if (!photoBase64.isNullOrBlank()) put("photoBase64", photoBase64)
        }
        val client = OkHttpClient()
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "application/json")
            .addHeader("X-DEVICE-ID", MyApp.DEVICE_ID)
            .build()
        val response = client.newCall(request).execute()
        val res = response.body?.string() ?: ""
        val obj = JSONObject(res)
        if (obj.optString("status") == "ok") "Absen manual berhasil dikirim!"
        else "Gagal: ${obj.optString("message")}"
    } catch (e: Exception) {
        "⚠️ Error: ${e.message}"
    }
}

suspend fun FusedLocationProviderClient.awaitLocation(context: Context): Location? =
    suspendCancellableCoroutine { cont ->
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        try {
            lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { cont.resume(null) }
        } catch (e: SecurityException) {
            cont.resume(null)
        }
    }

suspend fun ensureToken(context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
        val session = SessionManager(context)
        val adminEmail = session.getUser()["email"] as? String ?: return@withContext false
        val adminPassword = session.getPassword() ?: session.getTempPassword() ?: return@withContext false

        val json = JSONObject().apply {
            put("admin_email", adminEmail)
            put("admin_password", adminPassword)
        }

        val request = Request.Builder()
            //.url("https://absensi.matahati.my.id/ensure_token.php")
            .url("https://absensi.karyatra.cloud/ensure_token.php")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("X-DEVICE-ID", MyApp.DEVICE_ID)
            .build()

        val response = OkHttpClient().newCall(request).execute()
        val body = response.body?.string().orEmpty()

        response.isSuccessful && body.contains("\"status\":\"ok\"")

    } catch (e: Exception) {
        false
    }
}

suspend fun getPlaceName(
    context: Context,
    lat: Double,
    lng: Double
): String? = withContext(Dispatchers.IO) {
    try {
        val geocoder = Geocoder(context, Locale("id", "ID"))
        val addresses = geocoder.getFromLocation(lat, lng, 1)

        if (!addresses.isNullOrEmpty()) {
            val addr = addresses[0]

            listOfNotNull(
                addr.subLocality,      // desa / kelurahan
                addr.locality,         // kecamatan / kota
                addr.subAdminArea,     // kabupaten
                addr.adminArea         // provinsi
            ).joinToString(", ")
        } else null

    } catch (e: Exception) {
        null
    }
}
