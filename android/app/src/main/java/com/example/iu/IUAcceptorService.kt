package com.example.iu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class IUAcceptorService : Service() {

    private var acceptor: IUAcceptor? = null

    private var networkDiscovery:
        NetworkDiscovery? = null

    companion object {

        const val ACTION_START =
            "com.example.iu.START_ACCEPTOR"

        const val ACTION_STOP =
            "com.example.iu.STOP_ACCEPTOR"

        const val ACTION_STATE_CHANGED =
            "com.example.iu.ACCEPTOR_STATE_CHANGED"

        const val STATE_STARTED =
            "started"

        const val STATE_PROGRESS =
            "progress"

        const val STATE_COMPLETE =
            "complete"

        const val STATE_ERROR =
            "error"

        const val STATE_STOPPED =
            "stopped"

        const val EXTRA_STATE =
            "state"

        const val EXTRA_PORT =
            "port"

        const val EXTRA_FILENAME =
            "filename"

        const val EXTRA_RECEIVED =
            "received"

        const val EXTRA_TOTAL =
            "total"

        const val EXTRA_ERROR =
            "error"

        private const val CHANNEL_ID =
            "iu_acceptor"

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

        fun getActivePort(): Int {
            return activePort
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
                            onDevicesChanged = {},
                            advertiseSelf = true,
                            discoverServices = false
                        )

                    networkDiscovery?.start()

                    updateAcceptorNotification()

                    sendState(
                        state = STATE_STARTED
                    )
                },

                onFileReceived = { filename, _ ->

                    sendState(
                        state = STATE_COMPLETE,
                        filename = filename
                    )
                },

                onProgress = {
                    filename,
                    received,
                    total ->

                    sendState(
                        state = STATE_PROGRESS,
                        filename = filename,
                        received = received,
                        total = total
                    )
                },

                onError = { error ->

                    sendState(
                        state = STATE_ERROR,
                        error =
                            error.message
                                ?: "Unknown error"
                    )
                }
            )

        acceptor = newAcceptor

        newAcceptor.start()
    }

    private fun stopAcceptor() {

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

        sendState(
            state = STATE_STOPPED
        )

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
    }

    private fun buildAcceptorNotification():
        Notification {

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

    private fun sendState(
        state: String,
        filename: String? = null,
        received: Long = 0L,
        total: Long = 0L,
        error: String? = null
    ) {
        val intent =
            Intent(
                ACTION_STATE_CHANGED
            ).apply {

                setPackage(
                    packageName
                )

                putExtra(
                    EXTRA_STATE,
                    state
                )

                putExtra(
                    EXTRA_PORT,
                    activePort
                )

                if (filename != null) {
                    putExtra(
                        EXTRA_FILENAME,
                        filename
                    )
                }

                putExtra(
                    EXTRA_RECEIVED,
                    received
                )

                putExtra(
                    EXTRA_TOTAL,
                    total
                )

                if (error != null) {
                    putExtra(
                        EXTRA_ERROR,
                        error
                    )
                }
            }

        sendBroadcast(intent)
    }

    override fun onDestroy() {

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