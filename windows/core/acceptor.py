import os
import socket
import threading
from pathlib import Path

from zeroconf import (
    ServiceInfo,
    Zeroconf,
)

from .protocol import (
    ACK_BYTE,
    BUFFER_SIZE,
    MAX_FILENAME_LENGTH,
    read_uint32,
    read_uint64,
    read_exact,
    sanitize_filename,
)


SERVICE_TYPE = "_iu._tcp.local."

SERVICE_PROPERTIES = {
    b"device": b"PC",
}

RECEIVED_DIRECTORY = (
    Path.home()
    / "Downloads"
)


class IUAcceptor:

    def __init__(
        self,
        on_started=None,
        on_stopped=None,
        on_file_received=None,
        on_progress=None,
        on_error=None,
    ):
        self.on_started = (
            on_started
            or (lambda port: None)
        )

        self.on_stopped = (
            on_stopped
            or (lambda: None)
        )

        self.on_file_received = (
            on_file_received
            or (lambda filename, path: None)
        )

        self.on_progress = (
            on_progress
            or (
                lambda filename, received, total: None
            )
        )

        self.on_error = (
            on_error
            or (
                lambda error, filename=None: None
            )
        )

        self.server_socket = None
        self.zeroconf = None
        self.service_info = None

        self.running = False

        self._server_thread = None
        self._lock = threading.Lock()
        self._path_lock = threading.Lock()

    @property
    def port(self):

        if self.server_socket is None:
            return -1

        try:
            return self.server_socket.getsockname()[1]

        except OSError:
            return -1

    def start(self):

        with self._lock:

            if self.running:
                return

            self.running = True

        try:

            RECEIVED_DIRECTORY.mkdir(
                parents=True,
                exist_ok=True,
            )

            self.server_socket = socket.socket(
                socket.AF_INET,
                socket.SOCK_STREAM,
            )

            self.server_socket.setsockopt(
                socket.SOL_SOCKET,
                socket.SO_REUSEADDR,
                1,
            )

            self.server_socket.bind(
                ("0.0.0.0", 0)
            )

            self.server_socket.listen(16)

            self._register_service()

            self._server_thread = threading.Thread(
                target=self._accept_loop,
                name="IU-Acceptor",
                daemon=True,
            )

            self._server_thread.start()

            print(
                f"[IU] Acceptor active on port {self.port}",
                flush=True,
            )

            self.on_started(
                self.port
            )

        except Exception as error:

            self.running = False

            self._unregister_service()
            self._cleanup_socket()

            self.on_error(error)

    def stop(self):

        with self._lock:

            if not self.running:
                return

            self.running = False

        print(
            "[IU] Stopping acceptor.",
            flush=True,
        )

        self._cleanup_socket()
        self._unregister_service()

        self.on_stopped()

    def _accept_loop(self):

        while self.running:

            try:

                client_socket, address = (
                    self.server_socket.accept()
                )

                print(
                    f"[IU] Connection from "
                    f"{address[0]}:{address[1]}",
                    flush=True,
                )

                thread = threading.Thread(
                    target=self._handle_client,
                    args=(client_socket,),
                    name="IU-Transfer",
                    daemon=True,
                )

                thread.start()

            except OSError as error:

                if self.running:
                    self.on_error(error)

                break

            except Exception as error:

                if self.running:
                    self.on_error(error)

    def _handle_client(
        self,
        client_socket,
    ):

        filename = None
        output_path = None

        try:

            client_socket.setsockopt(
                socket.IPPROTO_TCP,
                socket.TCP_NODELAY,
                1,
            )

            input_stream = (
                client_socket.makefile("rb")
            )

            output_stream = (
                client_socket.makefile("wb")
            )

            filename_length = read_uint32(
                input_stream
            )

            if (
                filename_length <= 0
                or filename_length > MAX_FILENAME_LENGTH
            ):

                raise ValueError(
                    "Invalid filename length."
                )

            filename_bytes = read_exact(
                input_stream,
                filename_length,
            )

            filename = filename_bytes.decode(
                "utf-8",
                errors="replace",
            )

            filename = sanitize_filename(
                filename
            )

            file_size = read_uint64(
                input_stream
            )

            output_path = (
                self._get_unique_path(
                    filename
                )
            )

            print(
                f"[IU] Receiving "
                f"{output_path.name} "
                f"({file_size} bytes)",
                flush=True,
            )

            total_received = 0

            with output_path.open("wb") as output_file:

                while total_received < file_size:

                    remaining = (
                        file_size
                        - total_received
                    )

                    bytes_to_read = min(
                        BUFFER_SIZE,
                        remaining,
                    )

                    data = input_stream.read(
                        bytes_to_read
                    )

                    if not data:

                        raise ConnectionError(
                            "Connection closed before "
                            "the file was completely "
                            "received."
                        )

                    output_file.write(data)

                    total_received += len(data)

                    self.on_progress(
                        filename,
                        total_received,
                        file_size,
                    )

                output_file.flush()

            output_stream.write(
                ACK_BYTE
            )

            output_stream.flush()

            print(
                f"[IU] Received: {output_path}",
                flush=True,
            )

            self.on_file_received(
                output_path.name,
                output_path,
            )

        except Exception as error:

            print(
                f"[IU] Receive error: {error!r}",
                flush=True,
            )

            if output_path is not None:

                try:

                    if output_path.exists():
                        output_path.unlink()

                except OSError:
                    pass

            self.on_error(
                error,
                filename,
            )

        finally:

            try:
                client_socket.close()

            except OSError:
                pass

    def _register_service(self):

        device_name = socket.gethostname()
        service_name = (
            f"{device_name}."
            f"{SERVICE_TYPE}"
        )
    
        local_ip = (
            self._get_local_ip()
        )

        if not local_ip:

            raise RuntimeError(
                "Unable to determine "
                "local network address."
            )

        print(
            f"[IU] Registering service: "
            f"{service_name}",
            flush=True,
        )

        self.service_info = ServiceInfo(
            type_=SERVICE_TYPE,
            name=service_name,
            addresses=[
                socket.inet_aton(local_ip)
            ],
            port=self.port,
            properties=SERVICE_PROPERTIES,
        )

        self.zeroconf = Zeroconf()

        self.zeroconf.register_service(
            self.service_info
        )

        print(
            "[IU] Zeroconf registration successful.",
            flush=True,
        )

    def _unregister_service(self):

        if self.zeroconf is None:
            return

        try:

            if self.service_info is not None:

                self.zeroconf.unregister_service(
                    self.service_info
                )

        except Exception:
            pass

        try:
            self.zeroconf.close()

        except Exception:
            pass

        self.zeroconf = None
        self.service_info = None

    def _cleanup_socket(self):

        if self.server_socket is None:
            return

        try:

            self.server_socket.shutdown(
                socket.SHUT_RDWR
            )

        except OSError:
            pass

        try:
            self.server_socket.close()

        except OSError:
            pass

        self.server_socket = None

    @staticmethod
    def _get_local_ip():

        sock = socket.socket(
            socket.AF_INET,
            socket.SOCK_DGRAM,
        )

        try:

            sock.connect(
                ("8.8.8.8", 80)
            )

            return sock.getsockname()[0]

        except OSError:

            return None

        finally:

            sock.close()

    def _get_unique_path(
        self,
        filename,
    ):

        with self._path_lock:

            base_path = (
                RECEIVED_DIRECTORY
                / filename
            )

            if not base_path.exists():
                return base_path

            stem = base_path.stem
            suffix = base_path.suffix
            counter = 1

            while True:

                candidate = (
                    RECEIVED_DIRECTORY
                    / (
                        f"{stem} "
                        f"({counter})"
                        f"{suffix}"
                    )
                )

                if not candidate.exists():
                    return candidate

                counter += 1