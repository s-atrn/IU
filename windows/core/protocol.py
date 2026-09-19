import socket
import struct
from pathlib import Path


BUFFER_SIZE = 64 * 1024
MAX_FILENAME_LENGTH = 4096
CONNECT_TIMEOUT = 5
ACK_BYTE = b"\x06"


def send_file(
    host,
    port,
    file_path,
    progress_callback=None,
):
    path = Path(file_path)

    if not path.exists():
        raise FileNotFoundError(str(path))

    if not path.is_file():
        raise ValueError(f"Not a file: {path}")

    filename = path.name
    filename_bytes = filename.encode("utf-8")

    if not filename_bytes:
        raise ValueError("Filename is empty.")

    if len(filename_bytes) > MAX_FILENAME_LENGTH:
        raise ValueError(
            f"Filename is too long: {filename}"
        )

    file_size = path.stat().st_size

    with socket.socket(
        socket.AF_INET,
        socket.SOCK_STREAM,
    ) as sock:

        sock.settimeout(CONNECT_TIMEOUT)

        sock.connect(
            (
                host,
                port,
            )
        )

        sock.settimeout(None)

        sock.setsockopt(
            socket.IPPROTO_TCP,
            socket.TCP_NODELAY,
            1,
        )

        sock.sendall(
            struct.pack(
                "!I",
                len(filename_bytes),
            )
        )

        sock.sendall(
            filename_bytes
        )

        sock.sendall(
            struct.pack(
                "!Q",
                file_size,
            )
        )

        sent = 0

        with path.open("rb") as file:
            while True:
                chunk = file.read(BUFFER_SIZE)

                if not chunk:
                    break

                sock.sendall(chunk)

                sent += len(chunk)

                if progress_callback is not None:
                    progress_callback(
                        filename,
                        sent,
                        file_size,
                    )

        sock.shutdown(socket.SHUT_WR)

        acknowledgement = sock.recv(1)

        if acknowledgement != ACK_BYTE:
            raise ConnectionError(
                "IU device did not acknowledge the completed file."
            )


def read_exact(stream, size):
    data = bytearray()

    while len(data) < size:
        chunk = stream.read(
            size - len(data)
        )

        if not chunk:
            raise ConnectionError(
                "Connection closed unexpectedly."
            )

        data.extend(chunk)

    return bytes(data)


def read_uint32(stream):
    return struct.unpack(
        "!I",
        read_exact(stream, 4),
    )[0]


def read_uint64(stream):
    return struct.unpack(
        "!Q",
        read_exact(stream, 8),
    )[0]


def sanitize_filename(filename):
    invalid_characters = '<>:"/\\|?*'

    clean = "".join(
        "_"
        if character in invalid_characters
        else character
        for character in filename
    )

    clean = clean.strip()

    if not clean:
        return "received_file"

    return clean