package cn.edu.bit.bitmart.db

import cn.edu.bit.bitmart.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

/**
 * 数据库装配：HikariCP 连接池 → Flyway 迁移 → Exposed 连接。
 * 表结构由 Flyway SQL 迁移创建（单一事实来源），Exposed 仅用于查询。
 */
object DatabaseFactory {

    /** 构建连接池数据源。 */
    fun dataSource(config: DatabaseConfig): HikariDataSource {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = config.maxPoolSize
            driverClassName = "org.postgresql.Driver"
            isAutoCommit = false                 // 由 Exposed 事务控制提交
        }
        return HikariDataSource(hikari)
    }

    /** 运行 Flyway 迁移到最新版本。 */
    fun migrate(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    /** 将数据源注册到 Exposed，返回 Database 句柄。 */
    fun connect(dataSource: DataSource): Database =
        Database.connect(dataSource)

    /** 一站式：建池 → 迁移 → 连接。 */
    fun init(config: DatabaseConfig): Pair<HikariDataSource, Database> {
        val ds = dataSource(config)
        migrate(ds)
        val db = connect(ds)
        return ds to db
    }
}
