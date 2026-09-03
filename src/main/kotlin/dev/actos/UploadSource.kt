package dev.actos

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.InputStream

public sealed interface UploadSource {
    public val filename: String
    public val contentType: String?

    public fun toRequestBody(): RequestBody

    public data class FromFile(
        public val file: File,
        override val filename: String = file.name,
        override val contentType: String? = null,
    ) : UploadSource {
        override fun toRequestBody(): RequestBody = file.asRequestBody(contentType?.toMediaTypeOrNull())
    }

    public data class FromBytes(
        public val bytes: ByteArray,
        override val filename: String = "upload.bin",
        override val contentType: String? = null,
    ) : UploadSource {
        override fun toRequestBody(): RequestBody = bytes.toRequestBody(contentType?.toMediaTypeOrNull())

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FromBytes) return false
            if (!bytes.contentEquals(other.bytes)) return false
            if (filename != other.filename) return false
            if (contentType != other.contentType) return false
            return true
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + filename.hashCode()
            result = 31 * result + (contentType?.hashCode() ?: 0)
            return result
        }
    }

    public data class FromStream(
        public val stream: InputStream,
        override val filename: String = "upload.bin",
        public val byteLength: Long? = null,
        override val contentType: String? = null,
    ) : UploadSource {
        override fun toRequestBody(): RequestBody =
            object : RequestBody() {
                override fun contentType(): MediaType? = contentType?.toMediaTypeOrNull()

                override fun contentLength(): Long = byteLength ?: -1L

                override fun writeTo(sink: BufferedSink) {
                    stream.source().use { source ->
                        sink.writeAll(source)
                    }
                }
            }
    }
}
