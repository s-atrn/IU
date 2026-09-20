package com.example.iu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.net.InetAddress
import java.util.UUID

class IUTransferService : Service() {

    data class LiveTransferState(
        val transferId: String,
        val status: String,
        val fileName: String?,
        val progress: Int,
        val deviceName: String?
    )

    companion object {

        const val ACTION_START =
            "com.example.iu.START_TRANSFER"

        const val ACTION_STOP =
            "com.example.iu.STOP_TRANSFER"

        const val ACTION_STATE_CHANGED =
            "com.example.iu.TRANSFER_STATE_CHANGED"

        const val EXTRA_HOST =
            "host"

        const val EXTRA_PORT =
            "port"

        const val EXTRA_DEVICE_NAME =
            "device_name"

        const val EXTRA_FILES =
            "files"

        const val EXTRA_TRANSFER_ID =
            "transfer_id"

        const val PREFS_NAME =
            "iu_transfer_state"

        const val PREF_STATUS =
            "status"

        const val PREF_FILE_NAME =
            "file_name"

        const val PREF_PROGRESS =
            "progress"

        const val PREF_DEVICE_NAME =
            "device_name"

        const val PREF_TRANSFER_ID =
            "transfer_id"

        const val STATUS_IDLE =
            "idle"

        const val STATUS_CONNECTING =
            "connecting"

        const val STATUS_SENDING =
            "sending"

        const val STATUS_COMPLETE =
            "complete"

        const val STATUS_FAILED =
            "failed"

        private const val CHANNEL_ID =
            "iu_transfer"

        private const val NOTIFICATION_ID =
            1001

        @Volatile
        private var liveState:
            LiveTransferState? = null

        fun getLiveState():
            LiveTransferState? {
            return liveState
        }
    }

    private var sender:
        IUSender? = null

    private lateinit var preferences:
        SharedPreferences

    private var currentTransferId:
        String? = null

    override fun onCreate() {

        super.onCreate()

        preferences =
            getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
            )

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                title = "IU",
                text = "Preparing transfer...",
                progress = 0,
                showProgress = false
            )
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {

                val host =
                    intent.getStringExtra(
                        EXTRA_HOST
                    )

                val port =
                    intent.getIntExtra(
                        EXTRA_PORT,
                        -1
                    )

                val deviceName =
                    intent.getStringExtra(
                        EXTRA_DEVICE_NAME
                    ) ?: "PC"

                val transferId =
                    intent.getStringExtra(
                        EXTRA_TRANSFER_ID
                    ) ?: UUID.randomUUID().toString()

                val files =
                    if (
                        Build.VERSION.SDK_INT >= 33
                    ) {

                        intent.getParcelableArrayListExtra(
                            EXTRA_FILES,
                            Uri::class.java
                        )

                    } else {

                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra<Uri>(
                            EXTRA_FILES
                        )
                    }

                if (
                    host.isNullOrBlank() ||
                    port <= 0 ||
                    files.isNullOrEmpty()
                ) {

                    currentTransferId =
                        transferId

                    setState(
                        transferId =
                            transferId,
                        status =
                            STATUS_FAILED,
                        fileName =
                            null,
                        progress =
                            0,
                        deviceName =
                            deviceName
                    )

                    showFinishedNotification(
                        "Invalid transfer request"
                    )

                    finishService()

                    return START_NOT_STICKY
                }

                sender?.stop()
                sender?.shutdown()
                sender = null

                currentTransferId =
                    transferId

                setState(
                    transferId =
                        transferId,
                    status =
                        STATUS_CONNECTING,
                    fileName =
                        getFileName(
                            files.first()
                        ),
                    progress =
                        0,
                    deviceName =
                        deviceName
                )

                startTransfer(
                    host =
                        host,
                    port =
                        port,
                    deviceName =
                        deviceName,
                    transferId =
                        transferId,
                    files =
                        files
                )
            }

            ACTION_STOP -> {

                val transferId =
                    currentTransferId

                if (transferId != null) {

                    setState(
                        transferId =
                            transferId,
                        status =
                            STATUS_IDLE,
                        fileName =
                            null,
                        progress =
                            0,
                        deviceName =
                            null
                    )
                }

                stopTransfer()
            }
        }

        return START_NOT_STICKY
    }

    private fun startTransfer(
        host: String,
        port: Int,
        deviceName: String,
        transferId: String,
        files: ArrayList<Uri>
    ) {

        updateNotification(
            text =
                "Connecting to $deviceName...",
            progress =
                0,
            showProgress =
                false
        )

        sender =
            IUSender(
                contentResolver =
                    contentResolver,

                onProgress = {
                    fileName,
                    bytesSent,
                    totalBytes ->

                    if (
                        transferId !=
                        currentTransferId
                    ) {
                        return@IUSender
                    }

                    val percentage =
                        if (totalBytes > 0) {

                            (
                                bytesSent.toDouble() /
                                    totalBytes.toDouble() *
                                    100.0
                            ).toInt()

                        } else {
                            0
                        }

                    setState(
                        transferId =
                            transferId,
                        status =
                            STATUS_SENDING,
                        fileName =
                            fileName,
                        progress =
                            percentage,
                        deviceName =
                            deviceName
                    )

                    updateNotification(
                        text =
                            "Sending $fileName • $percentage%",
                        progress =
                            percentage,
                        showProgress =
                            true
                    )
                },

                onFileSent = {
                    fileName ->

                    if (
                        transferId !=
                        currentTransferId
                    ) {
                        return@IUSender
                    }

                    setState(
                        transferId =
                            transferId,
                        status =
                            STATUS_SENDING,
                        fileName =
                            fileName,
                        progress =
                            100,
                        deviceName =
                            deviceName
                    )

                    updateNotification(
                        text =
                            "Sent $fileName",
                        progress =
                            100,
                        showProgress =
                            true
                    )
                },

                onComplete = {

                    if (
                        transferId !=
                        currentTransferId
                    ) {
                        return@IUSender
                    }

                    setState(
                        transferId =
                            transferId,
                        status =
                            STATUS_COMPLETE,
                        fileName =
                            null,
                        progress =
                            100,
                        deviceName =
                            deviceName
                    )

                    showFinishedNotification(
                        "Transfer complete"
                    )

                    sender?.shutdown()
                    sender = null

                    currentTransferId =
                        null

                    liveState =
                        null

                    stopForeground(
                        STOP_FOREGROUND_REMOVE
                    )

                    stopSelf()
                },

                onError = {
                    error ->

                    if (
                        transferId !=
                        currentTransferId
                    ) {
                        return@IUSender
                    }

                    setState(
                        transferId =
                            transferId,
                        status =
                            STATUS_FAILED,
                        fileName =
                            null,
                        progress =
                            0,
                        deviceName =
                            deviceName
                    )

                    showFinishedNotification(
                        "Transfer failed: ${
                            error.message
                                ?: "Unknown error"
                        }"
                    )

                    sender?.shutdown()
                    sender = null

                    currentTransferId =
                        null

                    liveState =
                        null

                    stopForeground(
                        STOP_FOREGROUND_REMOVE
                    )

                    stopSelf()
                }
            )

        try {

            val device =
                IUDevice(
                    name =
                        deviceName,
                    host =
                        InetAddress.getByName(
                            host
                        ),
                    port =
                        port
                )

            sender?.send(
                device,
                files
            )

        } catch (e: Exception) {

            if (
                transferId ==
                currentTransferId
            ) {

                setState(
                    transferId =
                        transferId,
                    status =
                        STATUS_FAILED,
                    fileName =
                        null,
                    progress =
                        0,
                    deviceName =
                        deviceName
                )

                showFinishedNotification(
                    "Transfer failed: ${
                        e.message
                            ?: "Unknown error"
                    }"
                )
            }

            sender?.shutdown()
            sender = null

            currentTransferId =
                null

            liveState =
                null

            stopForeground(
                STOP_FOREGROUND_REMOVE
            )

            stopSelf()
        }
    }

    private fun setState(
        transferId: String,
        status: String,
        fileName: String?,
        progress: Int,
        deviceName: String?
    ) {

        liveState =
            LiveTransferState(
                transferId =
                    transferId,
                status =
                    status,
                fileName =
                    fileName,
                progress =
                    progress.coerceIn(
                        0,
                        100
                    ),
                deviceName =
                    deviceName
            )

        preferences.edit()
            .putString(
                PREF_TRANSFER_ID,
                transferId
            )
            .putString(
                PREF_STATUS,
                status
            )
            .putString(
                PREF_FILE_NAME,
                fileName
            )
            .putInt(
                PREF_PROGRESS,
                progress.coerceIn(
                    0,
                    100
                )
            )
            .putString(
                PREF_DEVICE_NAME,
                deviceName
            )
            .apply()

        sendStateBroadcast()
    }

    private fun sendStateBroadcast() {

        val intent =
            Intent(
                ACTION_STATE_CHANGED
            ).apply {
                setPackage(
                    packageName
                )
            }

        sendBroadcast(intent)
    }

    private fun stopTransfer() {

        sender?.stop()
        sender?.shutdown()

        sender = null

        liveState =
            null

        currentTransferId =
            null

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    private fun finishService() {

        liveState =
            null

        currentTransferId =
            null

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    private fun getFileName(
        uri: Uri
    ): String {

        var name: String? = null

        contentResolver.query(
            uri,
            arrayOf(
                android.provider.OpenableColumns.DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->

            if (cursor.moveToFirst()) {

                val index =
                    cursor.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME
                    )

                if (index >= 0) {
                    name =
                        cursor.getString(index)
                }
            }
        }

        return name
            ?: uri.lastPathSegment
            ?: "file"
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "IU Transfers",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {

                    description =
                        "IU file transfer progress"

                    setShowBadge(false)

                    enableVibration(false)

                    setSound(
                        null,
                        null
                    )
                }

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun buildNotification(
        title: String,
        text: String,
        progress: Int,
        showProgress: Boolean
    ): Notification {

        val builder =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.stat_sys_upload
                )
                .setContentTitle(
                    title
                )
                .setContentText(
                    text
                )
                .setOngoing(
                    showProgress
                )
                .setOnlyAlertOnce(
                    true
                )
                .setPriority(
                    NotificationCompat.PRIORITY_LOW
                )
                .setCategory(
                    NotificationCompat.CATEGORY_PROGRESS
                )

        if (showProgress) {

            builder.setProgress(
                100,
                progress.coerceIn(
                    0,
                    100
                ),
                false
            )
        }

        return builder.build()
    }

    private fun updateNotification(
        text: String,
        progress: Int,
        showProgress: Boolean
    ) {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                title =
                    "IU",
                text =
                    text,
                progress =
                    progress,
                showProgress =
                    showProgress
            )
        )
    }

    private fun showFinishedNotification(
        text: String
    ) {

        val notification =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setSmallIcon(
                    android.R.drawable.stat_sys_upload_done
                )
                .setContentTitle(
                    "IU"
                )
                .setContentText(
                    text
                )
                .setAutoCancel(
                    true
                )
                .setOngoing(
                    false
                )
                .setOnlyAlertOnce(
                    true
                )
                .setPriority(
                    NotificationCompat.PRIORITY_DEFAULT
                )
                .build()

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            notification
        )
    }

    override fun onDestroy() {

        sender?.shutdown()
        sender = null

        liveState = null

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}