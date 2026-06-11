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
}
