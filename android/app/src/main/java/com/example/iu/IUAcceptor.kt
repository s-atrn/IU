package com.example.iu

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class IUAcceptor(
    private val context: Context,
    private val onStarted: (Int) -> Unit = {},
    private val onFileReceived: (String, Uri) -> Unit = { _, _ -> },
    private val onProgress: (String, Long, Long) -> Unit = { _, _, _ -> },
    private val onError: (Exception) -> Unit = {}
) {
    private var serverSocket:
        ServerSocket? = null

    private val executor =
        Executors.newCachedThreadPool()

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private var lastProgressDispatchNanos =
        0L

    @Volatile
    private var running = false

    val isRunning: Boolean
        get() = running

    val port: Int
        get() = serverSocket?.localPort ?: -1

    fun start() {
        if (running) {
            return
        }

        createNotificationChannel()

        executor.execute {
            try {
                val socket =
                    ServerSocket(0)

                serverSocket =
                    socket

                running = true

                postToMain {
                    onStarted(
                        socket.localPort
                    )
                }

                while (running) {
                    try {
                        val client =
                            socket.accept()

                        executor.execute {
                            handleClient(client)
                        }

                    } catch (e: IOException) {
                        if (running) {
                            postToMain {
                                onError(e)
                            }
                        }
                    }
                }

            } catch (e: Exception) {

                running = false

                postToMain {
                    onError(e)
                }
            }
        }
    }

    private fun handleClient(
        client: Socket
    ) {
        client.use { socket ->

            var outputUri: Uri? = null

            try {
                socket.tcpNoDelay = true

                val input =
                    DataInputStream(
                        BufferedInputStream(
                            socket.getInputStream()
                        )
                    )

                val output =
                    DataOutputStream(
                        BufferedOutputStream(
                            socket.getOutputStream()
                        )
                    )

                val filenameLength =
                    input.readInt()

                if (
                    filenameLength <= 0 ||
                    filenameLength >
                    MAX_FILENAME_LENGTH
                ) {
                    throw IOException(
                        "Invalid filename length"
                    )
                }

                val filenameBytes =
                    ByteArray(
                        filenameLength
                    )

                input.readFully(
                    filenameBytes
                )

                val originalFilename =
                    String(
                        filenameBytes,
                        Charsets.UTF_8
                    )

                val filename =
                    sanitizeFilename(
                        originalFilename
                    )

                val fileSize =
                    input.readLong()

                if (fileSize < 0) {
                    throw IOException(
                        "Invalid file size"
                    )
                }

                outputUri =
                    createPendingFile(
                        filename
                    )

                if (outputUri == null) {
                    throw IOException(
                        "Unable to create destination file"
                    )
                }

                val fileOutput =
                    context.contentResolver
                        .openOutputStream(
                            outputUri,
                            "w"
                        )

                if (fileOutput == null) {
                    throw IOException(
                        "Unable to open destination file"
                    )
                }

                fileOutput.use { outputStream ->

                    receiveFile(
                        input = input,
                        output = outputStream,
                        filename = filename,
                        fileSize = fileSize
                    )
                }

                finalizeFile(
                    outputUri
                )

                output.writeByte(
                    ACK_BYTE
                )

                output.flush()

                postToMain {
                    onFileReceived(
                        filename,
                        outputUri
                    )
                }

                if (
                    IUSettings.notificationsEnabled(
                        context
                    )
                ) {
                    showReceivedNotification(
                        filename
                    )
                }

            } catch (e: Exception) {

                if (outputUri != null) {
                    try {
                        context.contentResolver.delete(
                            outputUri,
                            null,
                            null
                        )
                    } catch (_: Exception) {
                    }
                }

                postToMain {
                    onError(e)
                }
            }
        }
    }

    private fun receiveFile(
        input: DataInputStream,
        output: OutputStream,
        filename: String,
        fileSize: Long
    ) {
        val buffer =
            ByteArray(
                BUFFER_SIZE
            )

        var totalReceived =
            0L

        lastProgressDispatchNanos =
            0L

        while (
            totalReceived < fileSize
        ) {
            val remaining =
                fileSize -
                    totalReceived

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
                throw IOException(
                    "Connection closed before file was completely received"
                )
            }

            output.write(
                buffer,
                0,
                bytesRead
            )

            totalReceived +=
                bytesRead

            val now =
                System.nanoTime()

            val shouldDispatch =
                totalReceived >= fileSize ||
                    now -
                    lastProgressDispatchNanos >=
                    PROGRESS_INTERVAL_NANOS

            if (shouldDispatch) {

                lastProgressDispatchNanos =
                    now

                postToMain {
                    onProgress(
                        filename,
                        totalReceived,
                        fileSize
                    )
                }
            }
        }

        output.flush()
    }

    private fun createPendingFile(
        filename: String
    ): Uri? {
        val resolver =
            context.contentResolver

        val uniqueFilename =
            getUniqueFilename(
                filename
            )

        val values =
            ContentValues().apply {

                put(
                    MediaStore.Downloads.DISPLAY_NAME,
                    uniqueFilename
                )

                put(
                    MediaStore.Downloads.MIME_TYPE,
                    getMimeType(
                        uniqueFilename
                    )
                )

                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/IU/Received"
                )

                put(
                    MediaStore.Downloads.IS_PENDING,
                    1
                )
            }

        return resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        )
    }

    private fun finalizeFile(
        uri: Uri
    ) {
        val values =
            ContentValues().apply {
                put(
                    MediaStore.Downloads.IS_PENDING,
                    0
                )
            }

        val updated =
            context.contentResolver.update(
                uri,
                values,
                null,
                null
            )

        if (updated == 0) {
            throw IOException(
                "Unable to finalize received file"
            )
        }
    }

    private fun getUniqueFilename(
        filename: String
    ): String {
        val resolver =
            context.contentResolver

        val relativePath =
            "${Environment.DIRECTORY_DOWNLOADS}/IU/Received/"

        val dotIndex =
            filename.lastIndexOf('.')

        val base =
            if (dotIndex > 0) {
                filename.substring(
                    0,
                    dotIndex
                )
            } else {
                filename
            }

        val extension =
            if (dotIndex > 0) {
                filename.substring(
                    dotIndex
                )
            } else {
                ""
            }

        var counter =
            0

        while (true) {

            val candidate =
                if (counter == 0) {
                    "$base$extension"
                } else {
                    "$base ($counter)$extension"
                }

            val selection =
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND " +
                    "${MediaStore.Downloads.RELATIVE_PATH} = ?"

            val selectionArgs =
                arrayOf(
                    candidate,
                    relativePath
                )

            val exists =
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Downloads._ID
                    ),
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    cursor.moveToFirst()
                } ?: false

            if (!exists) {
                return candidate
            }

            counter++
        }
    }

    private fun getMimeType(
        filename: String
    ): String {
        val extension =
            filename
                .substringAfterLast(
                    '.',
                    ""
                )
                .lowercase()

        return when (extension) {
            "jpg",
            "jpeg" ->
                "image/jpeg"

            "png" ->
                "image/png"

            "gif" ->
                "image/gif"

            "webp" ->
                "image/webp"

            "heic",
            "heif" ->
                "image/heic"

            "mp4" ->
                "video/mp4"

            "mkv" ->
                "video/x-matroska"

            "mov" ->
                "video/quicktime"

            "avi" ->
                "video/x-msvideo"

            "mp3" ->
                "audio/mpeg"

            "wav" ->
                "audio/wav"

            "flac" ->
                "audio/flac"

            "pdf" ->
                "application/pdf"

            "zip" ->
                "application/zip"

            "rar" ->
                "application/vnd.rar"

            "7z" ->
                "application/x-7z-compressed"

            "txt" ->
                "text/plain"

            "json" ->
                "application/json"

            "xml" ->
                "application/xml"

            else ->
                "application/octet-stream"
        }
    }

    private fun sanitizeFilename(
        filename: String
    ): String {
        val clean =
            filename
                .replace("\\", "_")
                .replace("/", "_")
                .replace(":", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("\"", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_")
                .trim()

        return if (clean.isEmpty()) {
            "received_file"
        } else {
            clean
        }
    }

    private fun createNotificationChannel() {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Received files",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    "Notifications for files received by IU"
            }

        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.createNotificationChannel(
            channel
        )
    }

    private fun showReceivedNotification(
        filename: String
    ) {
        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val folderIntent =
            Intent(
                Intent.ACTION_VIEW
            ).apply {

                setDataAndType(
                    Uri.parse(
                        "content://com.android.externalstorage.documents/root/primary"
                    ),
                    "vnd.android.document/directory"
                )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        val pendingIntent =
            android.app.PendingIntent.getActivity(
                context,
                0,
                folderIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.M
                    ) {
                        android.app.PendingIntent.FLAG_IMMUTABLE
                    } else {
                        0
                    }
            )

        val notification =
            NotificationCompat.Builder(
                context,
                NOTIFICATION_CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.stat_sys_download_done
                )
                .setContentTitle(
                    "File received"
                )
                .setContentText(
                    filename
                )
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            "$filename was received successfully."
                        )
                )
                .setPriority(
                    NotificationCompat.PRIORITY_DEFAULT
                )
                .setContentIntent(
                    pendingIntent
                )
                .setAutoCancel(
                    true
                )
                .build()

        manager.notify(
            nextNotificationId(),
            notification
        )
    }

    private fun nextNotificationId(): Int {
        synchronized(
            NOTIFICATION_LOCK
        ) {
            notificationId++

            if (
                notificationId >=
                Int.MAX_VALUE
            ) {
                notificationId = 1
            }

            return notificationId
        }
    }

    fun stop() {
        running = false

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }

        serverSocket = null
    }

    fun shutdown() {
        stop()

        try {
            executor.shutdownNow()
        } catch (_: Exception) {
        }
    }

    private fun postToMain(
        action: () -> Unit
    ) {
        mainHandler.post {
            action()
        }
    }

    companion object {
        private const val BUFFER_SIZE =
            64 * 1024

        private const val MAX_FILENAME_LENGTH =
            4096

        private const val ACK_BYTE =
            0x06

        private const val NOTIFICATION_CHANNEL_ID =
            "iu_received_files"

        private const val NOTIFICATION_LOCK =
            "iu_notification_lock"

        private const val PROGRESS_INTERVAL_NANOS =
            100_000_000L

        private var notificationId =
            1000
    }
}