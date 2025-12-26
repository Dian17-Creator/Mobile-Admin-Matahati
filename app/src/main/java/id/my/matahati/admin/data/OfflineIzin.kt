package id.my.matahati.admin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_izin")
data class OfflineIzin(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val adminId : Int,
    val date: String,
    val lat: String,
    val lng: String,
    val placeName: String,
    val category: String,
    val reason: String,
    val photoBase64: String,
    val createdAt: Long = System.currentTimeMillis()
)
