package com.example.iu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicInteger

class IUAcceptorService : Service() {

    private var acceptor: IUAcceptor? = null
    private var networkDiscovery: NetworkDiscovery? = null

    private val progressNotificationIds =
        mutableMapOf<String, Int>()

    private val nextTransferNotificationId =
        AtomicInteger(3000)

    companion object {

        const val ACTION_START =
            "com.example.iu.START_ACCEPTOR"

        const val ACTION_STOP =
            "com.example.iu.STOP_ACCEPTOR"

        private const val CHANNEL_ID =
            "iu_acceptor"

        private const val TRANSFER_CHANNEL_ID =
            "iu_transfer"

        private const val NOTIFICATION_ID =
            2001

        private const val PREFS_NAME =
            "iu_acceptor_state"

        private const val PREF_RUNNING =
            "running"

        private const val NOTIFICATION_TITLE =
            "IU acceptor active"

        @Volatile
        private var running =
            false

        @Volatile
        private var activePort =
            -1

        fun isRunning(): Boolean {
            return running
        }
    }

    private val preferences by lazy {
        getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannels()

        startForeground(
            NOTIFICATION_ID,
            buildAcceptorNotification()
        )

        running = true

        preferences.edit()
            .putBoolean(
                PREF_RUNNING,
                true
            )
            .apply()

        startAcceptor()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {
                stopAcceptor()
            }

            ACTION_START -> {
                if (!running) {
                    startAcceptor()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startAcceptor() {

        if (acceptor != null) {
            return
        }

        val newAcceptor =
            IUAcceptor(
                context = this,

                onStarted = { port ->

                    activePort = port

                    networkDiscovery =
                        NetworkDiscovery(
                            context = this,
                            servicePort = port,
                            onDevicesChanged = {
                                // The acceptor does not need
                                // the discovered device list.
                            }
                        )

                    networkDiscovery?.start()

                    updateAcceptorNotification()
                },

                onFileReceived = { filename, _ ->

                    showTransferCompleteNotification(
                        filename
                    )
                },

                onProgress = { filename, received, total ->

                    showTransferProgressNotification(
                        filename,
                        received,
                        total
                    )
                },

                onError = {

                    cancelProgressNotificationForFailedTransfer()
                }
            )

        acceptor = newAcceptor

        newAcceptor.start()
    }

    private fun stopAcceptor() {

        cancelAllProgressNotifications()

        networkDiscovery?.stop()
        networkDiscovery = null

        acceptor?.shutdown()
        acceptor = null

        activePort = -1
        running = false

        preferences.edit()
            .putBoolean(
                PREF_RUNNING,
                false
            )
            .apply()

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    private fun createNotificationChannels() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        val acceptorChannel =
            NotificationChannel(
                CHANNEL_ID,
                "IU Acceptor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {

                description =
                    "IU file receiving"

                setShowBadge(false)

                enableVibration(false)

                setSound(
                    null,
                    null
                )
            }

        manager.createNotificationChannel(
            acceptorChannel
        )

        val transferChannel =
            NotificationChannel(
                TRANSFER_CHANNEL_ID,
                "IU Transfers",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {

                description =
                    "IU file transfer progress"

                setShowBadge(true)
            }

        manager.createNotificationChannel(
            transferChannel
        )
    }

    private fun buildAcceptorNotification(): Notification {

        val portText =
            if (activePort > 0) {
                "Port $activePort"
            } else {
                "Starting..."
            }

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                R.drawable.ic_iu_notification
            )
            .setContentTitle(
                NOTIFICATION_TITLE
            )
            .setContentText(
                portText
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .build()
    }

    private fun updateAcceptorNotification() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            buildAcceptorNotification()
        )
    }

    private fun showTransferProgressNotification(
        filename: String,
        received: Long,
        total: Long
    ) {

        val notificationId =
            synchronized(
                progressNotificationIds
            ) {

                progressNotificationIds[
                    filename
                ] ?: nextTransferNotificationId
                    .incrementAndGet()
                    .also { id ->
                        progressNotificationIds[
                            filename
                        ] = id
                    }
            }

        val progress =
            if (total > 0) {
                (
                    received.toDouble() /
                        total.toDouble() *
                        100.0
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        100
                    )
            } else {
                0
            }

        val notification =
            NotificationCompat.Builder(
                this,
                TRANSFER_CHANNEL_ID
            )
                .setSmallIcon(
                    R.drawable.ic_iu_notification
                )
                .setContentTitle(
                    "Receiving file"
                )
                .setContentText(
                    filename
                )
                .setProgress(
                    100,
                    progress,
                    total <= 0
                )
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(
                    NotificationCompat.PRIORITY_DEFAULT
                )
                .build()

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            notificationId,
            notification
        )
    }

    private fun showTransferCompleteNotification(
        filename: String
    ) {

        val notificationId =
            synchronized(
                progressNotificationIds
            ) {
                progressNotificationIds.remove(
                    filename
                )
            }

        if (notificationId != null) {

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.cancel(
                notificationId
            )
        }

        val completionId =
            nextTransferNotificationId
                .incrementAndGet()

        val notification =
            NotificationCompat.Builder(
                this,
                TRANSFER_CHANNEL_ID
            )
                .setSmallIcon(
                    R.drawable.ic_iu_notification
                )
                .setContentTitle(
                    "File received"
                )
                .setContentText(
                    filename
                )
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(
                    NotificationCompat.PRIORITY_DEFAULT
                )
                .build()

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            completionId,
            notification
        )
    }

    private fun cancelProgressNotificationForFailedTransfer() {

        synchronized(
            progressNotificationIds
        ) {

            if (
                progressNotificationIds.isEmpty()
            ) {
                return
            }

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            val notificationId =
                progressNotificationIds
                    .values
                    .first()

            manager.cancel(
                notificationId
            )

            val filename =
                progressNotificationIds
                    .entries
                    .first()
                    .key

            progressNotificationIds.remove(
                filename
            )
        }
    }

    private fun cancelAllProgressNotifications() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        synchronized(
            progressNotificationIds
        ) {

            progressNotificationIds.values
                .forEach { notificationId ->

                    manager.cancel(
                        notificationId
                    )
                }

            progressNotificationIds.clear()
        }
    }

    override fun onDestroy() {

        cancelAllProgressNotifications()

        networkDiscovery?.stop()
        networkDiscovery = null

        acceptor?.shutdown()
        acceptor = null

        activePort = -1
        running = false

        preferences.edit()
            .putBoolean(
                PREF_RUNNING,
                false
            )
            .apply()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}