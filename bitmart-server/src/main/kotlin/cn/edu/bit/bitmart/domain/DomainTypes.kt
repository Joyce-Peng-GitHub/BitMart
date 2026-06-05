package cn.edu.bit.bitmart.domain

import kotlinx.serialization.Serializable

/** 列表类型：卖品与求购共用一张表，以此区分（架构 §4.2）。 */
enum class ListingType { SELL, BUY }

/** 列表品类：通用商品或书籍（书籍含 ISBN 等专属字段）。 */
enum class ListingCategory { GENERAL, BOOK }

/** 来源：用户发布或 NapCat 机器人导入（改进项预留，架构 §14）。 */
enum class ListingSource { USER, NAPCAT_BOT }

/** 联系方式渠道提示（仅 UI 层使用，API 不强制枚举）。 */
enum class ContactChannel { WECHAT, QQ, PHONE, EMAIL, OTHER }

/** 单条联系方式。channel 可选（未指定时为空字符串），API 不强制种类。 */
@Serializable
data class Contact(val channel: String = "", val value: String)
