package cn.edu.bit.bitmart.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TagNormalizerTest : FunSpec({

    test("去首尾空白并转小写") {
        TagNormalizer.normalize("  Java ") shouldBe "java"
    }

    test("压缩内部连续空白为单个空格") {
        TagNormalizer.normalize("data   structure") shouldBe "data structure"
    }

    test("不同写法归一化为同一标签") {
        val a = TagNormalizer.normalize("JAVA")
        val b = TagNormalizer.normalize(" java ")
        a shouldBe b
    }

    test("去重保持首次出现顺序") {
        TagNormalizer.normalizeDistinct(listOf("Java", "java ", "Kotlin")) shouldBe listOf("java", "kotlin")
    }

    test("丢弃归一化后为空的项") {
        TagNormalizer.normalizeDistinct(listOf("  ", "Go", "")) shouldBe listOf("go")
    }
})
