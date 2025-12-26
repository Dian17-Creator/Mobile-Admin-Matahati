package id.my.matahati.admin.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OfflineManualAbsenDao {
    @Insert
    suspend fun insert(data: OfflineManualAbsen)

    @Query("SELECT * FROM offline_manual_absen")
    suspend fun getAll(): List<OfflineManualAbsen>

    @Delete
    suspend fun delete(data: OfflineManualAbsen)
}
