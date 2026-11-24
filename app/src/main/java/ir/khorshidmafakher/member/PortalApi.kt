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
import org.json.JSONArray
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
            val cookie = runCatching { Cookie.parse(url, raw) }.getOrNull() ?: return emptyList()
            if (cookie.expiresAt <= System.currentTimeMillis()) {
                prefs.edit().remove("session_cookie").apply()
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
            prefs.edit().remove("session_cookie").apply()
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
