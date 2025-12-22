package id.my.matahati.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DeviceInfoScreen() {

    val context = LocalContext.current
    val session = SessionManager(context)

    val deviceId = MyApp.DEVICE_ID
    val userName = session.getName() ?: "-"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Informasi Device Admin",
            style = MaterialTheme.typography.titleLarge
        )

        Text("User Admin:")
        Text(
            text = userName,
            fontWeight = FontWeight.Bold
        )

        Divider()

        Text("Device ID (ANDROID_ID):")
        Text(
            text = deviceId,
            fontWeight = FontWeight.Bold
        )

        Button(
            onClick = {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("device_id", deviceId)
                )
            }
        ) {
            Text("Copy Device ID")
        }
    }
}
