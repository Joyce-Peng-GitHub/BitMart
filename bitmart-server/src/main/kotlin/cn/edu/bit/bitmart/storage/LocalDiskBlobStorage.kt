package cn.edu.bit.bitmart.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 本地磁盘 Blob 存储（架构 §8）。按 yyyy/MM/dd/<uuid>.<ext> 落盘，
 * 通过 publicBaseUrl 暴露为 /static/<key>。IO 切到 Dispatchers.IO。
 */
class LocalDiskBlobStorage(
    rootDir: String,
    private val publicBaseUrl: String,
) : BlobStorage {

    private val root: Path = Paths.get(rootDir).toAbsolutePath().normalize()
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd")

    override suspend fun put(bytes: ByteArray, contentType: String, extension: String): BlobRef =
        withContext(Dispatchers.IO) {
            val key = "${LocalDate.now().format(dateFmt)}/${UUID.randomUUID()}.$extension"
            val target = resolveSafely(key)
            Files.createDirectories(target.parent)
            Files.write(target, bytes)
            BlobRef(key = key, url = publicUrl(key), contentType = contentType, size = bytes.size.toLong())
        }

    override suspend fun get(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val target = resolveSafely(key)
        if (Files.exists(target)) Files.readAllBytes(target) else null
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) {
            Files.deleteIfExists(resolveSafely(key))
        }
    }

    override fun publicUrl(key: String): String = "${publicBaseUrl.trimEnd('/')}/$key"

    /** 解析并校验 key 落在 root 内，防止路径穿越（../）。 */
    private fun resolveSafely(key: String): Path {
        val resolved = root.resolve(key).normalize()
        require(resolved.startsWith(root)) { "非法存储键: $key" }
        return resolved
    }
}
