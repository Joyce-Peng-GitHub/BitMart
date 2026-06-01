package cn.edu.bit.bitmart.domain

import cn.edu.bit.bitmart.config.PasswordPolicyConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class PasswordPolicyTest : FunSpec({

    val policy = PasswordPolicy(PasswordPolicyConfig(minLength = 8, minCharClasses = 2))
    fun codes(p: String) = policy.validate(p).errors.map { it.code }

    test("满足长度与字符类别要求时通过") {
        policy.validate("Secret12").isValid.shouldBeTrue()
    }

    test("过短被拒绝") {
        codes("Ab1") shouldContain "PASSWORD_TOO_SHORT"
    }

    test("长度够但只有一类字符被拒绝") {
        codes("aaaaaaaa") shouldContain "PASSWORD_TOO_SIMPLE"
    }

    test("恰好两类字符（小写+数字）通过") {
        policy.validate("abcd1234").isValid.shouldBeTrue()
    }

    test("符号也计为一类") {
        policy.validate("abcd!!!!").isValid.shouldBeTrue()
    }

    test("过短且过于简单时累积两条错误") {
        val r = policy.validate("aaa")
        r.isValid.shouldBeFalse()
        r.errors.size shouldBe 2
    }

    test("更严格策略：要求 3 类字符") {
        val strict = PasswordPolicy(PasswordPolicyConfig(minLength = 8, minCharClasses = 3))
        strict.validate("abcd1234").isValid.shouldBeFalse()
        strict.validate("Abcd1234").isValid.shouldBeTrue()
    }
})
