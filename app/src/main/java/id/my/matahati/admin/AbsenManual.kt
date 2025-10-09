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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import com.google.android.gms.location.FusedLocationProviderClient
import kotlin.coroutines.resume
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.delay
import androidx.compose.foundation.ExperimentalFoundationApi


// Simple in-memory cache for last known location while app runs
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AbsenManualScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var userpasswordVisible by rememberSaveable { mutableStateOf(false) }

    // ✅ Admin harus isi manual
    var adminEmail by rememberSaveable { mutableStateOf("") }
    var adminPassword by rememberSaveable { mutableStateOf("") }

    var userEmail by rememberSaveable { mutableStateOf("") }
    var userPassword by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val primaryColor = Color(0xFFFF6F51)
    val focusManager = LocalFocusManager.current

    // lat/lng state uses cached value if available
    var lat by remember { mutableStateOf(LocationCache.lat) }
    var lng by remember { mutableStateOf(LocationCache.lng) }

    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // Permission launcher
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
                            Toast.makeText(context, "Akses lokasi ditolak!", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(context, "Akses lokasi ditolak!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 26.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Absen Manual",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.Black,
            modifier = Modifier
                .padding(bottom = 12.dp, top = 20.dp)
        )

        Image(
            painter = painterResource(id = R.drawable.panaform),
            contentDescription = "Login",
            modifier = Modifier
                .height(200.dp)
                .padding(bottom = 16.dp),
            contentScale = ContentScale.Fit
        )

        // ✅ Admin Email Input
        OutlinedTextField(
            value = adminEmail,
            onValueChange = { adminEmail = it },
            label = { Text("Admin Email") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                focusedLabelColor = primaryColor,
                cursorColor = primaryColor
            ),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                focusManager.moveFocus(FocusDirection.Down)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        // ✅ Admin Password Input
        OutlinedTextField(
            value = adminPassword,
            onValueChange = { adminPassword = it },
            label = { Text("Admin Password") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                focusedLabelColor = primaryColor,
                cursorColor = primaryColor
            ),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                focusManager.moveFocus(FocusDirection.Down)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            singleLine = true,
            visualTransformation = if (passwordVisible)
                VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(image, contentDescription = null)
                }
            }
        )

        // 🧍 User Email
        OutlinedTextField(
            value = userEmail,
            onValueChange = { userEmail = it },
            label = { Text("User Email") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                focusedLabelColor = primaryColor,
                cursorColor = primaryColor
            ),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                focusManager.moveFocus(FocusDirection.Down)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester) // ✅ ini kuncinya
                .onFocusEvent { focusState ->
                    if (focusState.isFocused) {
                        scope.launch {
                            delay(1) // beri waktu keyboard muncul
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                }
        )

        // 🔒 User Password
        OutlinedTextField(
            value = userPassword,
            onValueChange = { userPassword = it },
            label = { Text("User Password") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                focusedLabelColor = primaryColor,
                cursorColor = primaryColor
            ),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                focusManager.moveFocus(FocusDirection.Down)
            }),
            singleLine = true,
            visualTransformation = if (userpasswordVisible)
                VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (userpasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { userpasswordVisible = !userpasswordVisible }) {
                    Icon(image, contentDescription = null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester) // ✅ ini kuncinya
                .onFocusEvent { focusState ->
                    if (focusState.isFocused) {
                        scope.launch {
                            delay(1) // beri waktu keyboard muncul
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                }
        )

        // 📄 Alasan
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("Alasan") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                focusedLabelColor = primaryColor,
                cursorColor = primaryColor
            ),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester) // ✅ ini kuncinya
                .onFocusEvent { focusState ->
                    if (focusState.isFocused) {
                        scope.launch {
                            delay(1) // beri waktu keyboard muncul
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                }
        )

        Spacer(Modifier.height(20.dp))

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
                containerColor = Color(0xFFFF725E),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Mengirim..." else "KIRIM ABSEN MANUAL")
        }
    }
}


/** Handler yang menjaga UI tetap bersih — memanggil sendManualCheckin di background */
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
        Toast.makeText(context, "Admin harus mengisi email & password!", Toast.LENGTH_SHORT).show()
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
        adminEmail,
        adminPassword,
        userEmail,
        userPassword,
        reason.ifBlank { "Manual check-in" },
        lat,
        lng
    )

    withContext(Dispatchers.Main) {
        isLoadingSetter(false)
        Toast.makeText(context, result, Toast.LENGTH_LONG).show()
    }
}

/** Kirim data ke server */
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
            "✅ Absen manual berhasil dikirim!"
        else
            "❌ Gagal: ${obj.optString("message")}"

    } catch (e: IOException) {
        "⚠️ Kesalahan jaringan: ${e.message}"
    } catch (e: Exception) {
        "⚠️ Error: ${e.message}"
    }
}

/** Helper coroutine-safe untuk ambil lokasi */
suspend fun FusedLocationProviderClient.awaitLocation(context: Context): Location? =
    suspendCancellableCoroutine { cont ->
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
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
