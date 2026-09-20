package com.example.iu

import android.app.ComponentCaller
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var selectedFileUris =
        emptyList<Uri>()

    private var selectedFileNames by
        mutableStateOf(emptyList<String>())

    private var devices by
        mutableStateOf(emptyList<IUDevice>())

    private var statusText by
        mutableStateOf("Starting IU...")

    private var transferFileName by
        mutableStateOf<String?>(null)

    private var transferProgress by
        mutableStateOf(0f)

    private var acceptor: IUAcceptor? = null

    private var discovery: NetworkDiscovery? = null

    private val appFont =
        FontFamily.SansSerif

    private var activeTransferId:
        String? = null

    private val transferStateReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (
                    intent?.action ==
                    IUTransferService.ACTION_STATE_CHANGED
                ) {
                    restoreTransferState()
                }
            }
        }

    private val filePicker =
        registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->

            if (uris.isNotEmpty()) {
                selectedFileUris = uris

                selectedFileNames =
                    uris.map { uri ->
                        getFileName(uri)
                    }

                statusText =
                    "${uris.size} file(s) selected"
            }
        }

    private val networkPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                requestNotificationPermission()
            } else {
                statusText =
                    "Nearby Wi-Fi permission required"
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            setupNetworking()
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        if (
            intent.getBooleanExtra(
                "MINIMIZE_IU",
                false
            )
        ) {
            moveTaskToBack(true)
            return
        }

        if (Settings.canDrawOverlays(this)) {
            startService(
                Intent(
                    this,
                    IUTouchLayerService::class.java
                )
            )
        } else {
            requestOverlayPermission()
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )

        if (hasNetworkPermission()) {
            requestNotificationPermission()
        } else {
            requestNetworkPermission()
        }

        restoreTransferState()


        handleIncomingShareIntent(intent)

        setContent {

            val cardShape =
                RoundedCornerShape(28.dp)

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable {
                            stopService(
                                Intent(
                                    this@MainActivity,
                                    IUTouchLayerService::class.java
                                )
                            )
                            moveTaskToBack(true)
                        }
            ) {
                Box(
                    modifier = 
                        Modifier
                            .fillMaxWidth(0.90f)
                            .fillMaxHeight(0.45f)
                            .align(Alignment.Center)
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize(0.857f)
                                .align(Alignment.Center)
                                .dropShadow(
                                    shape = cardShape,
                                    shadow =
                                        Shadow(
                                            radius = 7.dp,
                                            spread = 4.dp,
                                            color =
                                                Color.Black.copy(
                                                    alpha = 0.25f
                                                ),
                                            offset =
                                                DpOffset(
                                                    x = 4.dp,
                                                    y = 4.dp
                                                )
                                        )
                                )
                                .background(
                                    brush =
                                        Brush.verticalGradient(
                                            colors =
                                                listOf(
                                                    Color(
                                                        50,
                                                        50,
                                                        53
                                                    ),
                                                    Color(
                                                        18,
                                                        18,
                                                        20
                                                    )
                                                )
                                        ),
                                    shape = cardShape
                                )
                                .border(
                                    width = 1.dp,
                                    color =
                                        Color(
                                            130,
                                            130,
                                            145
                                        ).copy(
                                            alpha = 0.65f
                                        ),
                                    shape = cardShape
                                )
                    ) {

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                        ) {

                            Box(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                contentAlignment =
                                    Alignment.Center
                            ) {

                                Text(
                                    text = "IU",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight =
                                        FontWeight.SemiBold,
                                    fontFamily =
                                        appFont,
                                    letterSpacing =
                                        0.5.sp
                                )
                            }

                            Spacer(
                                modifier =
                                    Modifier.height(4.dp)
                            )

                            HorizontalDivider(
                                thickness = 1.dp,
                                color = Color.White
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(10.dp)
                            )

                            Text(
                                text = statusText,
                                color =
                                    Color.White.copy(
                                        alpha = 0.65f
                                    ),
                                fontSize = 12.sp,
                                fontFamily = appFont
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(8.dp)
                            )

                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                verticalArrangement =
                                    Arrangement.spacedBy(6.dp)
                            ) {

                                if (devices.isEmpty()) {

                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                        contentAlignment =
                                            Alignment.Center
                                    ) {

                                        Text(
                                            text =
                                                "No IU devices found",
                                            color =
                                                Color.White.copy(
                                                    alpha = 0.45f
                                                ),
                                            fontSize = 13.sp,
                                            fontFamily =
                                                appFont
                                        )
                                    }

                                } else {

                                    devices.forEach { device ->

                                        DeviceRow(
                                            device = device,
                                            fontFamily =
                                                appFont,
                                            enabled =
                                                selectedFileUris.isNotEmpty(),
                                            onClick = {
                                                sendToDevice(
                                                    device
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            if (
                                transferFileName != null
                            ) {

                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                bottom = 8.dp
                                            )
                                ) {

                                    Text(
                                        text =
                                            transferFileName!!,
                                        color =
                                            Color.White,
                                        fontSize = 12.sp,
                                        fontFamily =
                                            appFont
                                    )

                                    Spacer(
                                        modifier =
                                            Modifier.height(5.dp)
                                    )

                                    LinearProgressIndicator(
                                        progress =
                                            { transferProgress },
                                        modifier =
                                            Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            ElevatedButton(
                                onClick = {
                                    filePicker.launch(
                                        arrayOf("*/*")
                                    )
                                },
                                modifier =
                                    Modifier.fillMaxWidth()
                            ) {

                                val buttonText =
                                    when {

                                        selectedFileNames.isEmpty() ->
                                            "+"

                                        selectedFileNames.size == 1 ->
                                            displayFileName(
                                                selectedFileNames[0]
                                            )

                                        else ->
                                            "${selectedFileNames.size} files selected"
                                    }

                                Text(
                                    text = buttonText,
                                    color =
                                        if (
                                            selectedFileNames.isEmpty()
                                        ) {
                                            Color.Black
                                        } else {
                                            Color.DarkGray
                                        },
                                    fontSize =
                                        if (
                                            selectedFileNames.isEmpty()
                                        ) {
                                            20.sp
                                        } else {
                                            14.sp
                                        },
                                    fontWeight =
                                        if (
                                            selectedFileNames.isEmpty()
                                        ) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        },
                                    fontFamily =
                                        appFont
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(intent)

        setIntent(intent)

        handleIncomingShareIntent(
            intent
        )
    }

    private fun handleIncomingShareIntent(
        intent: Intent?
    ) {
        if (intent == null) {
            return
        }

        when (intent.action) {

            Intent.ACTION_SEND -> {

                val uri =
                    intent.getParcelableExtra<Uri>(
                        Intent.EXTRA_STREAM
                    )

                if (uri != null) {
                    setSharedFiles(
                        listOf(uri)
                    )
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {

                val uris =
                    if (Build.VERSION.SDK_INT >= 33) {

                        intent.getParcelableArrayListExtra(
                            Intent.EXTRA_STREAM,
                            Uri::class.java
                        )

                    } else {

                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra<Uri>(
                            Intent.EXTRA_STREAM
                        )
                    }

                if (
                    !uris.isNullOrEmpty()
                ) {
                    setSharedFiles(
                        uris
                    )
                }
            }
        }
    }

    private fun setSharedFiles(
        uris: List<Uri>
    ) {
        selectedFileUris =
            uris

        selectedFileNames =
            uris.map { uri ->
                getFileName(uri)
            }

        statusText =
            if (uris.size == 1) {
                "1 file selected"
            } else {
                "${uris.size} files selected"
            }
    }

    override fun onStart() {
        super.onStart()

        val filter =
            IntentFilter(
                IUTransferService.ACTION_STATE_CHANGED
            )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            registerReceiver(
                transferStateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )

        } else {

            @Suppress("DEPRECATION")
            registerReceiver(
                transferStateReceiver,
                filter
            )
        }

        restoreTransferState()
    }

    override fun onStop() {

        try {
            unregisterReceiver(
                transferStateReceiver
            )
        } catch (_: Exception) {
        }

        super.onStop()
    }

    private fun restoreTransferState() {


        val liveState =
            IUTransferService.getLiveState()

        if (liveState != null) {

            activeTransferId =
                liveState.transferId

            applyTransferState(
                status =
                    liveState.status,
                fileName =
                    liveState.fileName,
                progress =
                    liveState.progress,
                deviceName =
                    liveState.deviceName
            )

            return
        }

        val preferences =
            getSharedPreferences(
                IUTransferService.PREFS_NAME,
                MODE_PRIVATE
            )

        val status =
            preferences.getString(
                IUTransferService.PREF_STATUS,
                IUTransferService.STATUS_IDLE
            ) ?: IUTransferService.STATUS_IDLE

        val transferId =
            preferences.getString(
                IUTransferService.PREF_TRANSFER_ID,
                null
            )

        val fileName =
            preferences.getString(
                IUTransferService.PREF_FILE_NAME,
                null
            )

        val progress =
            preferences.getInt(
                IUTransferService.PREF_PROGRESS,
                0
            )

        val deviceName =
            preferences.getString(
                IUTransferService.PREF_DEVICE_NAME,
                null
            )

        activeTransferId =
            transferId

        applyTransferState(
            status =
                status,
            fileName =
                fileName,
            progress =
                progress,
            deviceName =
                deviceName
        )
    }

    private fun applyTransferState(
        status: String,
        fileName: String?,
        progress: Int,
        deviceName: String?
    ) {

        when (status) {

            IUTransferService.STATUS_CONNECTING -> {

                statusText =
                    if (deviceName != null) {
                        "Connecting to $deviceName..."
                    } else {
                        "Connecting..."
                    }

                transferFileName =
                    fileName

                transferProgress =
                    progress / 100f
            }

            IUTransferService.STATUS_SENDING -> {

                statusText =
                    if (deviceName != null) {
                        "Sending to $deviceName..."
                    } else {
                        "Sending..."
                    }

                transferFileName =
                    fileName

                transferProgress =
                    progress / 100f
            }

            IUTransferService.STATUS_COMPLETE -> {

                statusText =
                    "Transfer complete"

                transferFileName =
                    null

                transferProgress =
                    0f
            }

            IUTransferService.STATUS_FAILED -> {

                statusText =
                    "Transfer failed"

                transferFileName =
                    null

                transferProgress =
                    0f
            }

            IUTransferService.STATUS_IDLE -> {
            }
        }
    }


    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )

                return
            }
        }

        setupNetworking()
    }

    private fun hasNetworkPermission(): Boolean {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            checkSelfPermission(
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) ==
                PackageManager.PERMISSION_GRANTED

        } else {

            checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestNetworkPermission() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            networkPermissionLauncher.launch(
                Manifest.permission.NEARBY_WIFI_DEVICES
            )

        } else {

            networkPermissionLauncher.launch(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    private fun setupNetworking() {

        if (acceptor != null) {
            return
        }

        acceptor =
            IUAcceptor(
                context = this,

                onStarted = { port ->

                    statusText =
                        "Ready • port $port"

                    discovery =
                        NetworkDiscovery(
                            context = this,
                            servicePort = port,
                            onDevicesChanged = {
                                devices = it
                            }
                        )

                    discovery?.start()
                },

                onFileReceived = {
                    filename,
                    _ ->

                    statusText =
                        "Received $filename"

                    transferFileName =
                        null

                    transferProgress =
                        0f
                },

                onProgress = {
                    filename,
                    received,
                    total ->

                    transferFileName =
                        filename

                    transferProgress =
                        if (total > 0) {

                            received.toFloat() /
                                total.toFloat()

                        } else {
                            0f
                        }
                },

                onError = { error ->

                    statusText =
                        "Error: ${
                            error.message
                                ?: "Unknown error"
                        }"
                }
            )

        acceptor?.start()
    }

    private fun stopNetworking() {

        discovery?.stop()

        discovery = null

        acceptor?.shutdown()

        acceptor = null

        devices =
            emptyList()
    }

    private fun sendToDevice(
        device: IUDevice
    ) {

        if (
            selectedFileUris.isEmpty()
        ) {

            statusText =
                "Select a file first"

            return
        }

        val transferId =
            UUID.randomUUID().toString()

        activeTransferId =
            transferId

        val preferences =
            getSharedPreferences(
                IUTransferService.PREFS_NAME,
                MODE_PRIVATE
            )

        preferences.edit()
            .putString(
                IUTransferService.PREF_TRANSFER_ID,
                transferId
            )
            .putString(
                IUTransferService.PREF_STATUS,
                IUTransferService.STATUS_CONNECTING
            )
            .putString(
                IUTransferService.PREF_FILE_NAME,
                selectedFileNames.firstOrNull()
            )
            .putInt(
                IUTransferService.PREF_PROGRESS,
                0
            )
            .putString(
                IUTransferService.PREF_DEVICE_NAME,
                device.name
            )
            .apply()

        statusText =
            "Connecting to ${device.name}..."

        transferFileName =
            selectedFileNames.firstOrNull()

        transferProgress =
            0f

        startTransferService(
            device,
            selectedFileUris,
            transferId
        )
    }

    private fun startTransferService(
        device: IUDevice,
        files: List<Uri>,
        transferId: String
    ) {

        val intent =
            Intent(
                this,
                IUTransferService::class.java
            ).apply {

                action =
                    IUTransferService.ACTION_START

                putExtra(
                    IUTransferService.EXTRA_HOST,
                    device.host.hostAddress
                )

                putExtra(
                    IUTransferService.EXTRA_PORT,
                    device.port
                )

                putExtra(
                    IUTransferService.EXTRA_DEVICE_NAME,
                    device.name
                )

                putExtra(
                    IUTransferService.EXTRA_TRANSFER_ID,
                    transferId
                )

                putParcelableArrayListExtra(
                    IUTransferService.EXTRA_FILES,
                    ArrayList(files)
                )
            }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            startForegroundService(
                intent
            )

        } else {

            startService(
                intent
            )
        }

        statusText =
            "Sending to ${device.name}..."
    }

    private fun getFileName(
        uri: Uri
    ): String {

        var name: String? = null

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

                    name =
                        cursor.getString(index)
                }
            }
        }

        return name
            ?: uri.lastPathSegment
            ?: "Unknown file"
    }

    private fun displayFileName(
        name: String
    ): String {

        if (name.length <= 16) {
            return name
        }

        val dotIndex =
            name.lastIndexOf('.')

        if (dotIndex <= 0) {

            return "${name.take(8)}..." +
                name.takeLast(8)
        }

        val extension =
            name.substring(dotIndex)

        val baseName =
            name.substring(
                0,
                dotIndex
            )

        return "${baseName.take(8)}..." +
            "${baseName.takeLast(8)}" +
            extension
    }

    override fun onDestroy() {

        stopNetworking()

        super.onDestroy()
    }

    private fun requestOverlayPermission() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    override fun onNewIntent(
        intent: Intent,
        caller: ComponentCaller
    ) {
        super.onNewIntent(intent, caller)

        if (
            intent.getBooleanExtra(
                "MINIMIZE_IU",
                false
            )
        ) {
            moveTaskToBack(true)
        }
    }
}

@Composable
private fun DeviceRow(
    device: IUDevice,
    fontFamily: FontFamily,
    enabled: Boolean,
    onClick: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(
                        alpha =
                            if (enabled) {
                                0.08f
                            } else {
                                0.04f
                            }
                    ),
                    RoundedCornerShape(12.dp)
                )
                .clickable(
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = 10.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = "●",
            color =
                if (enabled) {
                    Color.White
                } else {
                    Color.Gray
                },
            fontSize = 10.sp
        )

        Column(
            modifier =
                Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
        ) {

            Text(
                text = device.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight =
                    FontWeight.Medium,
                fontFamily =
                    fontFamily
            )

            Text(
                text =
                    "${device.host.hostAddress}:${device.port}",
                color =
                    Color.White.copy(
                        alpha = 0.45f
                    ),
                fontSize = 10.sp,
                fontFamily =
                    fontFamily
            )
        }

        Text(
            text = "›",
            color =
                Color.White.copy(
                    alpha = 0.5f
                ),
            fontSize = 22.sp
        )
    }
}