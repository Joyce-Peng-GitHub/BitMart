package cn.edu.bit.bitmart.storage

import cn.edu.bit.bitmart.auth.AUTH_BEARER
import cn.edu.bit.bitmart.auth.fail
import cn.edu.bit.bitmart.shared.ErrorCode
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.serialization.Serializable

@Serializable
data class UploadResponse(val blobKey: String, val url: String, val contentType: String)

/**
 * POST /uploads/images：multipart 上传单张图片（需登录）。校验类型与大小，返回 blobKey。
 * 前端在创建 listing 时携带 blobKey。
 */
fun Route.uploadRoutes(uploadService: UploadService) {
    authenticate(AUTH_BEARER) {
        post("/uploads/images") {
            val bytes = call.readFirstFilePart()
                ?: return@post call.fail(HttpStatusCode.BadRequest, ErrorCode.VALIDATION_FAILED, "No uploaded file found")

            when (val r = uploadService.uploadImage(bytes)) {
                is UploadResult.Success ->
                    call.respond(HttpStatusCode.Created, UploadResponse(r.blobKey, r.url, r.contentType))
                is UploadResult.TooLarge ->
                    call.fail(HttpStatusCode.PayloadTooLarge, ErrorCode.VALIDATION_FAILED, "File exceeds size limit")
                is UploadResult.UnsupportedType ->
                    call.fail(HttpStatusCode.UnsupportedMediaType, ErrorCode.VALIDATION_FAILED, "Only JPEG/PNG/WebP images are supported")
                is UploadResult.Empty ->
                    call.fail(HttpStatusCode.BadRequest, ErrorCode.VALIDATION_FAILED, "Empty file")
            }
        }
    }
}

/** 读取 multipart 中第一个文件分片的字节。 */
private suspend fun ApplicationCall.readFirstFilePart(): ByteArray? {
    var result: ByteArray? = null
    receiveMultipart().forEachPart { part ->
        if (part is PartData.FileItem && result == null) {
            result = part.provider().toInputStream().readBytes()
        }
    }
    return result
}
