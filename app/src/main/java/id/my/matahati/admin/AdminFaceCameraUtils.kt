package id.my.matahati.admin

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
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

const val TAG_ADMIN_ABSEN = "ADMIN_FACE_ABSEN"
private const val ADMIN_FACE_ABSEN_URL = "https://absensi.matahati.my.id/admin_face_scan_ssid.php"
private const val API_KEY = "MH4T4H4TI_2025_ABSENSI_APP_SECRETx9P2F7Q1L8S3Z0R6W4K2D1M9B7T5"
private const val MSG_DEVICE_NOT_REGISTERED = "Device belum terdaftar"

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
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // 🔥 INI ADA
        .setMinFaceSize(0.15f)
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AdminFaceAbsensiCamera(
    onReady: () -> Unit,
    onFaceFrame: (Face) -> Unit,
    onCaptured: (Bitmap?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageCapture = ImageCapture.Builder().build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            // =========================
            // FACE ANALYZER
            // =========================
            imageAnalysis.setAnalyzer(executor) { imageProxy ->

                val media = imageProxy.image ?: run {
                    imageProxy.close()
                    return@setAnalyzer
                }

                val img = InputImage.fromMediaImage(
                    media,
                    imageProxy.imageInfo.rotationDegrees
                )

                AdminFaceAbsenDetector.detector
                    .process(img)
                    .addOnSuccessListener { faces ->
                        if (faces.size == 1) {
                            val face = faces.first()
                            Handler(Looper.getMainLooper()).post {
                                onFaceFrame(face)
                            }
                        }
                        imageProxy.close()
                    }
                    .addOnFailureListener {
                        imageProxy.close()
                    }
            }

            // =========================
            // CAPTURE LOGIC (WAJIB ADA)
            // =========================
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
                                    .addOnFailureListener {
                                        onCaptured(null)
                                    }

                            } catch (e: Exception) {
                                onCaptured(null)
                            } finally {
                                image.close()
                            }
                        }
                    }
                )
            }

            // =========================
            // BIND CAMERA
            // =========================
            cameraProvider.unbindAll()

            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                imageCapture,
                imageAnalysis
            )

            onReady()

        }, ContextCompat.getMainExecutor(context))


        onDispose {
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { previewView }
    )
}


data class AdminFaceResponse(
    val success: Boolean,
    val message: String
)

suspend fun uploadAdminFaceLogin(
    bitmap: Bitmap,
    adminId: Int,
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
            .url("https://absensi.matahati.my.id/admin_face_scan_mobile.php")
            .addHeader("X-DEVICE-ID", MyApp.DEVICE_ID)
            .post(body)
            .build()

        httpClient.newCall(request).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (txt.isEmpty()) {
                return@withContext AdminFaceResponse(false, "Server tidak merespons")
            }

            val obj = JSONObject(txt)
            val success = obj.optBoolean("success", false)
            val message = obj.optString("message", "Login wajah gagal")

            if (!success && message.contains("device", true)) {
                return@withContext AdminFaceResponse(false, MSG_DEVICE_NOT_REGISTERED)
            }

            return@withContext AdminFaceResponse(success, message)
        }

    } catch (e: Exception) {
        Log.e(TAG_ADMIN_ABSEN, "Face login error", e)
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
            val request = Request.Builder()
                .url(
                    "https://absensi.matahati.my.id/reverse_geocode.php" +
                            "?lat=$lat&lon=$lng"
                )
                .build()

            httpClient.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || !body.startsWith("{")) {
                    return@withContext "$lat,$lng"
                }

                val obj = JSONObject(body)
                val place = obj.optString("display_name", "")

                if (place.isNotBlank()) place else "$lat,$lng"
            }

        } catch (e: Exception) {
            "$lat,$lng"
        }
    }

