package cn.edu.bit.bitmart.auth

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * BIT101 统一身份认证密码加密。
 *
 * 须与 BIT101 前端 EncryptPassword.ts（CryptoJS AES.encrypt，mode.ECB、pad.Pkcs7，
 * key 由 Base64 解析）保持字节级一致。JCE 的 PKCS5Padding 对 16 字节 AES 分组等价于 PKCS7Padding。
 *
 * salt 来自 BIT101 `webvpn_verify_init` 响应，是 Base64 编码的 AES 密钥（直接作为密钥使用）。
 * 入站的统一身份认证明文密码仅用于本次直连 BIT101，不落盘、不入日志。
 */
object Bit101PasswordCipher {

    private const val TRANSFORMATION = "AES/ECB/PKCS5Padding"

    /** 用 Base64 解码后的 salt 作为 AES 密钥加密明文密码，返回 Base64 密文。 */
    fun encrypt(plainPassword: String, salt: String): String {
        val keyBytes = Base64.getDecoder().decode(salt)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(plainPassword.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }
}
