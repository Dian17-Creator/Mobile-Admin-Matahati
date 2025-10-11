package id.my.matahati.admin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.coroutines.resume
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import java.io.IOException
import androidx.activity.compose.setContent
import androidx.compose.ui.tooling.preview.Preview

object LocationCache {
    var lat: Double? = null
    var lng: Double? = null
}

class AbsenManual : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AbsenManualScreen()
        }
    }
}

/** 🔹 Fungsi helper untuk membuat UI scaling adaptif di semua device */
@Composable
fun rememberAdaptiveScale(baseWidthDp: Float = 411f): Float {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    return (screenWidthDp / baseWidthDp).coerceIn(0.75f, 1.2f)
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "Absen Manual Preview"
)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AbsenManualScreen() {
    val context = LocalContext.current
    val session = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val activity = context as? android.app.Activity
    val intent = activity?.intent

    val adminEmail = intent?.getStringExtra("ADMIN_EMAIL")
        ?: session.getUser()["email"] as? String
        ?: ""

    val adminPassword = intent?.getStringExtra("ADMIN_PASSWORD")
        ?: session.getPassword()
        ?: session.getTempPassword()
        ?: SessionManager.SessionCache.tempPassword
        ?: ""

    var userEmail by rememberSaveable { mutableStateOf("") }
    var userPassword by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val primaryColor = Color(0xFFFF6F51)
    val focusManager = LocalFocusManager.current
    var lat by remember { mutableStateOf(LocationCache.lat) }
    var lng by remember { mutableStateOf(LocationCache.lng) }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // Adaptive scale
    val scaleFactor = rememberAdaptiveScale()

    // Permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val loc = fusedLocationClient.awaitLocation(context)
                        if (loc != null) {
                            LocationCache.lat = loc.latitude
                            LocationCache.lng = loc.longitude
                            withContext(Dispatchers.Main) {
                                lat = loc.latitude
                                lng = loc.longitude
                            }
                        }
                    } catch (e: SecurityException) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Akses lokasi ditolak!", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            } else {
                Toast.makeText(context, "Izin lokasi diperlukan!", Toast.LENGTH_SHORT).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (LocationCache.lat != null && LocationCache.lng != null) {
                lat = LocationCache.lat
                lng = LocationCache.lng
            } else {
                scope.launch(Dispatchers.IO) {
                    try {
                        val loc = fusedLocationClient.awaitLocation(context)
                        if (loc != null) {
                            LocationCache.lat = loc.latitude
                            LocationCache.lng = loc.longitude
                            withContext(Dispatchers.Main) {
                                lat = loc.latitude
                                lng = loc.longitude
                            }
                        }
                    } catch (e: SecurityException) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Akses lokasi ditolak!", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
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
        // Background bawah
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((400.dp * scaleFactor).coerceAtLeast(250.dp))
                .align(Alignment.BottomCenter)
                .background(
                    color = Color(0xFFFD6E50),
                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = (24.dp * scaleFactor))
                .padding(top = (40.dp * scaleFactor), bottom = (24.dp * scaleFactor)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Gambar ilustrasi
            Image(
                painter = painterResource(id = R.drawable.panaform),
                contentDescription = "Ilustrasi Absen",
                modifier = Modifier
                    .height((120.dp * scaleFactor).coerceAtLeast(80.dp))
                    .padding(bottom = (8.dp * scaleFactor)),
                contentScale = ContentScale.Fit
            )

            // Judul
            Text(
                text = "Absen Manual",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    fontSize = (22.sp * scaleFactor)
                ),
                textAlign = TextAlign.Center
            )

            // Deskripsi
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

            // Card Form
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = (8.dp * scaleFactor)),
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = (8.dp * scaleFactor)),
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
                            .padding(bottom = (16.dp * scaleFactor)),
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
                            .height((50.dp * scaleFactor)),
                        enabled = !isLoading,
                        shape = RoundedCornerShape((25.dp * scaleFactor))
                    ) {
                        Text(
                            if (isLoading) "Mengirim..." else "KIRIM ABSEN MANUAL",
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
    isLoadingSetter: (Boolean) -> Unit
) {
    if (adminEmail.isBlank() || adminPassword.isBlank()) {
        Toast.makeText(context, "Data admin tidak ditemukan. Silakan login ulang!", Toast.LENGTH_SHORT).show()
        return
    }

    if (userEmail.isBlank() || userPassword.isBlank()) {
        Toast.makeText(context, "Isi email & password user!", Toast.LENGTH_SHORT).show()
        return
    }

    if (lat == null || lng == null) {
        Toast.makeText(context, "Lokasi belum tersedia!", Toast.LENGTH_SHORT).show()
        return
    }

    isLoadingSetter(true)
    val result = sendManualCheckin(
        adminEmail, adminPassword, userEmail, userPassword,
        reason.ifBlank { "Manual check-in" }, lat, lng
    )

    withContext(Dispatchers.Main) {
        isLoadingSetter(false)
        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
    }
}

suspend fun sendManualCheckin(
    adminEmail: String,
    adminPassword: String,
    userEmail: String,
    userPassword: String,
    reason: String,
    lat: Double,
    lng: Double
): String = withContext(Dispatchers.IO) {
    try {
        val url = "https://absensi.matahati.my.id/manual_checkin.php"

        val json = JSONObject().apply {
            put("admin_email", adminEmail)
            put("admin_password", adminPassword)
            put("user_email", userEmail)
            put("user_password", userPassword)
            put("reason", reason)
            put("lat", lat)
            put("lng", lng)
        }

        val client = OkHttpClient()
        val body = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val res = response.body?.string() ?: ""

        val obj = JSONObject(res)
        if (obj.optString("status") == "ok")
            "Absen manual berhasil dikirim!"
        else
            "Gagal: ${obj.optString("message")}"

    } catch (e: IOException) {
        "⚠️ Kesalahan jaringan: ${e.message}"
    } catch (e: Exception) {
        "⚠️ Error: ${e.message}"
    }
}

suspend fun FusedLocationProviderClient.awaitLocation(context: Context): Location? =
    suspendCancellableCoroutine { cont ->
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
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
