import threading


class IUSendController:
    def __init__(
        self,
        sender,
    ):
        self.sender = sender
        self.sending = False

    def send(
        self,
        device,
        files,
    ):
        if self.sending:
            return False

        self.sending = True

        thread = threading.Thread(
            target=self._send_worker,
            args=(
                device,
                files,
            ),
            daemon=True,
        )

        thread.start()

        return True

    def _send_worker(
        self,
        device,
        files,
    ):
        try:
            self.sender.send(
                device,
                files,
            )
        finally:
            self.sending = False