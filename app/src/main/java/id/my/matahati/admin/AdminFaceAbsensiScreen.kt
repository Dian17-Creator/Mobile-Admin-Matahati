package id.my.matahati.admin

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFaceAbsensiScreen() {

    val primaryColor = Color(0xFFB63352)
    var showSuccessDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val session = remember { SessionManager(context) }
    val adminId = session.getUserId()
    var isCameraReady by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(Color.Black) }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    var place by remember { mutableStateOf("Mengambil lokasi...") }
    var cameraEnabled by remember { mutableStateOf(false) }
    var locationEnabled by remember { mutableStateOf(false) }
    var selectedDuration by remember { mutableStateOf(15) }
    var remainingSeconds by remember { mutableStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        cameraEnabled = true
        locationEnabled = true
        remainingSeconds = selectedDuration * 60
        isTimerRunning = true
    }

    LaunchedEffect(locationEnabled) {
        if (!locationEnabled) {
            place = "📍 Lokasi dimatikan"
            lat = null
            lng = null
            return@LaunchedEffect
        }

        try {
            val activity = context as Activity
            val fused = LocationServices.getFusedLocationProviderClient(activity)

            fused.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    lat = loc.latitude
                    lng = loc.longitude
                    scope.launch {
                        place = reverseGeocode(loc.latitude, loc.longitude)
                    }
                } else {
                    place = "Lokasi tidak tersedia"
                }
            }
        } catch (e: SecurityException) {
            place = "Izin lokasi belum diberikan"
            lat = null
            lng = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF3F3)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var expanded by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),

                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = "$selectedDuration menit",
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = { Text("⏱️") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
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
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf(5, 10, 15, 30).forEach { minute ->
                            DropdownMenuItem(
                                text = { Text("$minute menit") },
                                onClick = {
                                    selectedDuration = minute
                                    expanded = false
                                }
                            )
                        }
                    }
                }


                // ⏳ COUNTDOWN
                val min = remainingSeconds / 60
                val sec = remainingSeconds % 60


                Text(
                    text = if (isTimerRunning)
                        "%02d:%02d".format(min, sec)
                    else "--:--",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.width(60.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (cameraEnabled) {
                    AdminFaceAbsensiCamera(
                        onReady = { isCameraReady = true },

                        // liveness sudah dihapus → kosong saja
                        onFaceFrame = { _ -> },

                        onCaptured = { bmp ->
                            if (bmp == null) {
                                statusText = "Wajah tidak valid"
                                statusColor = Color.Red
                                isCapturing = false
                                return@AdminFaceAbsensiCamera
                            }

                            scope.launch {
                                isUploading = true

                                val result = uploadAdminFaceLogin(
                                    bmp,
                                    adminId,
                                    lat,
                                    lng,
                                    place
                                )

                                isUploading = false
                                isCapturing = false

                                if (result.success) {
                                    showSuccessDialog = true
                                } else {
                                    statusText = result.message
                                    statusColor = Color.Red
                                }
                            }
                        }
                    )
                } else {
                    // 🔋 Kamera OFF (hemat baterai)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(2.dp, Color.Gray, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "📷 Kamera dimatikan\nTekan Aktifkan untuk menyalakan",
                            textAlign = TextAlign.Center,
                            color = Color.Gray
                        )
                    }
                }

                // frame hijau tetap
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .aspectRatio(3f / 4f)
                        .border(3.dp, Color.Green, RoundedCornerShape(12.dp))
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "📍 $place",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                enabled = isCameraReady && !isUploading && !isCapturing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB63352),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(5.dp),
                onClick = {
                    isCapturing = true
                    AdminCameraController.capture()
                }
            ) {
                Text(
                    when {
                        isUploading -> "Lihat Ke Kamera"
                        isCapturing -> "Lihat Ke Kamera"
                        else -> "Ambil Foto"
                    }
                )
            }

            if (statusText.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(statusText, color = statusColor, textAlign = TextAlign.Center)
            }

            if (showSuccessDialog) {

                // animasi scale + fade
                val scale by animateFloatAsState(
                    targetValue = if (showSuccessDialog) 1f else 0.9f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "scale"
                )

                val alpha by animateFloatAsState(
                    targetValue = if (showSuccessDialog) 1f else 0f,
                    animationSpec = tween(durationMillis = 180),
                    label = "alpha"
                )

                androidx.compose.material3.AlertDialog(
                    onDismissRequest = {},
                    shape = RoundedCornerShape(3.dp),

                    confirmButton = {},
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp),

                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {

                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(56.dp)
                            )

                            Text(
                                text = "Absen Berhasil!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Absen Anda Sudah Tercatat",
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )

                            Button(
                                onClick = {
                                    showSuccessDialog = false
                                },
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF6F51),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("OK")
                            }
                        }
                    }
                )
            }

            Spacer(Modifier.height(5.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .padding(horizontal = 0.dp, vertical = 5.dp), // 🔹 Kurangi padding
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 🔘 Tombol Refresh dan Logout
                val listState = rememberLazyListState()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(125.dp)
                        .padding(horizontal = 0.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {


                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center // ✅ KUNCI UTAMA
                    ) {
                        LazyRow(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 5.dp)
                        ) {

                            item {
                                ActionCard(
                                    icon = Icons.Default.Refresh,
                                    label = "Refresh",
                                ) {
                                    cameraEnabled = true
                                    locationEnabled = true
                                    remainingSeconds = selectedDuration * 60
                                    isTimerRunning = true
                                }
                            }

                            item {
                                ActionCard(
                                    icon = Icons.Default.Face,
                                    label = "Face Register",
                                ) {
                                    context.launchWithSlide(RegistrasiWajahAdmin::class.java)
                                }
                            }

                            item {
                                ActionCard(
                                    icon = Icons.Default.QrCode2,
                                    label = "QR",
                                ) {
                                    context.launchWithSlide(QrPage::class.java)
                                }
                            }

                            item {
                                ActionCard(
                                    icon = Icons.Default.PhoneAndroid,
                                    label = "ID",
                                ) {
                                    context.launchWithSlide(DeviceInfoAdminActivity::class.java)
                                }
                            }

                            item {
                                ActionCard(
                                    icon = Icons.Default.Edit,
                                    label = "Manual",
                                ) {
                                    context.launchWithSlide(AbsenManual::class.java)
                                }
                            }

                            item {
                                ActionCard(
                                    icon = Icons.Default.Event,
                                    label = "Izin",
                                ) {
                                    context.launchWithSlide(IzinAdmin::class.java)
                                }
                            }



                            item {
                                ActionCard(
                                    icon = Icons.Default.Logout,
                                    label = "Logout",
                                ) {
                                    if (session.isRememberMe()) {
                                        session.clearSession()
                                    } else {
                                        session.clearLoginButKeepTemp()
                                    }
                                    val intent = Intent(context, LoginPage::class.java)
                                    context.startActivity(intent)
                                    if (context is ComponentActivity) context.finish()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}