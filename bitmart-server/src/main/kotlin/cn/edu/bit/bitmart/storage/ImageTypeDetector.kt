package cn.edu.bit.bitmart.storage

/**
 * 通过魔数（magic bytes）识别图片真实类型，防止伪造扩展名（架构 §8）。
 * 仅支持需求中允许的 JPEG / PNG / WebP。
 */
object ImageTypeDetector {

    data class ImageType(val mimeType: String, val extension: String)

    /** 识别字节流的图片类型；无法识别返回 null。 */
    fun detect(bytes: ByteArray): ImageType? {
        if (bytes.size < 12) return null
        return when {
            // JPEG: FF D8 FF
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() ->
                ImageType("image/jpeg", "jpg")
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() ->
                ImageType("image/png", "png")
            // WebP: "RIFF"...."WEBP"
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte() ->
                ImageType("image/webp", "webp")
            else -> null
        }
    }
}
