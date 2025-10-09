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
    }

    fun saveUser(id: Int, name: String, email: String, password: String) {
        prefs.edit().apply {
            putInt(KEY_ID, id)
            putString(KEY_NAME, name)
            putString(KEY_EMAIL, email)
            putString(KEY_PASSWORD, password)
            putBoolean(KEY_IS_LOGGED_IN, true)
            commit()
        }
    }

    fun getUserId(): Int = prefs.getInt(KEY_ID, -1)
    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)
    fun getUser(): Map<String, Any?> = mapOf(
        "id" to prefs.getInt(KEY_ID, -1),
        "name" to prefs.getString(KEY_NAME, null),
        "email" to prefs.getString(KEY_EMAIL, null)
    )

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

    // 🔹 Tambahan baru
    fun clearLoginButKeepTemp() {
        val temp = getTempPassword()
        prefs.edit().clear().apply()
        if (temp != null) saveTempPassword(temp)
    }

    object SessionCache {
        var tempPassword: String? = null
    }

    fun saveTempUser(email: String, password: String) {
        prefs.edit().apply {
            putString(KEY_EMAIL, email)          // 🔹 simpan email sementara
            putString(KEY_TEMP_PASSWORD, password) // 🔹 simpan password sementara
            putBoolean(KEY_IS_LOGGED_IN, true)
            commit()
        }
    }

}
