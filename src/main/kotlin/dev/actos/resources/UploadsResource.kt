package dev.actos.resources

import dev.actos.Transport
import dev.actos.UploadSource
import dev.actos.model.UploadResponse
import okhttp3.MultipartBody
import java.io.File
import java.io.InputStream

public class UploadsResource internal constructor(
    internal val transport: Transport,
) {
    /**
     * Uploads media content (image/video) via multipart form upload.
     *
     * The file is normalized on the server (images to `image/webp`).
     *
     * @param source The media data source ([UploadSource.FromFile], [UploadSource.FromBytes],
     *               or [UploadSource.FromStream]).
     * @return [UploadResponse] containing the upload ID, public URL, dimensions, and checksum.
     */
    public suspend fun create(source: UploadSource): UploadResponse {
        val multipartBody =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", source.filename, source.toRequestBody())
                .build()
        val response = transport.post("/uploads", body = multipartBody)
        return response.parseJson()
    }

    /**
     * Convenience overload uploading a local [File].
     */
    public suspend fun create(file: File): UploadResponse = create(UploadSource.FromFile(file))

    /**
     * Convenience overload uploading in-memory bytes.
     */
    public suspend fun create(
        bytes: ByteArray,
        filename: String = "upload.bin",
    ): UploadResponse = create(UploadSource.FromBytes(bytes, filename))

    /**
     * Convenience overload uploading from a streaming [InputStream] without full heap buffering.
     */
    public suspend fun create(
        stream: InputStream,
        filename: String = "upload.bin",
        byteLength: Long? = null,
    ): UploadResponse = create(UploadSource.FromStream(stream, filename, byteLength))

    /**
     * Permanently deletes an unattached upload.
     *
     * Uploads attached to published posts or comments cannot be deleted directly.
     *
     * @param id The upload ID (e.g. `up_...`).
     */
    public suspend fun delete(id: String) {
        transport.delete("/uploads/$id")
    }
}
