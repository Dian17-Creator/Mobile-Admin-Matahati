package id.my.matahati.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class DeviceInfoAdminActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DeviceInfoAdminScreen()
        }
    }
}

@Composable
fun DeviceInfoAdminScreen() {

    val context = LocalContext.current
    val session = SessionManager(context)

    val deviceId = MyApp.DEVICE_ID
    val adminName = session.getUserName() ?: "-"
    val adminEmail = session.getUserEmail() ?: "-"

    val primaryColor = Color(0xFFB63352)
    val secondaryColor = Color(0xFFE8E8E8)

    Box(modifier = Modifier
        .fillMaxSize()
        .background(primaryColor)) {

        // ===== BACKGROUND ATAS (½ LAYAR) =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .background(secondaryColor)
        )

        // ===== CONTENT =====
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),

            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Informasi Device Admin",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )

            Spacer(modifier = Modifier.height(30.dp))

            // ===== CARD INFO =====
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(5.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    InfoItem(label = "Nama Admin", value = adminName)
                    Divider()
                    InfoItem(label = "Email Admin", value = adminEmail)
                    Divider()
                    InfoItem(label = "Device ID", value = deviceId)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("device_id", deviceId)
                    )
                },
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Copy Device ID")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { (context as? ComponentActivity)?.finish() },
                shape = RoundedCornerShape(5.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF0000),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Kembali")
            }
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    val primaryColor = Color(0xFFB63352)

    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = primaryColor,
        )
    }
}

