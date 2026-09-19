package com.example.iu

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class IUSender(
    private val contentResolver: ContentResolver,

    private val onProgress: (
        fileName: String,
        bytesSent: Long,
        totalBytes: Long
    ) -> Unit = { _, _, _ -> },

    private val onFileSent: (
        fileName: String
    ) -> Unit = {},

    private val onComplete: () -> Unit = {},

    private val onError: (
        Exception
    ) -> Unit = {}
) {

    private val executor =
        Executors.newSingleThreadExecutor()

    @Volatile
    private var sending = false

    @Volatile
    private var stopped = false

    val isSending: Boolean
        get() = sending

    fun send(
        device: IUDevice,
        files: List<Uri>
    ) {

        if (
            sending ||
            files.isEmpty()
        ) {
            return
        }

        sending = true
        stopped = false

        executor.execute {

            try {

                for (uri in files) {

                    if (stopped) {
                        return@execute
                    }

                    sendFile(
                        device,
                        uri
                    )
                }

                /*
                 * We only get here when every requested file
                 * has successfully completed.
                 */
                if (!stopped) {
                    onComplete()
                }

            } catch (e: Exception) {

                if (!stopped) {
                    onError(e)
                }

            } finally {

                sending = false
                stopped = false
            }
        }
    }

    private fun sendFile(
        device: IUDevice,
        uri: Uri
    ) {

        val fileName =
            getFileName(uri)

        val fileSize =
            getFileSize(uri)

        if (fileSize < 0) {

            throw IllegalStateException(
                "Unable to determine file size: $fileName"
            )
        }

        contentResolver
            .openInputStream(uri)
            ?.use { inputStream ->

                Socket().use { socket ->

                    socket.tcpNoDelay = true

                    socket.connect(
                        InetSocketAddress(
                            device.host,
                            device.port
                        ),
                        CONNECTION_TIMEOUT
                    )

                    val output =
                        DataOutputStream(
                            BufferedOutputStream(
                                socket.getOutputStream()
                            )
                        )

                    val filenameBytes =
                        fileName.toByteArray(
                            Charsets.UTF_8
                        )

                    /*
                     * Protocol:
                     *
                     * 4 bytes  filename length
                     * N bytes  filename
                     * 8 bytes  file size
                     * N bytes  file data
                     */

                    output.writeInt(
                        filenameBytes.size
                    )

                    output.write(
                        filenameBytes
                    )

                    output.writeLong(
                        fileSize
                    )

                    val input =
                        BufferedInputStream(
                            inputStream
                        )

                    val buffer =
                        ByteArray(
                            BUFFER_SIZE
                        )

                    var totalSent = 0L

                    while (
                        totalSent < fileSize
                    ) {

                        if (stopped) {
                            return
                        }

                        val remaining =
                            fileSize -
                                totalSent

                        val bytesToRead =
                            minOf(
                                BUFFER_SIZE.toLong(),
                                remaining
                            ).toInt()

                        val bytesRead =
                            input.read(
                                buffer,
                                0,
                                bytesToRead
                            )

                        if (bytesRead == -1) {

                            throw IllegalStateException(
                                "File ended before expected size: $fileName"
                            )
                        }

                        output.write(
                            buffer,
                            0,
                            bytesRead
                        )

                        totalSent +=
                            bytesRead

                        onProgress(
                            fileName,
                            totalSent,
                            fileSize
                        )
                    }

                    /*
                     * Make absolutely sure every byte has been
                     * handed to the socket before considering
                     * this file finished.
                     */
                    output.flush()

                    /*
                     * Closing the socket happens after leaving
                     * this use block.
                     */
                }

            } ?: throw IllegalStateException(
                "Unable to open file: $fileName"
            )

        if (!stopped) {
            onFileSent(fileName)
        }
    }

    private fun getFileName(
        uri: Uri
    ): String {

        var fileName: String? = null

        contentResolver.query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->

            if (cursor.moveToFirst()) {

                val index =
                    cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )

                if (index >= 0) {

                    fileName =
                        cursor.getString(index)
                }
            }
        }

        return fileName
            ?: uri.lastPathSegment
            ?: "file"
    }

    private fun getFileSize(
        uri: Uri
    ): Long {

        contentResolver.query(
            uri,
            arrayOf(
                OpenableColumns.SIZE
            ),
            null,
            null,
            null
        )?.use { cursor ->

            if (cursor.moveToFirst()) {

                val index =
                    cursor.getColumnIndex(
                        OpenableColumns.SIZE
                    )

                if (
                    index >= 0 &&
                    !cursor.isNull(index)
                ) {

                    return cursor.getLong(index)
                }
            }
        }

        return -1L
    }

    fun stop() {

        stopped = true
        sending = false
    }

    fun shutdown() {

        stopped = true
        sending = false

        try {
            executor.shutdownNow()
        } catch (_: Exception) {
        }
    }

    companion object {

        private const val BUFFER_SIZE =
            64 * 1024

        private const val CONNECTION_TIMEOUT =
            5_000
    }
}