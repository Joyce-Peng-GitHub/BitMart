package cn.edu.bit.bitmart.domain

import cn.edu.bit.bitmart.config.PasswordPolicyConfig

/**
 * BitMart 账号密码策略校验（注册/重置时使用）。
 * 规则：最小长度，且至少满足 N 类字符（小写/大写/数字/符号），N 来自配置。
 */
class PasswordPolicy(private val config: PasswordPolicyConfig) {

    fun validate(password: String): ValidationResult {
        val errors = ValidationErrors()
        errors.check(
            password.length >= config.minLength,
            field = "password",
            code = "PASSWORD_TOO_SHORT",
            message = "密码长度至少 ${config.minLength} 位",
            params = mapOf("minLength" to config.minLength.toString()),
        )
        errors.check(
            charClassCount(password) >= config.minCharClasses,
            field = "password",
            code = "PASSWORD_TOO_SIMPLE",
            message = "密码须包含至少 ${config.minCharClasses} 类字符（小写、大写、数字、符号）",
            params = mapOf("minCharClasses" to config.minCharClasses.toString()),
        )
        return errors.build()
    }

    /** 统计密码覆盖的字符类别数：小写、大写、数字、其他（符号）。 */
    private fun charClassCount(password: String): Int {
        var hasLower = false
        var hasUpper = false
        var hasDigit = false
        var hasSymbol = false
        for (ch in password) {
            when {
                ch.isLowerCase() -> hasLower = true
                ch.isUpperCase() -> hasUpper = true
                ch.isDigit() -> hasDigit = true
                else -> hasSymbol = true
            }
        }
        return listOf(hasLower, hasUpper, hasDigit, hasSymbol).count { it }
    }
}
