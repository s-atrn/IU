import os
import sys
import threading
import tkinter as tk

from core.acceptor import IUAcceptor
from core.discovery import IUDiscovery
from core.sender import IUSender

from ui.send import IUSendController
from ui.receive import IUReceiveController
from ui.window import IUWindow
from ui.tray import IUTray


class IU:

    def __init__(
        self,
        initial_files=None,
    ):
        print("[IU] Starting IU...")

        self.root = tk.Tk()
        self.root.withdraw()

        self.shutting_down = False

        self.initial_files = (
            self._clean_files(
                initial_files or []
            )
        )

        self.receive_controller = (
            IUReceiveController()
        )

        self.acceptor = IUAcceptor(
            self._on_acceptor_started,
            self._on_acceptor_stopped,
            self._on_file_received,
            self._on_receive_progress,
            self._on_acceptor_error,
        )

        self.discovery = IUDiscovery(
            self._on_devices_changed
        )

        self.sender = IUSender(
            self._on_file_started,
            self._on_progress,
            self._on_file_finished,
            self._on_complete,
            self._on_sender_error,
        )

        self.send_controller = (
            IUSendController(
                self.sender
            )
        )

        self.window = IUWindow(
            self.root,
            self.discovery,
            self.send_controller,
            self.receive_controller,
            self.acceptor,
        )

        if self.initial_files:
            self.window.set_files(
                self.initial_files
            )

        self.tray = IUTray(
            self.acceptor,
            self.window,
            self.shutdown,
        )

    # ==============================================================
    # START
    # ==============================================================

    def start(self):
        print(
            "[IU] IU is running in the system tray."
        )

        self.discovery.start()

        tray_thread = threading.Thread(
            target=self._start_tray,
            daemon=True,
        )

        tray_thread.start()

        if self.initial_files:
            self.root.after(
                100,
                self.window.show,
            )

        self.root.mainloop()

    def _start_tray(self):
        print("[IU] Tray started.")
        self.tray.start()

    # ==============================================================
    # FILE ARGUMENTS
    # ==============================================================

    @staticmethod
    def _clean_files(
        files,
    ):
        cleaned = []

        for path in files:
            try:
                path = os.path.abspath(
                    os.path.expanduser(
                        str(path)
                    )
                )
            except Exception:
                continue

            if os.path.isfile(path):
                cleaned.append(path)

        return cleaned

    # ==============================================================
    # ACCEPTOR
    # ==============================================================

    def _on_acceptor_started(
        self,
        port,
    ):
        print(
            f"[IU] Acceptor active on port {port}."
        )

        try:
            self.discovery.set_service_port(
                port
            )
        except Exception:
            pass

        self.root.after(
            0,
            self.window._draw,
        )

    def _on_acceptor_stopped(self):
        print(
            "[IU] Acceptor stopped."
        )

        self.root.after(
            0,
            self.window._draw,
        )

    def _on_acceptor_error(
        self,
        error,
        filename=None,
    ):
        print(
            f"[IU] Error: {error}"
        )

        self.root.after(
            0,
            lambda:
            self.window.on_error(
                error
            ),
        )

    def _on_file_received(
        self,
        filename,
        path,
    ):
        print(
            f"[IU] File received: {filename}"
        )

        self.root.after(
            0,
            lambda:
            self.window.on_file_received(
                filename,
                path,
            ),
        )

        self.root.after(
            0,
            self.window._draw,
        )

    def _on_receive_progress(
        self,
        filename,
        received,
        total,
    ):
        self.root.after(
            0,
            lambda:
            self.window.on_receive_progress(
                filename,
                received,
                total,
            ),
        )

    # ==============================================================
    # DISCOVERY
    # ==============================================================

    def _on_devices_changed(
        self,
        devices,
    ):
        self.root.after(
            0,
            lambda:
            self.window.set_devices(
                devices
            ),
        )

    # ==============================================================
    # SENDER
    # ==============================================================

    def _on_file_started(
        self,
        filename,
        file_index=None,
        total_files=None,
    ):
        self.root.after(
            0,
            lambda:
            self.window.on_file_started(
                filename,
                file_index,
                total_files,
            ),
        )

    def _on_progress(
        self,
        filename,
        sent,
        total,
    ):
        self.root.after(
            0,
            lambda:
            self.window.on_progress(
                filename,
                sent,
                total,
            ),
        )

    def _on_file_finished(
        self,
        filename,
    ):
        pass

    def _on_complete(self):
        self.root.after(
            0,
            self.window.on_complete,
        )

    def _on_sender_error(
        self,
        error,
    ):
        self.root.after(
            0,
            lambda:
            self.window.on_error(
                error
            ),
        )

    # ==============================================================
    # SHUTDOWN
    # ==============================================================

    def shutdown(self):
        if self.shutting_down:
            return

        self.shutting_down = True

        print("[IU] Quitting.")

        def shutdown_worker():
            print(
                "[IU] Stopping discovery."
            )

            try:
                self.discovery.stop()
            except Exception:
                pass

            print(
                "[IU] Stopping acceptor."
            )

            try:
                self.acceptor.stop()
            except Exception:
                pass

            print(
                "[IU] Stopping IU."
            )

            self.root.after(
                0,
                self._finish_shutdown,
            )

        threading.Thread(
            target=shutdown_worker,
            daemon=True,
        ).start()

    def _finish_shutdown(self):
        try:
            self.tray.stop()
        except Exception:
            pass

        try:
            self.root.quit()
        except Exception:
            pass

        try:
            self.root.destroy()
        except Exception:
            pass


def main():
    initial_files = sys.argv[1:]

    app = IU(
        initial_files=initial_files
    )

    app.start()


if __name__ == "__main__":
    main()