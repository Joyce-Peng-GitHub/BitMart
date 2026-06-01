package cn.edu.bit.bitmart.storage

/** Blob 引用：存储键与可公开访问的 URL。 */
data class BlobRef(val key: String, val url: String, val contentType: String, val size: Long)

/**
 * 二进制对象存储抽象（架构 §8）。初期落本地磁盘，未来切 MinIO/S3 仅替换实现。
 */
interface BlobStorage {
    /** 写入对象，返回引用。key 由实现内部生成时可忽略入参 key。 */
    suspend fun put(bytes: ByteArray, contentType: String, extension: String): BlobRef

    /** 读取对象字节；不存在返回 null。 */
    suspend fun get(key: String): ByteArray?

    /** 删除对象。 */
    suspend fun delete(key: String)

    /** 给前端直接拉取的公开 URL。 */
    fun publicUrl(key: String): String
}
