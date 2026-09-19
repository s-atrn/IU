import threading

from zeroconf import (
    ServiceBrowser,
    ServiceListener,
    Zeroconf,
)


SERVICE_TYPE = "_iu._tcp.local."
RESOLVE_RETRIES = 5
RESOLVE_RETRY_DELAY = 0.3


class IUDevice:
    def __init__(
        self,
        name,
        host,
        port,
    ):
        self.name = name
        self.host = host
        self.port = port

    def __repr__(self):
        return (
            f"IUDevice("
            f"name={self.name!r}, "
            f"host={self.host!r}, "
            f"port={self.port!r}"
            f")"
        )


class _DiscoveryListener(ServiceListener):
    def __init__(self, discovery):
        self.discovery = discovery

    def add_service(
        self,
        zeroconf,
        service_type,
        name,
    ):
        self._resolve(
            zeroconf,
            name,
        )

    def update_service(
        self,
        zeroconf,
        service_type,
        name,
    ):
        self._resolve(
            zeroconf,
            name,
        )

    def remove_service(
        self,
        zeroconf,
        service_type,
        name,
    ):
        self.discovery._remove_service(name)

    def _resolve(
        self,
        zeroconf,
        name,
        attempt=0,
    ):
        try:
            info = zeroconf.get_service_info(
                SERVICE_TYPE,
                name,
                timeout=2000,
            )

            if info is not None:
                properties = info.properties

                device_type = properties.get(
                    b"device",
                    b"",
                )


                if device_type == b"PC":
                    return

                addresses = info.parsed_addresses()

                if addresses:
                    host = addresses[0]

                    display_name = info.name

                    suffix = SERVICE_TYPE

                    if display_name.endswith(suffix):
                        display_name = display_name[
                            :-len(suffix)
                        ]

                    device = IUDevice(
                        name=display_name,
                        host=host,
                        port=info.port,
                    )

                    self.discovery._add_service(
                        name,
                        device,
                    )

                    return

        except Exception:
            pass

        if attempt < RESOLVE_RETRIES - 1:
            timer = threading.Timer(
                RESOLVE_RETRY_DELAY,
                self._resolve,
                args=(
                    zeroconf,
                    name,
                    attempt + 1,
                ),
            )

            timer.daemon = True
            timer.start()


class IUDiscovery:
    def __init__(
        self,
        on_devices_changed=None,
    ):
        self.on_devices_changed = (
            on_devices_changed
            or (lambda devices: None)
        )

        self.zeroconf = None
        self.browser = None
        self.listener = None

        self.devices = {}

        self._lock = threading.Lock()
        self.running = False

    def start(self):
        with self._lock:
            if self.running:
                return

            self.running = True

        self.zeroconf = Zeroconf()

        self.listener = _DiscoveryListener(
            self
        )

        try:
            self.browser = ServiceBrowser(
                self.zeroconf,
                SERVICE_TYPE,
                self.listener,
            )

        except Exception:
            self.stop()
            raise

    def stop(self):
        with self._lock:
            if not self.running:
                return

            self.running = False

        try:
            if self.browser is not None:
                self.browser.cancel()
        except Exception:
            pass

        self.browser = None

        try:
            if self.zeroconf is not None:
                self.zeroconf.close()
        except Exception:
            pass

        self.zeroconf = None
        self.listener = None

        with self._lock:
            self.devices.clear()

        self._notify()

    def _add_service(
        self,
        service_name,
        device,
    ):
        with self._lock:
            if not self.running:
                return

            self.devices[service_name] = device

        self._notify()

    def _remove_service(
        self,
        service_name,
    ):
        with self._lock:
            self.devices.pop(
                service_name,
                None,
            )

        self._notify()

    def get_devices(self):
        with self._lock:
            return sorted(
                self.devices.values(),
                key=lambda device: device.name.lower(),
            )

    def _notify(self):
        devices = self.get_devices()

        try:
            self.on_devices_changed(devices)
        except Exception:
            pass