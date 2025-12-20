package id.my.matahati.admin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

data class UserItem(
    val id: Int,
    val name: String
)
enum class AdminFaceStatus {
    NONE,
    REGISTERED
}

private const val TAG_FACE = "ADMIN_FACE_REGISTER"
private val httpClient by lazy { OkHttpClient() }
private const val FACE_UPLOAD_URL = "https://absensi.matahati.my.id/admin_face_register.php"
private const val FACE_CHECK_URL = "https://absensi.matahati.my.id/check_face_registered.php"

object AdminFaceDetector {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .build()

    val detector: FaceDetector by lazy {
        FaceDetection.getClient(options)
    }
}

fun isFaceValid(face: Face): Boolean {
    if (face.boundingBox.width() < 200) return false
    if (kotlin.math.abs(face.headEulerAngleY) > 15) return false
    if (kotlin.math.abs(face.headEulerAngleX) > 15) return false
    return true
}

fun cropFace(bitmap: Bitmap, face: Face): Bitmap {
    val b = face.boundingBox
    return Bitmap.createBitmap(
        bitmap,
        b.left.coerceAtLeast(0),
        b.top.coerceAtLeast(0),
        b.width().coerceAtMost(bitmap.width),
        b.height().coerceAtMost(bitmap.height)
    )
}

fun resizeFace(bitmap: Bitmap, size: Int = 360): Bitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)

class RegistrasiWajahAdmin : ComponentActivity() {

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
        setContent {
            AdminFaceRegisterScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminFaceRegisterScreen() {

    val primaryColor = Color(0xFFB63352)
    val context = LocalContext.current
    val session = remember { SessionManager(context) }
    val scope = rememberCoroutineScope()

    val poses = listOf("Hadap Depan (Netral)", "Miring Sedikit ke Kanan", "Miring Sedikit ke Kiri")
    val poseBitmaps: SnapshotStateMap<Int, Bitmap> = remember { mutableStateMapOf() }

    var users by remember { mutableStateOf(listOf<UserItem>()) }
    var selectedUser by remember { mutableStateOf<UserItem?>(null) }
    var expanded by remember { mutableStateOf(false) }

    var faceStatus by remember { mutableStateOf(AdminFaceStatus.NONE) }
    var isUploading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loadUsers(session) { users = it }
    }

    val nextPose = (0 until poses.size).firstOrNull { !poseBitmaps.containsKey(it) }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .align(Alignment.BottomCenter)
                .background(primaryColor)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 32.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { (context as? android.app.Activity)?.finish() },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Kembali",
                        tint = primaryColor
                    )
                }

                Text(
                    text = "Registrasi Wajah",
                    fontSize = 22.sp,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Ikuti instruksi berikut untuk hasil yang lebih akurat",
                fontSize = 13.sp,
                color = Color.Gray
            )

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedUser?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pilih Pegawai") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                            },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                focusedLabelColor = primaryColor,
                                cursorColor = primaryColor
                            ),
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            users.forEach { user ->
                                DropdownMenuItem(
                                    text = { Text(user.name) },
                                    onClick = {
                                        expanded = false
                                        selectedUser = user
                                        poseBitmaps.clear()
                                        message = null

                                        scope.launch {
                                            faceStatus =
                                                if (checkUserFaceRegistered(user.id))
                                                    AdminFaceStatus.REGISTERED
                                                else AdminFaceStatus.NONE
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    when {
                        selectedUser == null -> {
                        }

                        faceStatus == AdminFaceStatus.REGISTERED -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "Wajah sudah terdaftar!",
                                    color = Color(0xFF2E7D32),
                                    textAlign = TextAlign.Center
                                )

                                Button(
                                    onClick = {
                                        val intent = Intent(context, AbsensiWajahAdmin::class.java).apply {
                                            putExtra("USER_ID", selectedUser!!.id)
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFB63352),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(5.dp)
                                ) {
                                    Text(
                                        "ABSENS WAJAH",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        else -> {
                            Text(
                                "Pose : ${nextPose?.let { poses[it] } ?: "Lengkap ✔"}",
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(Modifier.height(5.dp))

                            Text(
                                "pastikan wajah penuh di dalam frame hijau",
                                fontWeight = FontWeight.Normal
                            )

                            Spacer(Modifier.height(12.dp))

                            if (nextPose != null) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(325.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    AdminCameraPreview { bmp: Bitmap ->
                                        poseBitmaps[nextPose] = bmp
                                    }

                                    Box(
                                        Modifier
                                            .fillMaxWidth(0.7f)
                                            .aspectRatio(3f / 4f)
                                            .border(2.dp, Color.Green, RoundedCornerShape(10.dp))
                                            .align(Alignment.Center)
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                Button(
                                    onClick = { AdminCameraController.capture() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFB63352),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(5.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                ) {
                                    Text("Ambil Foto")
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(count = poses.size) { index ->
                                val bmp = poseBitmaps[index]
                                    if (bmp != null) {
                                        Box {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .width(80.dp)
                                                    .height(105.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .graphicsLayer { scaleX = -1f },
                                                contentScale = ContentScale.Crop
                                            )

                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Hapus foto",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(20.dp)
                                                    .background(Color.Red, RoundedCornerShape(50))
                                                    .padding(2.dp)
                                                    .clickable {
                                                        poseBitmaps.remove(index)
                                                        message = "Pose dihapus, silakan ambil ulang"
                                                    }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Button(
                                enabled = poseBitmaps.size == 3 && !isUploading,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFB63352),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(5.dp),
                                onClick = {
                                    scope.launch {
                                        isUploading = true
                                        val ok = withContext(Dispatchers.IO) {
                                            uploadAllAdminFaces(
                                                selectedUser!!.id,
                                                poseBitmaps.values.toList()
                                            )
                                        }
                                        isUploading = false
                                        if (ok) faceStatus = AdminFaceStatus.REGISTERED
                                    }
                                }

                            ) {
                                Text(if (isUploading) "Menyimpan..." else "Simpan Semua Foto")
                            }
                        }
                    }

                    message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

object AdminCameraController {
    var capture: () -> Unit = {}
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun AdminCameraPreview(
    onImageCaptured: (Bitmap) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val currentOnImageCaptured by rememberUpdatedState(onImageCaptured)

    AndroidView(factory = { ctx ->
        val previewView = PreviewView(ctx)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageCapture = ImageCapture.Builder().build()

            AdminCameraController.capture = {
                imageCapture.takePicture(
                    executor,
                    object : ImageCapture.OnImageCapturedCallback() {

                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                val rawBitmap = imageProxyToBitmap(image)

                                val rotated = rotateBitmap(
                                    rawBitmap,
                                    image.imageInfo.rotationDegrees.toFloat()
                                )

                                val inputImage =
                                    InputImage.fromBitmap(rotated, 0)

                                AdminFaceDetector.detector
                                    .process(inputImage)
                                    .addOnSuccessListener { faces ->
                                        if (faces.size != 1) return@addOnSuccessListener

                                        val face = faces.first()
                                        if (!isFaceValid(face)) return@addOnSuccessListener

                                        val cropped = cropFace(rotated, face)
                                        val finalFace = resizeFace(cropped)

                                        currentOnImageCaptured(finalFace)
                                    }
                                    .addOnFailureListener {
                                        Log.e(TAG_FACE, "ML Kit failed", it)
                                    }

                            } catch (e: Exception) {
                                Log.e(TAG_FACE, "Capture error", e)
                            } finally {
                                image.close()
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG_FACE, "ImageCapture error", exception)
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

        }, ContextCompat.getMainExecutor(ctx))

        previewView
    })
}

suspend fun checkUserFaceRegistered(userId: Int): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$FACE_CHECK_URL?user_id=$userId")
                .build()
            val res = httpClient.newCall(req).execute()
            JSONObject(res.body?.string() ?: "{}")
                .optBoolean("registered", false)
        } catch (_: Exception) {
            false
        }
    }
suspend fun uploadAllAdminFaces(
    userId: Int,
    bitmaps: List<Bitmap>
): Boolean {

    if (bitmaps.size < 3) return false

    for ((index, bmp) in bitmaps.withIndex()) {

        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
        val base64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)

        val json = JSONObject().apply {
            put("userId", userId)
            put("pose", index) // ⬅️ PENTING
            put("photoBase64", base64)
        }

        val body = json.toString()
            .toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url(FACE_UPLOAD_URL)
            .post(body)
            .build()

        val res = httpClient.newCall(req).execute()

        res.use { response ->
            if (!response.isSuccessful) return false

            val respBody = response.body?.string() ?: return false
            val respJson = JSONObject(respBody)

            if (!respJson.optBoolean("success")) {
                Log.e(TAG_FACE, "Upload failed: $respBody")
                return false
            }
        }
    }
    return true
}
suspend fun loadUsers(
    session: SessionManager,
    onResult: (List<UserItem>) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val adminId = session.getUserId()
            val req = Request.Builder()
                .url("https://absensi.matahati.my.id/get_users_by_department.php?admin_id=$adminId")
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext
            val json = JSONObject(body)

            if (json.optBoolean("success")) {
                val arr = json.getJSONArray("data")
                val list = mutableListOf<UserItem>()

                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        UserItem(
                            id = o.getInt("nid"),
                            name = o.getString("cname")
                        )
                    )
                }
                withContext(Dispatchers.Main) {
                    onResult(list)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_FACE, "loadUsers error", e)
        }
    }
}

fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

fun rotateBitmap(src: Bitmap, rotation: Float): Bitmap {
    val matrix = android.graphics.Matrix().apply {
        postRotate(rotation)
    }
    return Bitmap.createBitmap(
        src, 0, 0, src.width, src.height, matrix, true
    )
}
