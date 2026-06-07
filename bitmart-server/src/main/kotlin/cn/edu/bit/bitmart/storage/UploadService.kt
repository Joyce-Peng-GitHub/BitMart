package cn.edu.bit.bitmart.storage

import cn.edu.bit.bitmart.config.UploadConfig
import org.slf4j.LoggerFactory

/** 图片上传结果。 */
sealed interface UploadResult {
    data class Success(val blobKey: String, val url: String, val contentType: String) : UploadResult
    data object TooLarge : UploadResult
    data object UnsupportedType : UploadResult
    data object Empty : UploadResult
}

/**
 * 图片上传服务：校验大小、用魔数识别真实类型并比对白名单，写入 BlobStorage。
 */
class UploadService(
    private val blobStorage: BlobStorage,
    private val uploadConfig: UploadConfig,
) {
    private val log = LoggerFactory.getLogger(UploadService::class.java)

    suspend fun uploadImage(bytes: ByteArray): UploadResult {
        if (bytes.isEmpty()) return UploadResult.Empty.also { log.warn("Upload rejected: empty bytes") }
        if (bytes.size > uploadConfig.maxFileBytes) return UploadResult.TooLarge.also {
            log.warn("Upload rejected: too large bytes={}", bytes.size)
        }

        val type = ImageTypeDetector.detect(bytes) ?: return UploadResult.UnsupportedType.also {
            log.warn("Upload rejected: unrecognized image type")
        }
        if (type.mimeType !in uploadConfig.allowedMimeTypes) return UploadResult.UnsupportedType.also {
            log.warn("Upload rejected: disallowed mime type={}", type.mimeType)
        }

        val ref = blobStorage.put(bytes, type.mimeType, type.extension)
        log.info("Image uploaded key={} type={} bytes={}", ref.key, type.mimeType, bytes.size)
        return UploadResult.Success(ref.key, ref.url, ref.contentType)
    }
}
