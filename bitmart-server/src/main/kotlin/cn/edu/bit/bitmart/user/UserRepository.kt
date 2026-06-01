package cn.edu.bit.bitmart.user

import cn.edu.bit.bitmart.db.Users
import cn.edu.bit.bitmart.domain.User
import cn.edu.bit.bitmart.domain.UserRole
import cn.edu.bit.bitmart.domain.UserStatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

/**
 * 用户仓储。所有方法须在 Exposed `transaction { }` 内调用（由 Service 层开启事务）。
 * 查询默认排除已注销（deleted_at 非空）的用户。
 */
class UserRepository {

    /** 新建用户，返回生成的 id。密码哈希由调用方（Service）预先计算。 */
    fun create(studentId: String, passwordHash: String, nickname: String?): Long =
        Users.insertAndGetId {
            it[Users.studentId] = studentId
            it[Users.passwordHash] = passwordHash
            it[Users.nickname] = nickname
            it[role] = UserRole.NORMAL.code
            it[status] = UserStatus.ACTIVE.code
            it[createdAt] = OffsetDateTime.now()
        }.value

    /** 按学号查找未注销用户。 */
    fun findByStudentId(studentId: String): User? =
        Users.selectAll()
            .where { (Users.studentId eq studentId) and Users.deletedAt.isNull() }
            .singleOrNull()
            ?.toUser()

    /** 按 id 查找未注销用户。 */
    fun findById(id: Long): User? =
        Users.selectAll()
            .where { (Users.id eq id) and Users.deletedAt.isNull() }
            .singleOrNull()
            ?.toUser()

    /** 读取密码哈希（仅鉴权用，不进入领域模型）。 */
    fun findPasswordHash(studentId: String): String? =
        Users.selectAll()
            .where { (Users.studentId eq studentId) and Users.deletedAt.isNull() }
            .singleOrNull()
            ?.get(Users.passwordHash)

    /** 更新密码哈希，返回受影响行数。 */
    fun updatePasswordHash(studentId: String, newHash: String): Int =
        Users.update({ (Users.studentId eq studentId) and Users.deletedAt.isNull() }) {
            it[passwordHash] = newHash
        }

    /** 更新昵称。 */
    fun updateNickname(userId: Long, nickname: String?): Int =
        Users.update({ (Users.id eq userId) and Users.deletedAt.isNull() }) {
            it[Users.nickname] = nickname
        }

    /** 软删除用户（注销），返回受影响行数。 */
    fun softDelete(userId: Long): Int =
        Users.update({ (Users.id eq userId) and Users.deletedAt.isNull() }) {
            it[deletedAt] = OffsetDateTime.now()
        }

    private fun ResultRow.toUser(): User = User(
        id = this[Users.id].value,
        studentId = this[Users.studentId],
        nickname = this[Users.nickname],
        role = UserRole.fromCode(this[Users.role]),
        status = UserStatus.fromCode(this[Users.status]),
        createdAt = this[Users.createdAt],
        deletedAt = this[Users.deletedAt],
    )
}
