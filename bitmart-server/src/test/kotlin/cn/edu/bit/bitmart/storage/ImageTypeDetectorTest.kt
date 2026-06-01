package cn.edu.bit.bitmart.storage

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ImageTypeDetectorTest : FunSpec({

    fun bytes(vararg ints: Int) = ByteArray(ints.size) { ints[it].toByte() }

    test("识别 JPEG 魔数") {
        val jpeg = bytes(0xFF, 0xD8, 0xFF, 0xE0) + ByteArray(8)
        ImageTypeDetector.detect(jpeg)?.mimeType shouldBe "image/jpeg"
    }

    test("识别 PNG 魔数") {
        val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(4)
        ImageTypeDetector.detect(png)?.mimeType shouldBe "image/png"
    }

    test("识别 WebP 魔数") {
        val webp = "RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray()
        ImageTypeDetector.detect(webp)?.mimeType shouldBe "image/webp"
    }

    test("非图片返回 null（伪造扩展名防护）") {
        val fake = "PK this is a zip".toByteArray()   // ZIP 魔数
        ImageTypeDetector.detect(fake) shouldBe null
    }

    test("过短输入返回 null") {
        ImageTypeDetector.detect(ByteArray(3)) shouldBe null
    }
})
