package cn.edu.bit.bitmart.llm

/**
 * 默认识别提示词（system 角色）。用户可在「LLM 设置」中覆盖。
 *
 * 两套模板分别对应「书籍 / 一般商品」（架构 §5.4）：
 * - 书籍：识别书名/作者/出版社/版本/ISBN，对应批量扫描书籍的编辑界面字段。
 * - 一般商品：从商品照片识别标题/描述/建议价/标签。
 *
 * 提示词约束模型仅输出纯 JSON，配合 response_format=json_schema 强约束结构；
 * 即使服务端不支持 json_schema，提示词亦要求不要包裹 Markdown 代码块，
 * 由客户端 [OpenAiCompatibleLlmClient] 兜底剥离 ``` 围栏。
 */
const val DEFAULT_BOOK_PROMPT: String =
    "你是一个图书识别助手。用户会发送书本封面、书脊或版权页的照片，请识别图中这本书的信息。" +
        "尽可能识别书名(title)、作者(author)、出版社(publisher)、版本(edition)、国际标准书号(isbn)。" +
        "如果某项信息无法从图片中识别，请将其设为空字符串。\n" +
        "你必须且只能输出一个纯 JSON 对象，不要输出任何其他内容，不要使用 Markdown 代码块包裹。"

const val DEFAULT_GENERAL_PROMPT: String =
    "你是一个二手商品信息助手。用户会发送一张商品照片，请根据照片识别并生成挂牌信息。" +
        "请给出简洁的商品标题(title)、一段商品描述(description)、一个建议价格(suggestedPrice，人民币元，纯数字字符串，无法判断则为空字符串)、" +
        "以及若干个有助于检索的标签(tags，字符串数组，可为空数组)。\n" +
        "你必须且只能输出一个纯 JSON 对象，不要输出任何其他内容，不要使用 Markdown 代码块包裹。"
