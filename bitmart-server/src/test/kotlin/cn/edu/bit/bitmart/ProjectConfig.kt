package cn.edu.bit.bitmart

import cn.edu.bit.bitmart.db.EmbeddedPostgresSupport
import io.kotest.core.config.AbstractProjectConfig

/**
 * Kotest 项目级配置。在任何 spec 运行前预热内嵌 PostgreSQL，
 * 避免首个 testApplication 用例在协程超时预算内承担数据库冷启动（约数十秒），
 * 从而消除偶发的 UncompletedCoroutinesError 超时。
 */
@Suppress("unused")
class ProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        // 触发懒初始化：启动嵌入式 PG 并跑迁移。
        EmbeddedPostgresSupport.db()
    }
}
