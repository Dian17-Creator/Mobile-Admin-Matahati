package id.my.matahati.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject


// ✅ OkHttpClient Singleton (agar tidak membuat instance berulang)
object HttpClientSingleton {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .build()
    }
}

class LoginPage : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session = SessionManager(applicationContext)

        // 🔹 Jika user sudah login dan RememberMe aktif → langsung ke MainActivity
        if (session.isRememberMe()) {

            val email = session.getUserEmail()
            val password = session.getPassword() // 🔑 ambil dari session

            if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
                autoLogin(this, email, password)
                return
            } else {
                session.logout()
            }
        }

        // 🔹 Jika belum login → tampilkan UI
        setContent {
            MaterialTheme {
                LoginUI()
            }
        }
    }
}

// ✅ Fungsi Login (lebih efisien & coroutine-friendly)
suspend fun loginUser(
    email: String,
    password: String
): Triple<String, String, JSONObject?> = withContext(Dispatchers.IO) {

    val client = HttpClientSingleton.client
    val url = "https://absensi.matahati.my.id/login_admin_mobile.php?api=1"

    try {
        val formBody = FormBody.Builder()
            .add("email", email)
            .add("password", password)
            .add("device_id", MyApp.DEVICE_ID)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: "{}"
        val json = JSONObject(body)

        val status = json.optString("status", "error")
        val msg = json.optString("message", "Terjadi kesalahan")

        if (status == "ok") {
            val user = JSONObject().apply {
                put("id", json.optInt("admin_id", -1))
                put("name", json.optString("admin_name", ""))
                put("email", json.optString("email", ""))
                put("department", json.optString("cdeptname", ""))
                put("fadmin", json.optInt("fadmin", 0))
                put("fsuper", json.optInt("fsuper", 0))
                put("fhrd", json.optInt("fhrd", 0))
            }
            Triple("ok", msg, user)
        } else {
            Triple(status, msg, null)
        }

    } catch (e: Exception) {
        Triple("error", e.message ?: "Network error", null)
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    name = "Login Preview"
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginUI() {
    val context = LocalContext.current as ComponentActivity
    val focusManager = LocalFocusManager.current
    val primaryColor = Color(0xFFB63352)

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // ✅ Layout utama (scrollable + padding aman untuk keyboard)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime)
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            LoginImage()

            Spacer(Modifier.height(24.dp))

            LoginTextField(
                value = username,
                label = "Email",
                onValueChange = { username = it },
                primaryColor = primaryColor,
                focusManager = focusManager
            )

            Spacer(Modifier.height(16.dp))

            PasswordTextField(
                password = password,
                passwordVisible = passwordVisible,
                onPasswordChange = { password = it },
                onToggleVisibility = { passwordVisible = !passwordVisible },
                onDone = {
                    focusManager.clearFocus()
                    handleLogin(context, username, password, rememberMe, { isLoading = it })
                },
                primaryColor = primaryColor
            )

            Spacer(Modifier.height(16.dp))

            RememberMeCheckbox(
                checked = rememberMe,
                onCheckedChange = { rememberMe = it },
                primaryColor = primaryColor
            )

            Spacer(Modifier.height(24.dp))

            LoginButton(
                isLoading = isLoading,
                onClick = {
                    handleLogin(context, username, password, rememberMe, { isLoading = it })
                },
                primaryColor = primaryColor
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ✅ Gambar header
@Composable
fun LoginImage() {
    Image(
        painter = painterResource(id = R.drawable.tablet_login),
        contentDescription = "Login",
        modifier = Modifier
            .size(240.dp)
            .padding(bottom = 8.dp),
        contentScale = ContentScale.FillWidth
    )
}

// ✅ Field Email
@Composable
fun LoginTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    primaryColor: Color,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
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
}

// ✅ Field Password
@Composable
fun PasswordTextField(
    password: String,
    passwordVisible: Boolean,
    onPasswordChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onDone: () -> Unit,
    primaryColor: Color
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = if (passwordVisible)
            VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            val image =
                if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
            IconButton(onClick = onToggleVisibility) {
                Icon(image, contentDescription = null)
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = primaryColor,
            focusedLabelColor = primaryColor,
            cursorColor = primaryColor
        ),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth()
    )
}

// ✅ Checkbox "Ingatkan Saya"
@Composable
fun RememberMeCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, primaryColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = primaryColor,
                uncheckedColor = primaryColor,
                checkmarkColor = Color.White
            )
        )
        Text("Ingatkan saya")
    }
}

// ✅ Tombol Login
@Composable
fun LoginButton(isLoading: Boolean, onClick: () -> Unit, primaryColor: Color) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = primaryColor,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        enabled = !isLoading
    ) {
        Text(if (isLoading) "MEMUAT..." else "LOGIN")
    }
}

// ✅ Logika handle login (non-blocking dan efisien)
fun handleLogin(
    context: ComponentActivity,
    email: String,
    password: String,
    rememberMe: Boolean,
    isLoadingSetter: (Boolean) -> Unit
) {
    if (email.isBlank() || password.isBlank()) {
        Toast.makeText(context, "Email dan password wajib diisi!", Toast.LENGTH_SHORT).show()
        return
    }

    isLoadingSetter(true)

    context.lifecycleScope.launchWhenResumed {

        val (status, msg, userJson) = loginUser(email, password)
        isLoadingSetter(false)

        when (status) {

            "ok" -> {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

                val userId = userJson!!.optInt("id")
                val userName = userJson.optString("name")
                val userEmail = userJson.optString("email")

                val session = SessionManager(context)
                session.saveTempPassword(password)

                val dept = userJson.optString("department")
                val fadmin = userJson.optInt("fadmin")
                val fsuper = userJson.optInt("fsuper")
                val fhrd = userJson.optInt("fhrd")

                if (rememberMe) {
                    session.saveUser(
                        userId,
                        userName,
                        userEmail,
                        password,
                        dept,
                        fadmin,
                        fsuper,
                        fhrd
                    )
                    session.setRememberMe(true)
                } else {
                    session.saveTempUser(userEmail, password)
                    session.setRememberMe(false)
                }

                context.startActivity(
                    Intent(context, MainActivity::class.java)
                )
                context.finish()
            }

            "pending" -> {
                Toast.makeText(
                    context,
                    "Device menunggu persetujuan HRD",
                    Toast.LENGTH_LONG
                ).show()
            }

            "rejected" -> {
                Toast.makeText(
                    context,
                    "Device ditolak. Hubungi HRD.",
                    Toast.LENGTH_LONG
                ).show()
            }

            else -> {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun autoLogin(
    context: ComponentActivity,
    email: String,
    password: String
) {
    context.lifecycleScope.launchWhenResumed {
        val (status, msg, _) = loginUser(email, password)

        if (status == "ok") {
            context.startActivity(Intent(context, MainActivity::class.java))
            context.finish()
        } else {
            SessionManager(context).logout()
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}

