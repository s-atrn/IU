from pathlib import Path

from .protocol import send_file


class IUSender:
    def __init__(
        self,
        on_progress=None,
        on_file_started=None,
        on_file_finished=None,
        on_complete=None,
        on_error=None,
    ):
        self.on_progress = (
            on_progress
            or (
                lambda filename, sent, total: None
            )
        )

        self.on_file_started = (
            on_file_started
            or (lambda filename, index, total: None)
        )

        self.on_file_finished = (
            on_file_finished
            or (lambda filename: None)
        )

        self.on_complete = (
            on_complete
            or (lambda: None)
        )

        self.on_error = (
            on_error
            or (lambda error: None)
        )

        self.sending = False

    def send(
        self,
        device,
        files,
    ):
        if self.sending:
            raise RuntimeError(
                "A transfer is already running."
            )

        files = [
            Path(file_path)
            for file_path in files
        ]

        if not files:
            raise ValueError(
                "No files selected."
            )

        self.sending = True

        try:
            total_files = len(files)

            for index, path in enumerate(
                files,
                start=1,
            ):
                if not path.exists():
                    raise FileNotFoundError(
                        str(path)
                    )

                if not path.is_file():
                    raise ValueError(
                        f"Not a file: {path}"
                    )

                self.on_file_started(
                    path.name,
                    index,
                    total_files,
                )

                print(
                    f"[IU] Sending to {device.host}:{device.port}",
                    flush=True,
                )

                send_file(
                    device.host,
                    device.port,
                    path,
                    self.on_progress,
                )

                self.on_file_finished(
                    path.name
                )

            self.on_complete()

        except Exception as error:
            self.on_error(error)

        finally:
            self.sending = False