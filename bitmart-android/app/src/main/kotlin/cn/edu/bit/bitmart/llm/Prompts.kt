package cn.edu.bit.bitmart.llm

/**
 * 默认识别提示词（system 角色）。用户可在「LLM 设置」中覆盖。
 *
 * 两套模板分别对应「书籍 / 一般商品」（架构 §5.4），均支持一图多项：
 * - 书籍：识别图中所有书的书名/作者/出版社/版本/ISBN，以及图中可见的标价(originalPrice)。
 * - 一般商品：从照片识别图中所有商品的标题/描述/标签，以及图中可见的标价(originalPrice)。
 *
 * 输出结构：`{"items":[ ... ]}`，每个元素为一项；图中没有可识别对象则 items 为空数组。
 * 价格规则：originalPrice 仅在图中能直接看到价格（吊牌、标价签、封底定价）时填写，
 * 否则一律留空，切勿臆测；售价/期望价不由模型产生，交由用户手填。
 *
 * 提示词约束模型仅输出纯 JSON，配合 response_format=json_schema 强约束结构；
 * 即使服务端不支持 json_schema，提示词亦要求不要包裹 Markdown 代码块，
 * 由客户端 [OpenAiCompatibleLlmClient] 兜底剥离 ``` 围栏。
 *
 * 每套模板各有中文(ZH)/英文(EN)两个版本，提示词语言决定模型输出语言。
 * 通过 [defaultBookPrompt] / [defaultGeneralPrompt] 按语言标签选择：
 * 标签以 "zh" 开头选中文，否则选英文（与应用「非 zh → 英文」规则一致）。
 */
const val DEFAULT_BOOK_PROMPT_ZH: String =
    "你是一个图书识别助手。用户会发送一张照片，图中可能有不止一本书，请识别图中每一本书的信息。" +
        "对每本书尽可能识别书名(title)、作者(author)、出版社(publisher)、版本(edition)、国际标准书号(isbn)；" +
        "若图中能直接看到该书定价（如封底标价），填入原价(originalPrice，人民币元，纯数字字符串），" +
        "看不到价格则将 originalPrice 设为空字符串，不要臆测，也不要给出售价；其它无法识别的项同样设为空字符串。\n" +
        "你必须且只能输出一个纯 JSON 对象，格式为 {\"items\":[{每本书一个元素}]}，图中没有书则 items 为空数组；" +
        "不要输出任何其他内容，不要使用 Markdown 代码块包裹。"

const val DEFAULT_BOOK_PROMPT_EN: String =
    "You are a book recognition assistant. The user sends one photo that may contain multiple books; " +
        "identify each book's title, author, publisher, edition, and ISBN. " +
        "If a price is directly visible on the book (e.g. back-cover list price), fill originalPrice " +
        "(CNY, digits-only string); if no price is visible, set originalPrice to an empty string—do not guess, " +
        "and never output a selling price; set any other unrecognizable field to an empty string.\n" +
        "You must output exactly one pure JSON object of the form {\"items\":[{one element per book}]}, " +
        "an empty array if there are no books; output nothing else and do not wrap it in a Markdown code block."

const val DEFAULT_GENERAL_PROMPT_ZH: String =
    "你是一个二手商品信息助手。用户会发送一张照片，图中可能有不止一件商品，请识别图中每一件商品并分别生成挂牌信息。" +
        "对每件商品给出简洁的标题(title)、一段描述(description)、以及若干有助于检索的标签(tags，字符串数组，可为空数组)；" +
        "若图中能直接看到该商品价格（吊牌、标价签等），填入原价(originalPrice，人民币元，纯数字字符串），" +
        "看不到价格则将 originalPrice 设为空字符串，切勿臆测，也不要生成售价。\n" +
        "你必须且只能输出一个纯 JSON 对象，格式为 {\"items\":[{每件商品一个元素}]}，图中没有商品则 items 为空数组；" +
        "不要输出任何其他内容，不要使用 Markdown 代码块包裹。"

const val DEFAULT_GENERAL_PROMPT_EN: String =
    "You are a second-hand goods listing assistant. The user sends one photo that may contain multiple items; " +
        "for each item produce a concise title, a short description, and a few search-friendly tags (string array, may be empty). " +
        "If a price is directly visible (price tag, label), fill originalPrice (CNY, digits-only string); " +
        "if no price is visible, set originalPrice to an empty string—do not guess, and do not produce a selling price.\n" +
        "You must output exactly one pure JSON object of the form {\"items\":[{one element per item}]}, " +
        "an empty array if there are no items; output nothing else and do not wrap it in a Markdown code block."

// 向后兼容别名（现有代码引用，默认中文）。
const val DEFAULT_BOOK_PROMPT: String = DEFAULT_BOOK_PROMPT_ZH
const val DEFAULT_GENERAL_PROMPT: String = DEFAULT_GENERAL_PROMPT_ZH

/** 按语言标签选择书籍默认提示词：标签以 "zh" 开头→中文，否则→英文。 */
fun defaultBookPrompt(tag: String): String =
    if (tag.startsWith("zh")) DEFAULT_BOOK_PROMPT_ZH else DEFAULT_BOOK_PROMPT_EN

/** 按语言标签选择一般商品默认提示词：标签以 "zh" 开头→中文，否则→英文。 */
fun defaultGeneralPrompt(tag: String): String =
    if (tag.startsWith("zh")) DEFAULT_GENERAL_PROMPT_ZH else DEFAULT_GENERAL_PROMPT_EN
