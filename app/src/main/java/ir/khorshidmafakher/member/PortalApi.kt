package ir.khorshidmafakher.member

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class PortalApi(context: Context) {
    private val base = "https://portal.khorshid-mafakher.ir"
    private val prefs by lazy {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "portal_session", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.firstOrNull { it.name == "khorshid_session" }?.let {
                prefs.edit().putString("session_cookie", it.toString()).apply()
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val raw = prefs.getString("session_cookie", null) ?: return emptyList()
            return runCatching { Cookie.parse(url, raw)?.let(::listOf) ?: emptyList() }.getOrDefault(emptyList())
        }
    }
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("Accept", "application/json")
                .header("Origin", base).build())
        }.build()

    suspend fun login(username: String, password: String) = withContext(Dispatchers.IO) {
        val json = JSONObject().put("username", username).put("password", password)
        val request = Request.Builder().url("$base/api/auth/login")
            .post(json.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("Login failed: ${it.code}")
            if (!JSONObject(it.body?.string().orEmpty()).optBoolean("ok")) throw IOException("Login rejected")
        }
        if (!checkSession()) throw IOException("Session verification failed")
    }

    suspend fun checkSession(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$base/api/auth/session").get().build()
        client.newCall(request).execute().use {
            if (!it.isSuccessful) return@withContext false
            JSONObject(it.body?.string().orEmpty()).optBoolean("authenticated")
        }
    }

    suspend fun memberName(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$base/api/workspace/member").get().build()
        client.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("Member request failed")
            JSONObject(it.body?.string().orEmpty()).optJSONObject("member")?.optString("fullName").orEmpty()
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            val body = "{}".toRequestBody("application/json".toMediaType())
            client.newCall(Request.Builder().url("$base/api/auth/logout").post(body).build()).execute().close()
        } finally {
            prefs.edit().remove("session_cookie").apply()
        }
    }
}
