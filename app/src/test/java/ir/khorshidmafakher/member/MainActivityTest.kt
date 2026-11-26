package ir.khorshidmafakher.member

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MainActivityTest {
    @Test
    fun mergeNoticesDeduplicatesByIdAndPrefersSecondarySource() {
        val primary = JSONArray()
            .put(JSONObject().put("id", "notice-1").put("title", "نسخه قدیمی"))
            .put(JSONObject().put("id", "notice-2").put("title", "اطلاعیه دوم"))
        val secondary = JSONArray()
            .put(JSONObject().put("id", "notice-1").put("title", "نسخه جدید"))
            .put(JSONObject().put("id", "notice-3").put("title", "اطلاعیه سوم"))

        val merged = mergeNotices(primary, secondary)

        assertEquals(3, merged.length())
        assertEquals("نسخه جدید", merged.getJSONObject(0).getString("title"))
        assertEquals("اطلاعیه دوم", merged.getJSONObject(1).getString("title"))
        assertEquals("اطلاعیه سوم", merged.getJSONObject(2).getString("title"))
    }

    @Test
    fun mergeNoticesUsesFallbackIdentityWhenIdIsMissing() {
        val primary = JSONArray().put(
            JSONObject().put("title", "اطلاعیه").put("createdAt", "2026-11-01")
        )
        val secondary = JSONArray().put(
            JSONObject().put("title", "اطلاعیه").put("createdAt", "2026-11-01").put("body", "به‌روزرسانی")
        )

        val merged = mergeNotices(primary, secondary)

        assertEquals(1, merged.length())
        assertNotNull(merged.getJSONObject(0).optString("body"))
        assertEquals("به‌روزرسانی", merged.getJSONObject(0).getString("body"))
    }
}
