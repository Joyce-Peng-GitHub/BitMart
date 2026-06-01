package cn.edu.bit.bitmart.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * 集成测试用的内嵌 PostgreSQL（zonky）。运行真实 PG 进程，无需 Docker，
 * 与架构 §12 的 Testcontainers 路线等价（CI 用 Testcontainers + 自定义 zhparser 镜像）。
 *
 * 单实例在整个测试套件内复用，按需 truncate；migrate 使用与生产相同的 Flyway 脚本，
 * 因此 V1 的 zhparser 在 stock 镜像上自动降级为 simple 配置（见迁移脚本注释）。
 */
object EmbeddedPostgresSupport {

    private val postgres: EmbeddedPostgres by lazy {
        EmbeddedPostgres.builder().start()
    }

    private val dataSource: HikariDataSource by lazy {
        val jdbcUrl = postgres.getJdbcUrl("postgres", "postgres")
        val ds = HikariDataSource(HikariConfig().apply {
            setJdbcUrl(jdbcUrl)
            username = "postgres"
            maximumPoolSize = 4
            isAutoCommit = false
        })
        DatabaseFactory.migrate(ds)   // 运行真实迁移脚本
        ds
    }

    private val database: Database by lazy { DatabaseFactory.connect(dataSource) }

    /** 获取（懒初始化的）已迁移数据库句柄。 */
    fun db(): Database = database

    fun rawDataSource(): HikariDataSource = dataSource
}
