package id.my.matahati.admin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OfflineIzinDao {
    @Insert
    suspend fun insert(izin: OfflineIzin)

    @Query("SELECT * FROM offline_izin")
    suspend fun getAll(): List<OfflineIzin>

    @Query("DELETE FROM offline_izin WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM offline_izin")
    suspend fun clearAll()
}
