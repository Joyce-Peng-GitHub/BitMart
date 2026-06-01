package cn.edu.bit.bitmart.storage

import cn.edu.bit.bitmart.config.UploadConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files

class UploadServiceTest : FunSpec({

    val tempRoot = Files.createTempDirectory("bitmart-upload-test").toString()
    val storage = LocalDiskBlobStorage(tempRoot, "/static")
    val config = UploadConfig(maxFileBytes = 1024, maxFilesPerListing = 9, allowedMimeTypes = listOf("image/jpeg", "image/png"))
    val service = UploadService(storage, config)

    val jpeg = ByteArray(20).also {
        it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte(); it[3] = 0xE0.toByte()
    }

    test("合法 JPEG 上传成功并可读回") {
        val r = service.uploadImage(jpeg)
        r.shouldBeInstanceOf<UploadResult.Success>()
        r.contentType shouldBe "image/jpeg"
        storage.get(r.blobKey)?.size shouldBe jpeg.size
    }

    test("超大文件被拒") {
        val big = ByteArray(2048).also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte(); it[2] = 0xFF.toByte() }
        service.uploadImage(big).shouldBeInstanceOf<UploadResult.TooLarge>()
    }

    test("空文件被拒") {
        service.uploadImage(ByteArray(0)).shouldBeInstanceOf<UploadResult.Empty>()
    }

    test("非图片（伪造）被拒") {
        service.uploadImage("not an image".toByteArray()).shouldBeInstanceOf<UploadResult.UnsupportedType>()
    }

    test("不在白名单的图片类型被拒（WebP 未允许）") {
        val webp = "RIFF".toByteArray() + ByteArray(4) + "WEBP".toByteArray() + ByteArray(4)
        service.uploadImage(webp).shouldBeInstanceOf<UploadResult.UnsupportedType>()
    }
})
