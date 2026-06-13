package cn.edu.bit.bitmart.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class TimeFormatsTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun `UTC timestamp converts to local zone with minute precision`() {
        // 用户报告的实际格式：UTC + 微秒。+8 时区下应跨到次日。
        assertEquals(
            "2026-06-12 02:40",
            formatTimestampMinute("2026-06-11T18:40:20.511027Z", shanghai),
        )
    }

    @Test
    fun `offset timestamp converts correctly`() {
        assertEquals(
            "2026-06-11 18:40",
            formatTimestampMinute("2026-06-11T18:40:20+08:00", shanghai),
        )
        // 不同偏移 → 同一时刻换算。
        assertEquals(
            "2026-06-12 02:40",
            formatTimestampMinute("2026-06-11T14:40:00-04:00", shanghai),
        )
    }

    @Test
    fun `timestamp without fraction parses`() {
        assertEquals(
            "2026-06-12 02:40",
            formatTimestampMinute("2026-06-11T18:40:20Z", shanghai),
        )
    }

    @Test
    fun `unparseable input falls back to raw string`() {
        assertEquals("不是时间", formatTimestampMinute("不是时间", shanghai))
        assertEquals("", formatTimestampMinute("", shanghai))
    }

    // 固定 now = 2026-06-12T00:00:00Z，窗口 24h，确保确定性。
    private val now = java.time.OffsetDateTime.parse("2026-06-12T00:00:00Z").toInstant().toEpochMilli()

    @Test
    fun `expiryStatusOf far future is NORMAL`() {
        assertEquals(
            ExpiryStatus.NORMAL,
            expiryStatusOf("2026-06-20T00:00:00Z", nowEpochMs = now, warnWindowHours = 24),
        )
    }

    @Test
    fun `expiryStatusOf within window is NEAR_EXPIRY`() {
        // 距 now 12 小时 → 落在 24h 窗口内。
        assertEquals(
            ExpiryStatus.NEAR_EXPIRY,
            expiryStatusOf("2026-06-12T12:00:00Z", nowEpochMs = now, warnWindowHours = 24),
        )
        // 恰好 24h 边界仍算临近。
        assertEquals(
            ExpiryStatus.NEAR_EXPIRY,
            expiryStatusOf("2026-06-13T00:00:00Z", nowEpochMs = now, warnWindowHours = 24),
        )
    }

    @Test
    fun `expiryStatusOf past or now is EXPIRED`() {
        assertEquals(
            ExpiryStatus.EXPIRED,
            expiryStatusOf("2026-06-11T23:59:00Z", nowEpochMs = now, warnWindowHours = 24),
        )
        // 恰好等于 now 视为已过期。
        assertEquals(
            ExpiryStatus.EXPIRED,
            expiryStatusOf("2026-06-12T00:00:00Z", nowEpochMs = now, warnWindowHours = 24),
        )
    }

    @Test
    fun `expiryStatusOf unparseable is NORMAL`() {
        assertEquals(ExpiryStatus.NORMAL, expiryStatusOf("不是时间", nowEpochMs = now, warnWindowHours = 24))
    }
}
