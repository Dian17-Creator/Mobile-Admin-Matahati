package id.my.matahati.admin

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_IS_LOGGED_IN = "isLoggedIn"
        private const val KEY_REMEMBER_ME = "rememberMe"
        private const val KEY_TEMP_PASSWORD = "tempPassword"

        // 🔹 Tambahan baru
        private const val KEY_DEPARTMENT = "department"
        private const val KEY_FADMIN = "fadmin"
        private const val KEY_FSUPER = "fsuper"
        private const val KEY_FHRD = "fhrd"
    }

    // ✅ Simpan data lengkap user termasuk role dan departemen
    fun saveUser(
        id: Int,
        name: String,
        email: String,
        password: String,
        department: String,
        fadmin: Int,
        fsuper: Int,
        fhrd: Int
    ) {
        prefs.edit().apply {
            putInt(KEY_ID, id)
            putString(KEY_NAME, name)
            putString(KEY_EMAIL, email)
            putString(KEY_PASSWORD, password)
            putString(KEY_DEPARTMENT, department)
            putInt(KEY_FADMIN, fadmin)
            putInt(KEY_FSUPER, fsuper)
            putInt(KEY_FHRD, fhrd)
            putBoolean(KEY_IS_LOGGED_IN, true)
            commit()
        }
    }

    fun getUserId(): Int = prefs.getInt(KEY_ID, -1)
    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)

    fun getUser(): Map<String, Any?> = mapOf(
        "id" to prefs.getInt(KEY_ID, -1),
        "name" to prefs.getString(KEY_NAME, null),
        "email" to prefs.getString(KEY_EMAIL, null),
        "department" to prefs.getString(KEY_DEPARTMENT, null),
        "fadmin" to prefs.getInt(KEY_FADMIN, 0),
        "fsuper" to prefs.getInt(KEY_FSUPER, 0),
        "fhrd" to prefs.getInt(KEY_FHRD, 0)
    )

    fun getDepartment(): String? = prefs.getString(KEY_DEPARTMENT, null)

    // ✅ Fungsi pengecekan role
    fun isAdmin(): Boolean = prefs.getInt(KEY_FADMIN, 0) == 1
    fun isSupervisor(): Boolean = prefs.getInt(KEY_FSUPER, 0) == 1
    fun isHRD(): Boolean = prefs.getInt(KEY_FHRD, 0) == 1

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    fun clearSession() {
        prefs.edit().clear().commit()
    }

    fun setRememberMe(value: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_ME, value).apply()
    }

    fun isRememberMe(): Boolean = prefs.getBoolean(KEY_REMEMBER_ME, false)

    fun saveTempPassword(password: String) {
        prefs.edit().putString(KEY_TEMP_PASSWORD, password).apply()
    }

    fun getTempPassword(): String? = prefs.getString(KEY_TEMP_PASSWORD, null)

    fun clearTempPassword() {
        prefs.edit().remove(KEY_TEMP_PASSWORD).apply()
    }

    // ✅ Hapus login tanpa hilangkan password sementara
    fun clearLoginButKeepTemp() {
        val temp = getTempPassword()
        prefs.edit().clear().apply()
        if (temp != null) saveTempPassword(temp)
    }

    object SessionCache {
        var tempPassword: String? = null
    }

    // ✅ Simpan data user sementara (tanpa role)
    fun saveTempUser(email: String, password: String) {
        prefs.edit().apply {
            putString(KEY_EMAIL, email)
            putString(KEY_TEMP_PASSWORD, password)
            putBoolean(KEY_IS_LOGGED_IN, true)
            commit()
        }
    }
}
