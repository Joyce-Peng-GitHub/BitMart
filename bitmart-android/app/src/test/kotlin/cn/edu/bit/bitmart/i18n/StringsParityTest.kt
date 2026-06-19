package cn.edu.bit.bitmart.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringsParityTest {
    /**
     * Resolves a strings.xml path that is given relative to the module root
     * (`bitmart-android/app`). Gradle runs `:app` unit tests with the working
     * directory set to the module dir, so the relative path resolves directly.
     * If it does not (different runner / IDE), we retry against `user.dir` and,
     * failing that, surface the resolved absolute path in the error message so
     * the failure is diagnosable rather than a bare FileNotFoundException.
     */
    private fun resolve(relPath: String): File {
        val direct = File(relPath)
        if (direct.exists()) return direct
        val fromUserDir = File(System.getProperty("user.dir"), relPath)
        if (fromUserDir.exists()) return fromUserDir
        throw AssertionError(
            "Could not locate strings file '$relPath'. Tried:\n" +
                "  - ${direct.absolutePath}\n" +
                "  - ${fromUserDir.absolutePath}\n" +
                "(working dir = ${System.getProperty("user.dir")})"
        )
    }

    private fun names(path: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resolve(path))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it).attributes.getNamedItem("name").nodeValue }
            .toSet()
    }

    /** key → 原始文本值（含格式占位符），用于占位符一致性校验。 */
    private fun values(path: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resolve(path))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val node = nodes.item(it)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    /** 提取有序的位置化格式占位符（如 %1$s、%2$d）。`%%` 等非位置化序列不在此列。 */
    private fun formatTokens(value: String): List<String> =
        FORMAT_TOKEN.findAll(value).map { it.value }.toList()

    @Test
    fun default_and_zh_have_identical_keys() {
        val en = names("src/main/res/values/strings.xml")
        val zh = names("src/main/res/values-zh/strings.xml")
        assertEquals("缺失/多余的 key（仅默认 values 有）", emptySet<String>(), en - zh)
        assertEquals("缺失/多余的 key（仅 values-zh 有）", emptySet<String>(), zh - en)
    }

    /**
     * 每个 key 的中英文必须含有完全一致（顺序与类型都相同）的位置化格式占位符。
     * 否则 String.format 在运行时会抛 IllegalFormatConversionException 或参数错位。
     */
    @Test
    fun default_and_zh_have_identical_format_tokens() {
        val en = values("src/main/res/values/strings.xml")
        val zh = values("src/main/res/values-zh/strings.xml")
        // key 集合由 default_and_zh_have_identical_keys 保证一致；此处仅校验交集（稳健起见）。
        val mismatches = (en.keys intersect zh.keys)
            .mapNotNull { key ->
                val enTokens = formatTokens(en.getValue(key))
                val zhTokens = formatTokens(zh.getValue(key))
                if (enTokens != zhTokens) "  $key: en=$enTokens vs zh=$zhTokens" else null
            }
        assertEquals(
            "中英文格式占位符不一致（会导致运行时 IllegalFormatConversionException）：\n" +
                mismatches.joinToString("\n"),
            emptyList<String>(),
            mismatches,
        )
    }

    private companion object {
        /** 位置化格式占位符：%N$<conversion>，如 %1$s、%2$d。 */
        val FORMAT_TOKEN = Regex("""%\d+\$[a-zA-Z]""")
    }
}
