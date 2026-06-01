package cn.edu.bit.bitmart.storage

import cn.edu.bit.bitmart.config.UploadConfig

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
    suspend fun uploadImage(bytes: ByteArray): UploadResult {
        if (bytes.isEmpty()) return UploadResult.Empty
        if (bytes.size > uploadConfig.maxFileBytes) return UploadResult.TooLarge

        val type = ImageTypeDetector.detect(bytes) ?: return UploadResult.UnsupportedType
        if (type.mimeType !in uploadConfig.allowedMimeTypes) return UploadResult.UnsupportedType

        val ref = blobStorage.put(bytes, type.mimeType, type.extension)
        return UploadResult.Success(ref.key, ref.url, ref.contentType)
    }
}
