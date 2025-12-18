package id.my.matahati.admin

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TAG_ADMIN_ABSEN = "ADMIN_FACE_ABSEN"
private const val ADMIN_FACE_ABSEN_URL = "https://absensi.matahati.my.id/user_face_scan_mobile.php"

private const val API_KEY = "MH4T4H4TI_2025_ABSENSI_APP_SECRETx9P2F7Q1L8S3Z0R6W4K2D1M9B7T5"

private val httpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

object AdminFaceAbsenDetector {
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .build()

    val detector: FaceDetector by lazy {
        FaceDetection.getClient(options)
    }
}

fun isFaceValidForAdmin(face: Face): Boolean {
    if (face.boundingBox.width() < 160) return false
    if (kotlin.math.abs(face.headEulerAngleY) > 25) return false
    if (kotlin.math.abs(face.headEulerAngleX) > 20) return false
    return true
}

class AbsensiWajahAdmin : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                200
            )
        }

        val targetUserId = intent.getIntExtra("USER_ID", -1)

        setContent {
            AdminFaceAbsensiScreen(targetUserId)
        }
    }
}

@Composable
fun AdminFaceAbsensiScreen(targetUserId: Int) {

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

    LaunchedEffect(Unit) {
        val activity = context as Activity

        val hasPermission =
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            place = "Lokasi tidak diizinkan"
            return@LaunchedEffect
        }

        val fused = LocationServices.getFusedLocationProviderClient(activity)
        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                lat = loc.latitude
                lng = loc.longitude
                scope.launch {
                    val name = reverseGeocode(loc.latitude, loc.longitude)
                    place = if (name.isNotEmpty()) name else "${lat}, ${lng}"
                }
            } else {
                place = "Lokasi tidak tersedia"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = { (context as Activity).finish() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Default.ArrowBack, null)
            }
            Text(
                "ABSENSI WAJAH (ADMIN)",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            AdminFaceAbsensiCamera(
                onReady = { isCameraReady = true },
                onCaptured = { bmp ->
                    if (bmp == null) {
                        statusText = "Wajah tidak valid"
                        statusColor = Color.Red
                        isCapturing = false
                        return@AdminFaceAbsensiCamera
                    }

                    if (targetUserId <= 0) {
                        statusText = "User target tidak valid"
                        statusColor = Color.Red
                        isCapturing = false
                        return@AdminFaceAbsensiCamera
                    }

                    scope.launch {
                        isUploading = true
                        val result = uploadAdminFaceAbsensi(
                            bmp, adminId, targetUserId, lat, lng, place
                        )

                        statusText = result.message
                        statusColor =
                            if (result.success) Color(0xFF2E7D32) else Color.Red

                        if (result.success) {
                            delay(1500)
                            (context as Activity).finish()
                        }

                        isUploading = false
                        isCapturing = false
                    }
                }
            )

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
            onClick = {
                isCapturing = true
                AdminCameraController.capture()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (isUploading) "Memindai..." else "Ambil Foto")
        }

        if (statusText.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(statusText, color = statusColor, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AdminFaceAbsensiCamera(
    onReady: () -> Unit,
    onCaptured: (Bitmap?) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            ProcessCameraProvider.getInstance(ctx).addListener({
                val cameraProvider = ProcessCameraProvider.getInstance(ctx).get()

                val preview = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageCapture = ImageCapture.Builder().build()

                AdminCameraController.capture = {
                    imageCapture.takePicture(
                        executor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    val bmp = imageProxyToBitmap(image)
                                    val rotated = rotateBitmap(
                                        bmp,
                                        image.imageInfo.rotationDegrees.toFloat()
                                    )

                                    val input = InputImage.fromBitmap(rotated, 0)

                                    AdminFaceAbsenDetector.detector
                                        .process(input)
                                        .addOnSuccessListener { faces ->
                                            if (faces.size != 1) {
                                                onCaptured(null)
                                                return@addOnSuccessListener
                                            }

                                            val face = faces.first()
                                            if (!isFaceValidForAdmin(face)) {
                                                onCaptured(null)
                                                return@addOnSuccessListener
                                            }

                                            val cropped = cropFaceForLogin(rotated, face)
                                            val resized = resizeFaceForLogin(cropped)
                                            onCaptured(resized)
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e(TAG_ADMIN_ABSEN, "Face detection failed", e)
                                            onCaptured(null)
                                        }
                                } catch (e: Exception) {
                                    Log.e(TAG_ADMIN_ABSEN, "Capture error", e)
                                    onCaptured(null)
                                } finally {
                                    image.close()
                                }
                            }
                        }
                    )
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageCapture
                )

                onReady()
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

data class AdminFaceResponse(
    val success: Boolean,
    val message: String
)

suspend fun uploadAdminFaceAbsensi(
    bitmap: Bitmap,
    adminId: Int,
    targetUserId: Int,
    lat: Double?,
    lng: Double?,
    place: String?
): AdminFaceResponse = withContext(Dispatchers.IO) {
    try {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val imageBytes = stream.toByteArray()

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("api_key", API_KEY)
            .addFormDataPart("adminId", adminId.toString())
            .addFormDataPart("userId", targetUserId.toString()) // ⬅️ FIX
            .addFormDataPart("lat", lat?.toString() ?: "")
            .addFormDataPart("lng", lng?.toString() ?: "")
            .addFormDataPart("place", place ?: "")
            .addFormDataPart(
                "facefile",
                "face.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaType())
            )
            .build()


        val request = Request.Builder()
            .url(ADMIN_FACE_ABSEN_URL)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful || txt.isEmpty()) {
                return@withContext AdminFaceResponse(false, "Server No RESPONS")
            }

            val obj = JSONObject(txt)
            AdminFaceResponse(
                obj.optBoolean("success", false),
                obj.optString("message", "Absensi gagal")
            )
        }
    } catch (e: Exception) {
        Log.e(TAG_ADMIN_ABSEN, "Upload error", e)
        AdminFaceResponse(false, "Koneksi gagal")
    }
}

fun cropFaceForLogin(bitmap: Bitmap, face: Face): Bitmap {
    val b = face.boundingBox
    val left = b.left.coerceAtLeast(0)
    val top = b.top.coerceAtLeast(0)
    val w = b.width().coerceAtMost(bitmap.width - left)
    val h = b.height().coerceAtMost(bitmap.height - top)
    return Bitmap.createBitmap(bitmap, left, top, w, h)
}

fun resizeFaceForLogin(bitmap: Bitmap, size: Int = 320): Bitmap =
    Bitmap.createScaledBitmap(bitmap, size, size, true)

suspend fun reverseGeocode(lat: Double, lng: Double): String =
    withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(
                    "https://nominatim.openstreetmap.org/reverse" +
                            "?lat=$lat&lon=$lng&format=json&addressdetails=0"
                )
                .header("User-Agent", "MatahatiAbsensiAdmin/1.0")
                .build()

            httpClient.newCall(req).execute().use {
                val body = it.body?.string() ?: return@withContext ""
                JSONObject(body).optString("display_name", "")
            }
        } catch (_: Exception) {
            ""
        }
    }
