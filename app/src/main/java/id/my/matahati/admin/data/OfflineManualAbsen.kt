package id.my.matahati.admin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_manual_absen")
data class OfflineManualAbsen(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val userEmail: String,
    val userPassword: String,
    val reason: String,

    val lat: String,
    val lng: String,
    val placeName: String,

    val photoBase64: String,
    val createdAt: Long
)
