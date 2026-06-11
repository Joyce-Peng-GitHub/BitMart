package cn.edu.bit.bitmart.core.ui

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

/**
 * 服务端 ISO-8601 时间戳（如 `2026-06-11T18:40:20.511027Z`，也可能带 `+08:00` 偏移）
 * → 本地时区、精确到分钟的可读文本（如 `2026-06-12 02:40`）。
 * 解析失败时原样返回，宁可显示原始串也不崩溃。
 * @param zone 目标时区，默认系统时区；注入以便测试。
 */
fun formatTimestampMinute(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching {
        OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(MINUTE_FORMATTER)
    }.getOrDefault(iso)
