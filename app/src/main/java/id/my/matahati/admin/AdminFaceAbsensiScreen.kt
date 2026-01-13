package id.my.matahati.admin

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFaceAbsensiScreen() {

    // liveness
    var smileDone by remember { mutableStateOf(false) }
    var smilingFrame by remember { mutableStateOf(0) }
    var isSessionStarted by remember { mutableStateOf(false) }
    var eyesClosed by remember { mutableStateOf(false) }
    var blinkDone by remember { mutableStateOf(false) }
    var headUpDone by remember { mutableStateOf(false) }
    var headNodDone by remember { mutableStateOf(false) }

    var headUpFrame by remember { mutableStateOf(0) }
    var headDownFrame by remember { mutableStateOf(0) }
    var turnRightDone by remember { mutableStateOf(false) }
    var turnLeftDone by remember { mutableStateOf(false) }

    var turnFrame by remember { mutableStateOf(0) }
    var remainingTime by remember { mutableStateOf(0) }
    var isTransitioningStep by remember { mutableStateOf(false) }
    val STEP_TIMEOUT_MS = 3_000L

    val STEP_TRANSITION_DELAY_MS = 300L
    var stepStartTime by remember { mutableStateOf(0L) }
    var livenessSteps by remember { mutableStateOf<List<LivenessStep>>(emptyList()) }

    var currentLivenessIndex by remember { mutableStateOf(0) }
    val currentLivenessStep = livenessSteps.getOrNull(currentLivenessIndex)

    val livenessPassed = currentLivenessIndex >= livenessSteps.size
    val isDoingLiveness = isSessionStarted && !livenessPassed
    val scope = rememberCoroutineScope()

    fun resetLiveness(autoRestart: Boolean = true) {
        Log.w(TAG_ADMIN_ABSEN, "🔄 RESET LIVENESS")

        blinkDone = false
        eyesClosed = false
        headUpDone = false
        headNodDone = false
        headUpFrame = 0
        headDownFrame = 0
        turnRightDone = false
        turnLeftDone = false
        turnFrame = 0
        smileDone = false
        smilingFrame = 0

        isTransitioningStep = false
        currentLivenessIndex = 0
        stepStartTime = System.currentTimeMillis()

        if (autoRestart) {
            livenessSteps = LivenessStep.values()
                .toList()
                .shuffled()
                .take(2)
            isSessionStarted = true
        }
    }

    val primaryColor = Color(0xFFB63352)
    var showSuccessDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
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

    LaunchedEffect(livenessPassed) {
        if (
            isSessionStarted &&
            livenessPassed &&
            isCameraReady &&
            !isCapturing &&
            !isUploading
        ) {
            delay(300)
            isCapturing = true
            AdminCameraController.capture()
        }
    }

    LaunchedEffect(currentLivenessIndex) {
        if (isSessionStarted && !livenessPassed) {
            stepStartTime = System.currentTimeMillis()
            Log.d(TAG_ADMIN_ABSEN, "⏱ Step $currentLivenessIndex started")
        }
    }

    LaunchedEffect(stepStartTime, isSessionStarted) {
        if (!isSessionStarted || stepStartTime == 0L) return@LaunchedEffect

        val timeoutSec = (STEP_TIMEOUT_MS / 1000).toInt()

        while (isSessionStarted && stepStartTime > 0) {
            val elapsed = ((System.currentTimeMillis() - stepStartTime) / 1000).toInt()
            remainingTime = (timeoutSec - elapsed).coerceAtLeast(0)
            delay(200)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (isTimerRunning && remainingSeconds > 0) {
                        cameraEnabled = true
                        locationEnabled = true
                    }
                }

                Lifecycle.Event.ON_PAUSE -> {
                    cameraEnabled = false
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        cameraEnabled = true
        locationEnabled = true
        remainingSeconds = selectedDuration * 60
        isTimerRunning = true
    }

    LaunchedEffect(isTimerRunning) {
        if (!isTimerRunning) return@LaunchedEffect

        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }

        // ⛔ AUTO OFF
        cameraEnabled = false
        locationEnabled = false
        isTimerRunning = false
        isCameraReady = false
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

            Spacer(Modifier.height(2.dp))

            LivenessInstructionText(
                isSessionStarted = isSessionStarted,
                currentStep = currentLivenessStep,
                headUpDone = headUpDone,
                livenessPassed = livenessPassed
            )

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
                        currentStepIndex = currentLivenessIndex,
                        onFaceFrame = { face, stepIndex ->   // 👈 INI DIA
                            if (!isSessionStarted) return@AdminFaceAbsensiCamera
                            if (stepIndex >= livenessSteps.size) return@AdminFaceAbsensiCamera
                            val now = System.currentTimeMillis()

                            if (stepStartTime > 0 && now - stepStartTime > STEP_TIMEOUT_MS) {
                                Log.w(TAG_ADMIN_ABSEN, "⏱ STEP TIMEOUT!")

                                statusText = "Waktu habis, silakan ulangi absen"
                                statusColor = Color.Red

                                // reset session
                                statusColor = Color.Red
                                resetLiveness(autoRestart = true)
                                stepStartTime = 0L

                                return@AdminFaceAbsensiCamera
                            }

                            val step = livenessSteps[stepIndex]

                            when (step) {
                                LivenessStep.HEAD_NOD -> {
                                    if (headNodDone) return@AdminFaceAbsensiCamera

                                    val pitch = face.headEulerAngleX
                                    val absPitch = kotlin.math.abs(pitch)

                                    Log.d(TAG_ADMIN_ABSEN, "🎯 HEAD_NOD: pitch=${"%.1f".format(pitch)} up=$headUpDone")

                                    // fase naik
                                    if (!headUpDone) {
                                        if (absPitch > 12f) {
                                            headUpFrame++
                                            if (headUpFrame >= 2) {
                                                headUpDone = true
                                                headUpFrame = 0
                                                Log.d(TAG_ADMIN_ABSEN, "⬆️ HEAD UP OK")
                                            }
                                        } else {
                                            headUpFrame = 0
                                        }
                                        return@AdminFaceAbsensiCamera
                                    }

                                    // fase balik
                                    // fase balik (event-based, TIDAK pakai frame)
                                    if (absPitch < 10f) {
                                        Log.d(TAG_ADMIN_ABSEN, "⬇️ HEAD NOD COMPLETE")
                                        headNodDone = true

                                        if (!isTransitioningStep) {
                                            isTransitioningStep = true
                                            scope.launch {
                                                delay(STEP_TRANSITION_DELAY_MS)
                                                currentLivenessIndex = stepIndex + 1
                                                isTransitioningStep = false
                                            }
                                        }

                                        // reset state
                                        headUpDone = false
                                        headUpFrame = 0
                                        headDownFrame = 0
                                        blinkDone = false
                                        eyesClosed = false
                                    }
                                }

                                LivenessStep.BLINK -> {
                                    if (blinkDone) return@AdminFaceAbsensiCamera

                                    val left = face.leftEyeOpenProbability
                                    val right = face.rightEyeOpenProbability

                                    if (left == null || right == null) return@AdminFaceAbsensiCamera

                                    Log.d(TAG_ADMIN_ABSEN, "👁️ BLINK: L=${"%.2f".format(left)} R=${"%.2f".format(right)} closed=$eyesClosed done=$blinkDone")

                                    if (left < 0.25f && right < 0.25f) {
                                        if (!eyesClosed) {
                                            eyesClosed = true
                                            Log.d(TAG_ADMIN_ABSEN, "👁️ EYES CLOSED ✅")
                                        }
                                        return@AdminFaceAbsensiCamera
                                    }

                                    if (eyesClosed && left > 0.6f && right > 0.6f) {
                                        Log.d(TAG_ADMIN_ABSEN, "👁️ ✅ BLINK COMPLETE → incrementing")
                                        blinkDone = true
                                        eyesClosed = false

                                        if (!isTransitioningStep) {
                                            isTransitioningStep = true
                                            scope.launch {
                                                delay(STEP_TRANSITION_DELAY_MS)
                                                currentLivenessIndex = stepIndex + 1
                                                isTransitioningStep = false
                                            }
                                        }
                                    }
                                }

                                LivenessStep.TURN_RIGHT -> {
                                    if (turnRightDone) return@AdminFaceAbsensiCamera

                                    val yaw = face.headEulerAngleY
                                    Log.d(TAG_ADMIN_ABSEN, "➡️ TURN_RIGHT yaw=${"%.1f".format(yaw)}")

                                    if (yaw < -20f) {
                                        turnFrame++
                                        if (turnFrame >= 2) {
                                            turnRightDone = true
                                            turnFrame = 0

                                            if (!isTransitioningStep) {
                                                isTransitioningStep = true
                                                scope.launch {
                                                    delay(STEP_TRANSITION_DELAY_MS)
                                                    currentLivenessIndex = stepIndex + 1
                                                    isTransitioningStep = false
                                                }
                                            }
                                        }
                                    }
                                }

                                LivenessStep.TURN_LEFT -> {
                                    if (turnLeftDone) return@AdminFaceAbsensiCamera

                                    val yaw = face.headEulerAngleY
                                    Log.d(TAG_ADMIN_ABSEN, "⬅️ TURN_LEFT yaw=${"%.1f".format(yaw)}")

                                    if (yaw > 20f) {
                                        turnFrame++
                                        if (turnFrame >= 2) {
                                            turnLeftDone = true
                                            turnFrame = 0

                                            if (!isTransitioningStep) {
                                                isTransitioningStep = true
                                                scope.launch {
                                                    delay(STEP_TRANSITION_DELAY_MS)
                                                    currentLivenessIndex = stepIndex + 1
                                                    isTransitioningStep = false
                                                }
                                            }
                                        }
                                    }
                                }

                                LivenessStep.SMILE -> {
                                    if (smileDone) return@AdminFaceAbsensiCamera

                                    val smileProb = face.smilingProbability
                                    if (smileProb == null) return@AdminFaceAbsensiCamera

                                    Log.d(TAG_ADMIN_ABSEN, "😄 SMILE: prob=${"%.2f".format(smileProb)}")

                                    if (smileProb > 0.6f) {
                                        smilingFrame++
                                        if (smilingFrame >= 2) {
                                            smileDone = true
                                            smilingFrame = 0

                                            if (!isTransitioningStep) {
                                                isTransitioningStep = true
                                                scope.launch {
                                                    delay(STEP_TRANSITION_DELAY_MS)
                                                    currentLivenessIndex = stepIndex + 1
                                                    isTransitioningStep = false
                                                }
                                            }
                                        }
                                    }
                                }

                                else -> {}
                            }
                        },
                        onCaptured = { bmp ->
                            if (bmp == null) {
                                statusText = "Wajah tidak valid"
                                statusColor = Color.Red
                                isCapturing = false
                                resetLiveness(autoRestart = true)
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
                                    statusText = ""

                                    // 🔥 RESET LIVENESS STATE
                                    isSessionStarted = false
                                    livenessSteps = emptyList()
                                    currentLivenessIndex = 0
                                    stepStartTime = 0L
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
                enabled = isCameraReady && !isUploading && !isCapturing && !isSessionStarted,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB63352),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(5.dp),
                onClick = {

                    livenessSteps = LivenessStep.values()
                        .toList()
                        .shuffled()
                        .take(2)
                    currentLivenessIndex = 0
                    stepStartTime = System.currentTimeMillis()
                    isSessionStarted = true
                }
            ) {
                Text(
                    when {
                        isUploading -> "Lihat Ke Kamera"
                        isCapturing -> "Lihat Ke Kamera"
                        isSessionStarted -> "Ikuti instruksi"
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LivenessInstructionText(
    isSessionStarted: Boolean,
    currentStep: LivenessStep?,
    headUpDone: Boolean,
    livenessPassed: Boolean
) {
    val instructionText = when {
        !isSessionStarted ->
            "Tekan tombol di bawah untuk mulai absensi"

        currentStep == LivenessStep.HEAD_NOD ->
            if (!headUpDone) "⬆️ Gerakkan kepala (atas / bawah)"
            else "⬇️ Kembali ke posisi normal"

        currentStep == LivenessStep.BLINK ->
            "👁️ Kedipkan mata"

        currentStep == LivenessStep.TURN_RIGHT ->
            "➡️ Hadapkan wajah ke kanan"

        currentStep == LivenessStep.TURN_LEFT ->
            "⬅️ Hadapkan wajah ke kiri"

        currentStep == LivenessStep.SMILE ->
            "😄 Senyum ke kamera"

        livenessPassed ->
            "✅ Liveness selesai"

        else -> ""
    }

    val instructionColor = when {
        !isSessionStarted -> Color.Gray
        livenessPassed -> Color(0xFF2E7D32)
        else -> Color(0xFFFF9800)
    }

    AnimatedContent(
        targetState = instructionText,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically { it / 4 }) with
                    (fadeOut(tween(180)) + slideOutVertically { -it / 4 })
        },
        label = "LivenessInstruction"
    ) { text ->
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = instructionColor,
            textAlign = TextAlign.Center
        )
    }
}