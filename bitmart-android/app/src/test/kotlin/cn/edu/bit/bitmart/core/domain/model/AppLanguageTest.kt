package cn.edu.bit.bitmart.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun explicit_zh_and_en() {
        assertEquals("zh", AppLanguage.ZH.resolveLanguageTag("en-US"))
        assertEquals("en", AppLanguage.EN.resolveLanguageTag("zh-CN"))
    }

    @Test fun system_follows_device_chinese_to_zh() {
        assertEquals("zh", AppLanguage.SYSTEM.resolveLanguageTag("zh-CN"))
        assertEquals("zh", AppLanguage.SYSTEM.resolveLanguageTag("zh-Hans-CN"))
    }

    @Test fun system_falls_back_to_english_for_non_chinese() {
        assertEquals("en", AppLanguage.SYSTEM.resolveLanguageTag("en-US"))
        assertEquals("en", AppLanguage.SYSTEM.resolveLanguageTag("fr-FR"))
        assertEquals("en", AppLanguage.SYSTEM.resolveLanguageTag(""))
    }
}
