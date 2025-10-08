package id.my.matahati.admin

import android.content.Context
import android.content.SharedPreferences
import kotlin.to

class SessionManager(context: Context) {

    // Gunakan applicationContext untuk memastikan prefs global & tidak ikut lifecycle Activity
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_REMEMBER_ME = "rememberMe"
    }

    // ✅ Simpan data user (gunakan commit agar langsung tersimpan ke disk)
    fun saveUser(id: Int, name: String, email: String) {
        val editor = prefs.edit()
        editor.putInt(KEY_ID, id)
        editor.putString(KEY_NAME, name)
        editor.putString(KEY_EMAIL, email)
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.commit() // <-- gunakan commit() agar tersimpan sinkron sebelum Activity berganti
    }

    // ✅ Ambil user ID
    fun getUserId(): Int = prefs.getInt(KEY_ID, -1)

    // ✅ Ambil semua data user
    fun getUser(): Map<String, Any?> = mapOf(
        "id" to prefs.getInt(KEY_ID, -1),
        "name" to prefs.getString(KEY_NAME, null),
        "email" to prefs.getString(KEY_EMAIL, null)
    )

    // ✅ Cek apakah user sudah login
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    // ✅ Hapus seluruh session (untuk logout)
    fun clearSession() {
        val editor = prefs.edit()
        editor.clear()
        editor.commit() // commit agar langsung benar-benar dihapus dari disk
    }

    // ✅ Simpan status “ingatkan saya”
    fun setRememberMe(value: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_ME, value).apply() // apply cukup aman untuk hal kecil
    }

    // ✅ Ambil status “ingatkan saya”
    fun isRememberMe(): Boolean = prefs.getBoolean(KEY_REMEMBER_ME, false)
}
