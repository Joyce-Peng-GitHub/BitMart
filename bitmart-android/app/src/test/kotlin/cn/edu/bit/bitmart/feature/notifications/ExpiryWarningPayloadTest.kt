package cn.edu.bit.bitmart.feature.notifications

import cn.edu.bit.bitmart.core.domain.model.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 校验 [expiryWarningPayload] 的「解析→本地化 / 回落服务端文案」决策：
 * - category==1 且 payload 合法且 templateKey==EXPIRY_WARNING → 返回解析结果（UI 走本地化渲染）。
 * - 其余情况（公告、缺 payload、非法 JSON、未知模板、缺字段）→ 返回 null（UI 回落存储的 title/body）。
 */
class ExpiryWarningPayloadTest {

    private fun notif(category: Int, payload: String?) = Notification(
        id = 1, category = category, title = "中文标题", body = "中文正文",
        payload = payload, read = false, createdAt = "2026-06-02T00:00:00Z",
        isAnnouncement = category == 0,
    )

    private val validPayload =
        """{"listingId":42,"expiresAt":"2026-06-20T10:00:00+08:00","templateKey":"EXPIRY_WARNING","listingTitle":"高数教材","hours":24,"listingType":"SELL"}"""

    @Test
    fun `valid expiry payload parses to localized model`() {
        val p = notif(category = 1, payload = validPayload).expiryWarningPayload()
        assertEquals(42L, p?.listingId)
        assertEquals("EXPIRY_WARNING", p?.templateKey)
        assertEquals("高数教材", p?.listingTitle)
        assertEquals(24, p?.hours)
        assertEquals("SELL", p?.listingType)
    }

    @Test
    fun `tolerates unknown extra fields`() {
        val withExtra =
            """{"listingId":7,"expiresAt":"2026-06-20T10:00:00+08:00","templateKey":"EXPIRY_WARNING","listingTitle":"x","hours":48,"listingType":"BUY","newField":"ignored"}"""
        val p = notif(category = 1, payload = withExtra).expiryWarningPayload()
        assertEquals(48, p?.hours)
        assertEquals("BUY", p?.listingType)
    }

    @Test
    fun `announcement category falls back even with parseable payload`() {
        assertNull(notif(category = 0, payload = validPayload).expiryWarningPayload())
    }

    @Test
    fun `null payload falls back`() {
        assertNull(notif(category = 1, payload = null).expiryWarningPayload())
    }

    @Test
    fun `garbage payload falls back`() {
        assertNull(notif(category = 1, payload = "not json {{{").expiryWarningPayload())
    }

    @Test
    fun `missing required field falls back`() {
        // 缺 hours 字段，解析应失败并回落。
        val missing =
            """{"listingId":1,"expiresAt":"2026-06-20T10:00:00+08:00","templateKey":"EXPIRY_WARNING","listingTitle":"x","listingType":"SELL"}"""
        assertNull(notif(category = 1, payload = missing).expiryWarningPayload())
    }

    @Test
    fun `unknown template key falls back`() {
        val other =
            """{"listingId":1,"expiresAt":"2026-06-20T10:00:00+08:00","templateKey":"SOMETHING_ELSE","listingTitle":"x","hours":24,"listingType":"SELL"}"""
        assertNull(notif(category = 1, payload = other).expiryWarningPayload())
    }

    @Test
    fun `unknown listing type falls back`() {
        val badType =
            """{"listingId":1,"expiresAt":"2026-06-20T10:00:00+08:00","templateKey":"EXPIRY_WARNING","listingTitle":"x","hours":24,"listingType":"RENT"}"""
        assertNull(notif(category = 1, payload = badType).expiryWarningPayload())
    }
}
