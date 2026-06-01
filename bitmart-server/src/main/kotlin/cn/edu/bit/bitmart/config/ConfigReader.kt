package cn.edu.bit.bitmart.config

import io.ktor.server.config.ApplicationConfig

/** 配置读取辅助：带类型转换与校验，缺失或非法时抛出清晰错误。 */
internal object ConfigReader {

    fun ApplicationConfig.string(key: String): String =
        propertyOrNull(key)?.getString()
            ?: throw ConfigException("缺少配置项: $key")

    fun ApplicationConfig.int(key: String): Int {
        val raw = string(key)
        return raw.toIntOrNull() ?: throw ConfigException("配置项 $key 不是整数: $raw")
    }

    fun ApplicationConfig.long(key: String): Long {
        val raw = string(key)
        return raw.toLongOrNull() ?: throw ConfigException("配置项 $key 不是整数: $raw")
    }

    fun ApplicationConfig.stringList(key: String): List<String> =
        propertyOrNull(key)?.getList()
            ?: throw ConfigException("缺少配置项(列表): $key")

    /** 校验断言：条件不成立时抛出配置异常。 */
    fun require(condition: Boolean, message: () -> String) {
        if (!condition) throw ConfigException(message())
    }
}

/** 配置加载/校验失败异常。 */
class ConfigException(message: String) : RuntimeException(message)
