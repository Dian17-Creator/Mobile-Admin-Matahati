package id.my.matahati.admin

import okhttp3.*
import android.content.Intent
import android.os.Bundle
import org.json.JSONObject
import java.io.IOException
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity

class LoginPage : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session = SessionManager(applicationContext)

        // 🔹 Cek apakah user sudah login dan centang "ingatkan saya"
        if (session.isRememberMe() && session.isLoggedIn()) {
            val userId = session.getUserId()
            val user = session.getUser()
            val userName = user["name"]?.toString() ?: ""
            val userEmail = user["email"]?.toString() ?: ""

            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("USER_ID", userId)
                putExtra("USER_NAME", userName)
                putExtra("USER_EMAIL", userEmail)
            }
            startActivity(intent)
            finish()
            return
        }

        // Kalau belum login, tampilkan form login
        setContent {
            LoginUI()
        }
    }
}

// ✅ Fungsi login
fun loginUser(
    context: ComponentActivity,
    email: String,
    password: String,
    onResult: (Boolean, String, JSONObject?) -> Unit
) {
    val client = OkHttpClient()
    val url = "https://absensi.matahati.my.id/login_admin_mobile.php?api=1"

    val formBody = FormBody.Builder()
        .add("email", email)
        .add("password", password)
        .build()

    val request = Request.Builder()
        .url(url)
        .post(formBody)
        .addHeader("Accept", "application/json")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            context.runOnUiThread {
                onResult(false, "Network error: ${e.message}", null)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                val bodyString = response.body?.string() ?: ""
                try {
                    val json = JSONObject(bodyString)
                    val status = json.optString("status", "error")
                    val message = json.optString("message", "Terjadi kesalahan")

                    if (status == "ok") {
                        val user = JSONObject().apply {
                            put("id", json.optInt("admin_id", -1))
                            put("name", json.optString("admin_name", ""))
                            put("email", json.optString("email", ""))
                            put("password", json.optString("password", ""))
                        }
                        context.runOnUiThread {
                            onResult(true, message, user)
                        }
                    } else {
                        context.runOnUiThread {
                            onResult(false, message, null)
                        }
                    }
                } catch (e: Exception) {
                    context.runOnUiThread {
                        onResult(false, "Invalid response: $bodyString", null)
                    }
                }
            }
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginUI() {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var rememberMe by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current as ComponentActivity
    val focusManager = LocalFocusManager.current
    val primaryColor = Color(0xFFFF6F51)

    val isKeyboardOpen by keyboardAsState()
    val animatedOffset by animateDpAsState(
        targetValue = if (isKeyboardOpen) (-180).dp else 0.dp,
        animationSpec = tween(durationMillis = 150),
        label = "loginSlide"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .offset(y = animatedOffset)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.tablet_login),
                contentDescription = "Login",
                modifier = Modifier
                    .size(260.dp)
                    .padding(bottom = 24.dp),
                contentScale = ContentScale.Fit
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Email") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    focusedLabelColor = primaryColor,
                    cursorColor = primaryColor
                ),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = {
                    focusManager.moveFocus(FocusDirection.Down)
                }),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(image, contentDescription = null)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    focusedLabelColor = primaryColor,
                    cursorColor = primaryColor
                ),
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    handleLogin(context, username, password, rememberMe)
                }),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = primaryColor,
                        uncheckedColor = primaryColor,
                        checkmarkColor = Color.White
                    )
                )
                Text("Ingatkan saya")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    handleLogin(context, username, password, rememberMe)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("LOGIN")
            }
        }
    }
}

// ✅ Logika handle login
fun handleLogin(context: ComponentActivity, email: String, password: String, rememberMe: Boolean) {
    loginUser(context, email, password) { success, msg, userJson ->
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

        if (success && userJson != null) {
            val userId = userJson.optInt("id", -1)
            val userName = userJson.optString("name", "")
            val userEmail = userJson.optString("email", "")

            val session = SessionManager(context.applicationContext)

            // Simpan sementara ke cache & prefs agar tidak hilang di antara Activity
            SessionManager.SessionCache.tempPassword = password
            session.saveTempPassword(password)

            if (rememberMe) {
                session.saveUser(userId, userName, userEmail, password)
                session.setRememberMe(true)
                session.clearTempPassword()
            } else {
                // ✅ Simpan data minimal agar AbsenManual bisa jalan
                session.saveTempUser(userEmail, password)
                session.setRememberMe(false)
            }

            // ✅ Pindah ke halaman QR
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("USER_ID", userId)
                putExtra("USER_NAME", userName)
                putExtra("USER_EMAIL", userEmail)
            }
            context.startActivity(intent)
            context.finish()
        }
    }
}



@Composable
fun keyboardAsState(): State<Boolean> {
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val isImeVisible = ime.getBottom(density) > 0
    val keyboardState = remember { mutableStateOf(isImeVisible) }

    LaunchedEffect(isImeVisible) {
        keyboardState.value = isImeVisible
    }
    return keyboardState
}
