package at.hrechny.predictionsbot.util

import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Singleton
class HashUtils(
    @param:Value("\${secrets.telegramKey}")
    private val telegramKey: String,
) {
    fun getHash(originalString: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(telegramKey.toByteArray(StandardCharsets.UTF_8))
        val hashBytes = digest.digest(originalString.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(hashBytes)
    }

    private companion object {
        fun bytesToHex(hash: ByteArray): String {
            val hexString = StringBuilder(2 * hash.size)
            for (byte in hash) {
                val hex = Integer.toHexString(0xff and byte.toInt())
                if (hex.length == 1) {
                    hexString.append('0')
                }
                hexString.append(hex)
            }
            return hexString.toString()
        }
    }
}
