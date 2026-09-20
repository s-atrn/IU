package com.example.iu

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.net.InetAddress
import java.net.NetworkInterface

data class IUDevice(
    val name: String,
    val host: InetAddress,
    val port: Int
)

class NetworkDiscovery(
    private val context: Context,
    private val servicePort: Int,
    private val onDevicesChanged: (List<IUDevice>) -> Unit,
    private val advertiseSelf: Boolean = true,
    private val discoverServices: Boolean = true
) {
    companion object {
        private const val SERVICE_TYPE = "_iu._tcp."
    }

    private val appContext =
        context.applicationContext

    private val nsdManager =
        appContext.getSystemService(
            Context.NSD_SERVICE
        ) as NsdManager

    private val wifiManager =
        appContext.getSystemService(
            Context.WIFI_SERVICE
        ) as WifiManager

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private var multicastLock:
        WifiManager.MulticastLock? = null

    private var registrationListener:
        NsdManager.RegistrationListener? = null

    private var discoveryListener:
        NsdManager.DiscoveryListener? = null

    private val devices =
        mutableMapOf<String, IUDevice>()

    private var running = false

    private var registeredServiceName:
        String? = null

    fun start() {
        if (running) {
            return
        }

        running = true
        devices.clear()
        notifyDevices()
        acquireMulticastLock()

        if (advertiseSelf) {
            registerService()
        }

        if (discoverServices) {
            startDiscovery()
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) {
            return
        }

        try {
            multicastLock =
                wifiManager
                    .createMulticastLock("IU-mDNS")
                    .apply {
                        setReferenceCounted(false)
                        acquire()
                    }
        } catch (_: Exception) {
            multicastLock = null
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                }
            }
        } catch (_: Exception) {
        }

        multicastLock = null
    }

    private fun registerService() {
        val deviceName =
            buildDeviceName()

        val serviceInfo =
            NsdServiceInfo().apply {
                serviceName = deviceName
                serviceType = SERVICE_TYPE
                port = servicePort
            }

        registrationListener =
            object : NsdManager.RegistrationListener {

                override fun onServiceRegistered(
                    serviceInfo: NsdServiceInfo
                ) {
                    registeredServiceName =
                        serviceInfo.serviceName
                }

                override fun onRegistrationFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int
                ) {
                }

                override fun onServiceUnregistered(
                    serviceInfo: NsdServiceInfo
                ) {
                    registeredServiceName = null
                }

                override fun onUnregistrationFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int
                ) {
                }
            }

        try {
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener!!
            )
        } catch (_: Exception) {
        }
    }

    private fun startDiscovery() {
        discoveryListener =
            object : NsdManager.DiscoveryListener {

                override fun onDiscoveryStarted(
                    serviceType: String
                ) {
                }

                override fun onServiceFound(
                    service: NsdServiceInfo
                ) {
                    if (
                        service.serviceType !=
                        SERVICE_TYPE
                    ) {
                        return
                    }

                    if (
                        service.serviceName ==
                        registeredServiceName
                    ) {
                        return
                    }

                    resolveService(service)
                }

                override fun onServiceLost(
                    service: NsdServiceInfo
                ) {
                    devices.remove(
                        service.serviceName
                    )

                    notifyDevices()
                }

                override fun onDiscoveryStopped(
                    serviceType: String
                ) {
                }

                override fun onStartDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int
                ) {
                    try {
                        nsdManager.stopServiceDiscovery(
                            this
                        )
                    } catch (_: Exception) {
                    }
                }

                override fun onStopDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int
                ) {
                }
            }

        try {
            nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener!!
            )
        } catch (_: Exception) {
        }
    }

    private fun resolveService(
        service: NsdServiceInfo
    ) {
        try {
            nsdManager.resolveService(
                service,
                object : NsdManager.ResolveListener {

                    override fun onResolveFailed(
                        serviceInfo: NsdServiceInfo,
                        errorCode: Int
                    ) {
                    }

                    override fun onServiceResolved(
                        resolvedService: NsdServiceInfo
                    ) {
                        if (!running) {
                            return
                        }

                        val host =
                            resolvedService.host
                                ?: return

                        if (isLocalAddress(host)) {
                            return
                        }

                        val port =
                            resolvedService.port

                        if (port <= 0) {
                            return
                        }

                        val device =
                            IUDevice(
                                name =
                                    resolvedService.serviceName,
                                host = host,
                                port = port
                            )

                        devices[
                            deviceKey(device)
                        ] = device

                        notifyDevices()
                    }
                }
            )
        } catch (_: Exception) {
        }
    }

    private fun isLocalAddress(
        address: InetAddress
    ): Boolean {
        return try {
            NetworkInterface
                .getNetworkInterfaces()
                ?.asSequence()
                ?.flatMap { networkInterface ->
                    networkInterface
                        .inetAddresses
                        .asSequence()
                }
                ?.any { localAddress ->
                    localAddress.hostAddress ==
                        address.hostAddress
                } == true
        } catch (_: Exception) {
            false
        }
    }

    private fun deviceKey(
        device: IUDevice
    ): String {
        return "${device.host.hostAddress}:${device.port}"
    }

    private fun buildDeviceName(): String {
        return Settings.Global.getString(
            appContext.contentResolver,
            Settings.Global.DEVICE_NAME
        )?.takeIf { it.isNotBlank() }
            ?: android.os.Build.MODEL
    }

    private fun notifyDevices() {
        val snapshot =
            devices.values
                .sortedBy { it.name }
                .toList()

        mainHandler.post {
            onDevicesChanged(snapshot)
        }
    }

    fun stop() {
        if (!running) {
            return
        }

        running = false

        try {
            discoveryListener?.let {
                nsdManager.stopServiceDiscovery(it)
            }
        } catch (_: Exception) {
        }

        try {
            registrationListener?.let {
                nsdManager.unregisterService(it)
            }
        } catch (_: Exception) {
        }

        discoveryListener = null
        registrationListener = null
        registeredServiceName = null

        devices.clear()
        notifyDevices()

        releaseMulticastLock()
    }
}