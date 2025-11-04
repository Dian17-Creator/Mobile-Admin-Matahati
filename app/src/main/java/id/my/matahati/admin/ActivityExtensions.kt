package id.my.matahati.admin

import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity

fun Context.launchWithSlide(target: Class<*>) {
    val intent = Intent(this, target)
    this.startActivity(intent)
    if (this is ComponentActivity) {
        this.overridePendingTransition(
            R.anim.slide_in_right,   // pakai file XML buatanmu
            R.anim.slide_out_left
        )
    }
}
