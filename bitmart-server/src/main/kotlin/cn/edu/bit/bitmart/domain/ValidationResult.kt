package cn.edu.bit.bitmart.domain

/**
 * 校验结果：累积所有错误而非遇错即停，便于批量发布一次性返回全部校验错误（架构 §6.3）。
 * 每个错误带字段名与稳定的错误码标识，方便前端逐条定位与本地化。
 */
data class ValidationResult(val errors: List<ValidationError>) {

    val isValid: Boolean get() = errors.isEmpty()

    companion object {
        val VALID = ValidationResult(emptyList())

        /** 收集多个子结果的错误，合并为一个。 */
        fun merge(vararg results: ValidationResult): ValidationResult =
            ValidationResult(results.flatMap { it.errors })
    }
}

data class ValidationError(val field: String, val code: String, val message: String, val params: Map<String, String> = emptyMap())

/** 累积器：在校验逻辑中逐条追加错误。 */
class ValidationErrors {
    private val errors = mutableListOf<ValidationError>()

    fun add(field: String, code: String, message: String, params: Map<String, String> = emptyMap()) {
        errors += ValidationError(field, code, message, params)
    }

    /** 条件不成立时记录一条错误。 */
    fun check(condition: Boolean, field: String, code: String, message: String, params: Map<String, String> = emptyMap()) {
        if (!condition) add(field, code, message, params)
    }

    fun build(): ValidationResult = ValidationResult(errors.toList())
}
