package cn.edu.bit.llmimageanalysisdemo.model

import kotlinx.serialization.Serializable

@Serializable
data class BookInfo(
    val title: String = "",
    val author: String = "",
    val publisher: String = "",
    val edition: String = ""
)

@Serializable
data class AnalysisResult(
    val books: List<BookInfo> = emptyList()
)
