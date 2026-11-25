package ir.khorshidmafakher.member

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private class SessionCookieVault(context: Context) {
    private val prefs = context.getSharedPreferences("portal_session_vault", Context.MODE_PRIVATE)
    private val alias = "khorshid_portal_session_aes"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    fun save(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        prefs.edit().putString("cookie", Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun read(): String? {
        val encoded = prefs.getString("cookie", null) ?: return null
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            require(packed.size > 12)
            val iv = packed.copyOfRange(0, 12)
            val ciphertext = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun clear() { prefs.edit().remove("cookie").apply() }
}

class PortalApi(context: Context) {
    private val base = "https://portal.khorshid-mafakher.ir"
    private val vault = SessionCookieVault(context.applicationContext)
    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.firstOrNull { it.name == "khorshid_session" }?.let { vault.save(it.toString()) }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val raw = vault.read() ?: return emptyList()
            val cookie = runCatching { Cookie.parse(url, raw) }.getOrNull() ?: return emptyList()
            if (cookie.expiresAt <= System.currentTimeMillis()) {
                vault.clear()
                return emptyList()
            }
            return listOf(cookie)
        }
    }
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("Accept", "application/json")
                .header("Origin", base)
                .header("X-Requested-With", "XMLHttpRequest")
                .build())
        }.build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun execute(request: Request): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val payload = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!response.isSuccessful || payload.optBoolean("ok") == false) {
                val code = payload.optString("error").ifBlank { "HTTP_${response.code}" }
                throw PortalException(response.code, code, friendlyError(code))
            }
            payload
        }
    }

    suspend fun login(username: String, password: String) {
        val body = JSONObject().put("username", username.trim()).put("password", password)
        execute(Request.Builder().url("$base/api/auth/login")
            .post(body.toString().toRequestBody(jsonType)).build())
        if (!checkSession()) throw PortalException(401, "SESSION_INVALID", "نشست ورود تأیید نشد.")
    }

    suspend fun checkSession(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$base/api/auth/session").get().build()
        runCatching {
            client.newCall(request).execute().use {
                if (!it.isSuccessful) return@use false
                JSONObject(it.body?.string().orEmpty()).optBoolean("authenticated")
            }
        }.getOrDefault(false)
    }

    suspend fun dashboard() = execute(Request.Builder().url("$base/api/workspace/member").get().build())
    suspend fun publicNotices(): JSONArray =
        execute(Request.Builder().url("$base/api/public/notices").get().build()).optJSONArray("notices") ?: JSONArray()
    suspend fun conversations(): JSONArray =
        execute(Request.Builder().url("$base/api/workspace/member?summary=conversations").get().build())
            .optJSONArray("conversations") ?: JSONArray()
    suspend fun projects(): JSONArray =
        execute(Request.Builder().url("$base/api/projects").get().build()).optJSONArray("projects") ?: JSONArray()

    suspend fun sendMessage(recipientRole: String, body: String) = post(
        "$base/api/workspace/member",
        JSONObject().put("action", "SEND_DEPARTMENT_MESSAGE")
            .put("recipientRole", recipientRole).put("body", body)
    )

    suspend fun reply(conversationId: String, body: String) = post(
        "$base/api/workspace/member",
        JSONObject().put("action", "REPLY_MEMBER_CONVERSATION")
            .put("conversationId", conversationId).put("body", body)
    )

    suspend fun markNotificationRead(id: String) = post(
        "$base/api/workspace/member",
        JSONObject().put("action", "MARK_NOTIFICATION_READ").put("id", id)
    )

    suspend fun submitProposal(
        title: String, objective: String, description: String, impact: String,
        audience: String, location: String, timeline: String, budget: String
    ) = post("$base/api/projects", JSONObject()
        .put("action", "SUBMIT_PROPOSAL")
        .put("title", title).put("objective", objective)
        .put("description", description).put("impact", impact)
        .put("audience", audience).put("location", location)
        .put("timeline", timeline).put("budget", budget)
    )

    private suspend fun post(url: String, payload: JSONObject) =
        execute(Request.Builder().url(url).post(payload.toString().toRequestBody(jsonType)).build())

    suspend fun logout() {
        try {
            val body = "{}".toRequestBody(jsonType)
            client.newCall(Request.Builder().url("$base/api/auth/logout").post(body).build())
                .execute().close()
        } finally {
            vault.clear()
        }
    }

    private fun friendlyError(code: String): String = when (code) {
        "UNAUTHENTICATED", "SESSION_INVALID" -> "نشست شما پایان یافته است. دوباره وارد شوید."
        "FORBIDDEN" -> "دسترسی به این بخش مجاز نیست."
        "RECIPIENT_UNAVAILABLE" -> "در حال حاضر مسئول این واحد در سامانه فعال نیست."
        "CONVERSATION_CLOSED" -> "این مکاتبه بسته شده و امکان پاسخ ندارد."
        "VALIDATION_FAILED", "INVALID_REQUEST" -> "اطلاعات واردشده معتبر نیست."
        "PROJECTS_UNAVAILABLE", "WORKSPACE_UNAVAILABLE" -> "سامانه موقتاً پاسخ‌گو نیست. کمی بعد تلاش کنید."
        else -> "ارتباط با پورتال ناموفق بود. کد خطا: $code"
    }
}

class PortalException(val status: Int, val code: String, message: String) : IOException(message)
