package cn.edu.bit.bitmart.domain

/**
 * 标签归一化：去首尾空白、转小写、压缩内部连续空白为单个空格（架构 §4.2 tag.name 归一化）。
 * 归一化后用于去重与唯一约束，避免 "Java"、"java "、" JAVA" 被视作不同标签。
 */
object TagNormalizer {

    private val whitespace = Regex("\\s+")

    fun normalize(raw: String): String =
        raw.trim().replace(whitespace, " ").lowercase()

    /** 归一化一组标签并去重，保持首次出现顺序；丢弃归一化后为空的项。 */
    fun normalizeDistinct(raw: List<String>): List<String> =
        raw.map(::normalize).filter { it.isNotEmpty() }.distinct()
}
