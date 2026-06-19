package cn.edu.bit.bitmart.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {
    @Test fun zh_tag_selects_chinese() {
        assertEquals(DEFAULT_BOOK_PROMPT_ZH, defaultBookPrompt("zh-CN"))
        assertEquals(DEFAULT_GENERAL_PROMPT_ZH, defaultGeneralPrompt("zh"))
    }

    @Test fun non_zh_tag_selects_english() {
        assertEquals(DEFAULT_BOOK_PROMPT_EN, defaultBookPrompt("en-US"))
        assertEquals(DEFAULT_GENERAL_PROMPT_EN, defaultGeneralPrompt("fr"))
    }

    @Test fun english_prompt_is_actually_english() {
        assertTrue(DEFAULT_BOOK_PROMPT_EN.contains("JSON"))
        assertTrue(DEFAULT_BOOK_PROMPT_EN.none { it.code in 0x4E00..0x9FFF }) // 无中日韩统一表意文字
        assertTrue(DEFAULT_GENERAL_PROMPT_EN.contains("JSON"))
        assertTrue(DEFAULT_GENERAL_PROMPT_EN.none { it.code in 0x4E00..0x9FFF })
    }
}
