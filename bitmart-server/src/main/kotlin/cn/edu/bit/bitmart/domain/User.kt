package cn.edu.bit.bitmart.domain

import java.time.OffsetDateTime

/** 用户领域模型（不含密码哈希等敏感字段，敏感字段仅在仓储层处理）。 */
data class User(
    val id: Long,
    val studentId: String,
    val nickname: String?,
    val role: UserRole,
    val status: UserStatus,
    val createdAt: OffsetDateTime,
    val deletedAt: OffsetDateTime?,
) {
    /** 展示用昵称：原始昵称，未设置/空白时为 null。本地化「匿名/Anonymous」由客户端兜底渲染。 */
    val displayName: String? get() = nickname?.takeIf { it.isNotBlank() }
}

enum class UserRole(val code: Int) {
    NORMAL(0),
    ADMIN(1);

    companion object {
        fun fromCode(code: Int): UserRole = entries.firstOrNull { it.code == code } ?: NORMAL
    }
}

enum class UserStatus(val code: Int) {
    ACTIVE(0),
    BANNED(1);

    companion object {
        fun fromCode(code: Int): UserStatus = entries.firstOrNull { it.code == code } ?: ACTIVE
    }
}
