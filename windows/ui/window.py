import os
import subprocess
import threading
import tkinter as tk

from tkinter import filedialog

from win11toast import toast


BG = "#0c0c0e"

CARD = "#151518"
CARD_HOVER = "#1b1b1f"

TEXT = "#f2f2f3"
SECONDARY = "#929298"

BORDER = "#29292d"

SUCCESS = "#63ff7a"

TITLEBAR_HOVER = "#1a1a1d"
CLOSE_HOVER = "#c42b1c"


class IUWindow:

    def __init__(
        self,
        root,
        discovery,
        send_controller,
        receive_controller,
        acceptor,
    ):
        self.root = root
        self.discovery = discovery
        self.send_controller = send_controller
        self.receive_controller = receive_controller
        self.acceptor = acceptor

        self.root.title("IU")
        self.root.geometry("340x400")
        self.root.minsize(300, 360)
        self.root.configure(bg=BG)
        self.root.overrideredirect(True)
        self.root.attributes(
            "-topmost",
            True,
        )

        self.files = []
        self.devices = []

        self.sending = False
        self.receiving = False

        self.status_text = ""
        self.status_color = SECONDARY

        self.progress = 0.0
        self.progress_active = False

        self.drag_start_x = 0
        self.drag_start_y = 0

        self._pending_received = []
        self._received_notification_timer = None
        self._received_lock = threading.Lock()

        self.canvas = tk.Canvas(
            self.root,
            bg=BG,
            highlightthickness=0,
            bd=0,
        )

        self.canvas.pack(
            fill="both",
            expand=True,
        )

        self.canvas.bind(
            "<Configure>",
            lambda event: self._draw(),
        )

        self.root.protocol(
            "WM_DELETE_WINDOW",
            self.hide,
        )

        self._draw()

    # =========================================================
    # WINDOW
    # =========================================================

    def show(self):
        self._center_window()

        self.root.deiconify()
        self.root.lift()

        self.root.attributes(
            "-topmost",
            True,
        )

        self.root.focus_force()

        self._draw()

    def _center_window(self):
        self.root.update_idletasks()

        width = self.root.winfo_width()
        height = self.root.winfo_height()

        if width <= 1:
            width = 340

        if height <= 1:
            height = 400

        screen_width = self.root.winfo_screenwidth()
        screen_height = self.root.winfo_screenheight()

        x = (
            screen_width - width
        ) // 2

        y = (
            screen_height - height
        ) // 2

        self.root.geometry(
            f"{width}x{height}+{x}+{y}"
        )

    def hide(self):
        self.root.withdraw()

    def _minimize(self):
        self.root.withdraw()

    def _maximize(self):
        if self.root.state() == "zoomed":
            self.root.state("normal")
            self.root.geometry(
                "340x400"
            )
            self._center_window()
        else:
            self.root.state("zoomed")

        self.root.after(
            20,
            self._draw,
        )

    def _close(self):
        self.hide()

    # =========================================================
    # DRAGGING
    # =========================================================

    def _start_drag(
        self,
        event,
    ):
        self.drag_start_x = event.x_root
        self.drag_start_y = event.y_root

    def _drag(
        self,
        event,
    ):
        if self.root.state() == "zoomed":
            return

        x = (
            self.root.winfo_x()
            + event.x_root
            - self.drag_start_x
        )

        y = (
            self.root.winfo_y()
            + event.y_root
            - self.drag_start_y
        )

        self.root.geometry(
            f"+{x}+{y}"
        )

        self.drag_start_x = event.x_root
        self.drag_start_y = event.y_root

    # =========================================================
    # MAIN DRAW
    # =========================================================

    def _draw(self):
        self.canvas.delete("all")

        width = self.root.winfo_width()
        height = self.root.winfo_height()

        if width <= 1 or height <= 1:
            return

        self._draw_titlebar(width)
        self._draw_header(width)

        selector_height = 46

        selector_bottom = (
            height - 14
        )

        selector_top = (
            selector_bottom
            - selector_height
        )

        # -----------------------------------------------------
        # Progress / status area
        # -----------------------------------------------------

        status_area_bottom = (
            selector_top - 12
        )

        if self.progress_active:
            self._draw_progress_bar(
                width,
                status_area_bottom,
            )

        if self.status_text:
            status_y = (
                status_area_bottom
                - (
                    17
                    if self.progress_active
                    else 0
                )
            )

            self.canvas.create_text(
                width // 2,
                status_y,
                text=self.status_text,
                fill=self.status_color,
                font=("Segoe UI", 10),
                anchor="center",
            )

        # -----------------------------------------------------
        # Devices
        # -----------------------------------------------------

        devices_top = 91

        devices_bottom = (
            selector_top
            - (
                48
                if self.progress_active
                else 31
            )
        )

        self._draw_devices(
            width,
            devices_top,
            devices_bottom,
        )

        self._draw_selector(
            width,
            selector_top,
            selector_bottom,
        )

    # =========================================================
    # PROGRESS BAR
    # =========================================================

    def _draw_progress_bar(
        self,
        width,
        y,
    ):
        bar_left = 16
        bar_right = width - 16

        bar_height = 5

        bar_top = (
            y - bar_height
        )

        bar_bottom = y

        self._rounded_rectangle(
            bar_left,
            bar_top,
            bar_right,
            bar_bottom,
            radius=3,
            fill=BORDER,
            outline="",
            width=0,
            tags="progress_background",
        )

        progress_width = (
            bar_right
            - bar_left
        ) * max(
            0.0,
            min(
                1.0,
                self.progress,
            ),
        )

        if progress_width <= 0:
            return

        self._rounded_rectangle(
            bar_left,
            bar_top,
            bar_left + progress_width,
            bar_bottom,
            radius=3,
            fill=SUCCESS,
            outline="",
            width=0,
            tags="progress_fill",
        )

    # =========================================================
    # SEPARATE TITLE BAR
    # =========================================================

    def _draw_titlebar(
        self,
        width,
    ):
        titlebar_height = 32

        self.canvas.create_rectangle(
            0,
            0,
            width,
            titlebar_height,
            fill=BG,
            outline="",
            tags="titlebar_drag",
        )

        minimize_width = 42
        maximize_width = 42
        close_width = 42

        close_x1 = (
            width
            - close_width
        )

        maximize_x1 = (
            close_x1
            - maximize_width
        )

        minimize_x1 = (
            maximize_x1
            - minimize_width
        )

        # -----------------------------------------------------
        # Minimize
        # -----------------------------------------------------

        self.canvas.create_rectangle(
            minimize_x1,
            0,
            maximize_x1,
            titlebar_height,
            fill=BG,
            outline="",
            tags="window_minimize_bg",
        )

        self.canvas.create_line(
            minimize_x1 + 16,
            18,
            minimize_x1 + 26,
            18,
            fill=SECONDARY,
            width=1,
            tags="window_minimize_icon",
        )

        # -----------------------------------------------------
        # Maximize
        # -----------------------------------------------------

        self.canvas.create_rectangle(
            maximize_x1,
            0,
            close_x1,
            titlebar_height,
            fill=BG,
            outline="",
            tags="window_maximize_bg",
        )

        self.canvas.create_rectangle(
            maximize_x1 + 16,
            9,
            maximize_x1 + 26,
            19,
            outline=SECONDARY,
            width=1,
            tags="window_maximize_icon",
        )

        # -----------------------------------------------------
        # Close
        # -----------------------------------------------------

        self.canvas.create_rectangle(
            close_x1,
            0,
            width,
            titlebar_height,
            fill=BG,
            outline="",
            tags="window_close_bg",
        )

        close_center_x = (
            close_x1
            + close_width // 2
        )

        close_center_y = (
            titlebar_height // 2
        )

        self.canvas.create_line(
            close_center_x - 5,
            close_center_y - 5,
            close_center_x + 5,
            close_center_y + 5,
            fill=SECONDARY,
            width=1,
            tags="window_close_icon",
        )

        self.canvas.create_line(
            close_center_x + 5,
            close_center_y - 5,
            close_center_x - 5,
            close_center_y + 5,
            fill=SECONDARY,
            width=1,
            tags="window_close_icon",
        )

        # -----------------------------------------------------
        # Dragging
        # -----------------------------------------------------

        self.canvas.tag_bind(
            "titlebar_drag",
            "<ButtonPress-1>",
            self._start_drag,
        )

        self.canvas.tag_bind(
            "titlebar_drag",
            "<B1-Motion>",
            self._drag,
        )

        # -----------------------------------------------------
        # Minimize
        # -----------------------------------------------------

        self.canvas.tag_bind(
            "window_minimize_bg",
            "<Enter>",
            lambda event:
            self._window_button_hover(
                "window_minimize_bg"
            ),
        )

        self.canvas.tag_bind(
            "window_minimize_bg",
            "<Leave>",
            lambda event:
            self._window_button_leave(
                "window_minimize_bg"
            ),
        )

        self.canvas.tag_bind(
            "window_minimize_bg",
            "<Button-1>",
            lambda event:
            self._minimize(),
        )

        self.canvas.tag_bind(
            "window_minimize_icon",
            "<Enter>",
            lambda event:
            self._window_button_hover(
                "window_minimize_bg"
            ),
        )

        self.canvas.tag_bind(
            "window_minimize_icon",
            "<Leave>",
            lambda event:
            self._window_button_leave(
                "window_minimize_bg"
            ),
        )

        self.canvas.tag_bind(
            "window_minimize_icon",
            "<Button-1>",
            lambda event:
            self._minimize(),
        )

        # -----------------------------------------------------
        # Maximize
        # -----------------------------------------------------

        self.canvas.tag_bind(
            "window_maximize_bg",
            "<Enter>",
            lambda event:
            self._window_button_hover(
                "window_maximize_bg"
            ),
        )

        self.canvas.tag_bind(
            "window_maximize_bg",
            "<Leave>",
            lambda event:
            self._window_button_leave(
                "window_maximize_bg"
            ),
        )

        self.canvas.tag_bind(
            "window_maximize_bg",
            "<Button-1>",
            lambda event:
            self._maximize(),
        )

        self.canvas.tag_bind(
            "window_maximize_icon",
            "<Enter>",
            lambda event:
            self._window_button_hover(
                "window_maximize_bg"
            ),
        )

        self.canvas.tag_bind(
            "window_maximize_icon",
            "<Leave>",
            lambda event:
            self._window_button_leave(
                "window_maximize_bg"
            ),
        )

        self.canvas.tag_bind(
            "window_maximize_icon",
            "<Button-1>",
            lambda event:
            self._maximize(),
        )

        # -----------------------------------------------------
        # Close
        # -----------------------------------------------------

        self.canvas.tag_bind(
            "window_close_bg",
            "<Enter>",
            lambda event:
            self._close_hover(),
        )

        self.canvas.tag_bind(
            "window_close_bg",
            "<Leave>",
            lambda event:
            self._close_leave(),
        )

        self.canvas.tag_bind(
            "window_close_bg",
            "<Button-1>",
            lambda event:
            self._close(),
        )

        self.canvas.tag_bind(
            "window_close_icon",
            "<Enter>",
            lambda event:
            self._close_hover(),
        )

        self.canvas.tag_bind(
            "window_close_icon",
            "<Leave>",
            lambda event:
            self._close_leave(),
        )

        self.canvas.tag_bind(
            "window_close_icon",
            "<Button-1>",
            lambda event:
            self._close(),
        )

    def _window_button_hover(
        self,
        tag,
    ):
        self.root.config(
            cursor="hand2"
        )

        self.canvas.itemconfig(
            tag,
            fill=TITLEBAR_HOVER,
        )

    def _window_button_leave(
        self,
        tag,
    ):
        self.root.config(
            cursor=""
        )

        self.canvas.itemconfig(
            tag,
            fill=BG,
        )

    def _close_hover(self):
        self.root.config(
            cursor="hand2"
        )

        self.canvas.itemconfig(
            "window_close_bg",
            fill=CLOSE_HOVER,
        )

    def _close_leave(self):
        self.root.config(
            cursor=""
        )

        self.canvas.itemconfig(
            "window_close_bg",
            fill=BG,
        )

    # =========================================================
    # MAIN HEADER
    # =========================================================

    def _draw_header(
        self,
        width,
    ):
        header_y = 55

        self.canvas.create_text(
            width // 2,
            header_y,
            text="IU",
            fill=TEXT,
            font=("Segoe UI", 17, "bold"),
            anchor="center",
        )

        receiver_label_x = (
            width - 73
        )

        self.canvas.create_text(
            receiver_label_x,
            header_y,
            text="Receiver",
            fill=SECONDARY,
            font=("Segoe UI", 10),
            anchor="center",
        )

        radio_x = width - 25

        if self.acceptor.running:
            circle_character = "●"
            circle_font = (
                "Segoe UI Symbol",
                14,
            )
            circle_color = SUCCESS
        else:
            circle_character = "○"
            circle_font = (
                "Segoe UI Symbol",
                16,
            )
            circle_color = SECONDARY

        self.canvas.create_text(
            radio_x,
            header_y,
            text=circle_character,
            fill=circle_color,
            font=circle_font,
            anchor="center",
            tags="receiver_circle",
        )

        self.canvas.tag_bind(
            "receiver_circle",
            "<Enter>",
            lambda event:
            self.root.config(
                cursor="hand2"
            ),
        )

        self.canvas.tag_bind(
            "receiver_circle",
            "<Leave>",
            lambda event:
            self.root.config(
                cursor=""
            ),
        )

        self.canvas.tag_bind(
            "receiver_circle",
            "<Button-1>",
            self._toggle_receiver,
        )

        self.canvas.create_line(
            16,
            76,
            width - 16,
            76,
            fill=BORDER,
            width=1,
        )

    # =========================================================
    # DEVICES
    # =========================================================

    def _draw_devices(
        self,
        width,
        top,
        bottom,
    ):
        if bottom <= top:
            return

        card_height = 47
        gap = 7

        y = top

        for index, device in enumerate(
            self.devices
        ):
            if (
                y + card_height
                > bottom
            ):
                break

            x1 = 16
            x2 = width - 16

            y1 = y
            y2 = y + card_height

            tag = (
                f"device_{index}"
            )

            self._rounded_rectangle(
                x1,
                y1,
                x2,
                y2,
                radius=12,
                fill=CARD,
                outline=BORDER,
                width=1,
                tags=tag,
            )

            name = getattr(
                device,
                "name",
                str(device),
            )

            name = name.rstrip(".")

            self.canvas.create_text(
                x1 + 15,
                (y1 + y2) // 2,
                text=name,
                fill=TEXT,
                font=("Segoe UI", 10),
                anchor="w",
                tags=tag,
            )

            self.canvas.tag_bind(
                tag,
                "<Enter>",
                lambda event,
                tag=tag:
                self._hover_device(
                    tag
                ),
            )

            self.canvas.tag_bind(
                tag,
                "<Leave>",
                lambda event,
                tag=tag:
                self._unhover_device(
                    tag
                ),
            )

            self.canvas.tag_bind(
                tag,
                "<Button-1>",
                lambda event,
                device=device:
                self._device_clicked(
                    device
                ),
            )

            y += (
                card_height
                + gap
            )

    # =========================================================
    # FILE SELECTOR
    # =========================================================

    def _draw_selector(
        self,
        width,
        top,
        bottom,
    ):
        tag = "file_selector"

        self._rounded_rectangle(
            16,
            top,
            width - 16,
            bottom,
            radius=13,
            fill=CARD,
            outline=BORDER,
            width=1,
            tags=tag,
        )

        if not self.files:
            text = "+"
            font = (
                "Segoe UI",
                20,
            )

        elif len(self.files) == 1:
            text = os.path.basename(
                self.files[0]
            )

            if len(text) > 42:
                text = (
                    text[:39]
                    + "..."
                )

            font = (
                "Segoe UI",
                10,
            )

        else:
            text = (
                f"{len(self.files)} "
                "files selected"
            )

            font = (
                "Segoe UI",
                10,
            )

        self.canvas.create_text(
            width // 2,
            (top + bottom) // 2,
            text=text,
            fill=TEXT,
            font=font,
            anchor="center",
            tags=tag,
        )

        self.canvas.tag_bind(
            tag,
            "<Enter>",
            lambda event:
            self.root.config(
                cursor="hand2"
            ),
        )

        self.canvas.tag_bind(
            tag,
            "<Leave>",
            lambda event:
            self.root.config(
                cursor=""
            ),
        )

        self.canvas.tag_bind(
            tag,
            "<Button-1>",
            self._select_files,
        )

    # =========================================================
    # ROUNDED RECTANGLE
    # =========================================================

    def _rounded_rectangle(
        self,
        x1,
        y1,
        x2,
        y2,
        radius,
        fill,
        outline=None,
        width=1,
        tags=None,
    ):
        if x2 <= x1 or y2 <= y1:
            return

        points = [
            x1 + radius,
            y1,

            x2 - radius,
            y1,

            x2,
            y1,

            x2,
            y1 + radius,

            x2,
            y2 - radius,

            x2,
            y2,

            x2 - radius,
            y2,

            x1 + radius,
            y2,

            x1,
            y2,

            x1,
            y2 - radius,

            x1,
            y1 + radius,

            x1,
            y1,
        ]

        self.canvas.create_polygon(
            points,
            smooth=True,
            fill=fill,
            outline=outline,
            width=width,
            tags=tags,
        )

    # =========================================================
    # RECEIVER
    # =========================================================

    def _toggle_receiver(
        self,
        event=None,
    ):
        # 1. Instantly toggle state visually before background thread runs
        if self.acceptor.running:
            # If it's running, we want to stop it (so show empty circle immediately)
            # We can force a temporary visual flag or let the acceptor handle it, 
            # but let's update the drawing state right away.
            pass
        
        def worker():
            if self.acceptor.running:
                self.acceptor.stop()
            else:
                self.acceptor.start()
            
            # Redraw again once thread action fully settles
            self.root.after(0, self._draw)

        # Trigger an immediate redraw so the icon changes state *instantly* on click
        self.root.after(0, self._draw)

        threading.Thread(
            target=worker,
            daemon=True,
        ).start()

    # =========================================================
    # FILE SELECTION
    # =========================================================

    def _select_files(
        self,
        event=None,
    ):
        selected = filedialog.askopenfilenames(
            parent=self.root,
            title="Select files",
        )

        if not selected:
            return

        self.set_files(
            list(selected)
        )

    def set_files(
        self,
        files,
    ):
        valid_files = []

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
                valid_files.append(path)

        self.files = valid_files

        self.status_text = ""
        self.status_color = SECONDARY

        self.progress = 0.0
        self.progress_active = False

        self.root.after(
            0,
            self._draw,
        )

    # =========================================================
    # DEVICES
    # =========================================================

    def set_devices(
        self,
        devices,
    ):
        self.devices = list(
            devices
        )

        self._draw()

    def _device_clicked(
        self,
        device,
    ):
        if not self.files:
            return

        if self.sending:
            return

        self.send_to_device(
            device
        )

    def _hover_device(
        self,
        tag,
    ):
        items = self.canvas.find_withtag(
            tag
        )

        for item in items:
            if (
                self.canvas.type(item)
                == "polygon"
            ):
                self.canvas.itemconfig(
                    item,
                    fill=CARD_HOVER,
                )

        self.root.config(
            cursor="hand2"
        )

    def _unhover_device(
        self,
        tag,
    ):
        items = self.canvas.find_withtag(
            tag
        )

        for item in items:
            if (
                self.canvas.type(item)
                == "polygon"
            ):
                self.canvas.itemconfig(
                    item,
                    fill=CARD,
                )

        self.root.config(
            cursor=""
        )

    # =========================================================
    # SENDING
    # =========================================================

    def send_to_device(
        self,
        device,
    ):
        if not self.files:
            return

        if self.sending:
            return

        self.sending = True

        self.progress = 0.0
        self.progress_active = True

        self.status_text = (
            "Sending files…"
        )

        self.status_color = SECONDARY

        self._draw()

        files = list(
            self.files
        )

        threading.Thread(
            target=self._send_worker,
            args=(
                device,
                files,
            ),
            daemon=True,
        ).start()

    def _send_worker(
        self,
        device,
        files,
    ):
        try:
            self.send_controller.send(
                device,
                files,
            )

        except Exception as error:
            self.root.after(
                0,
                lambda error=error:
                self.on_error(error),
            )

    def on_file_started(
        self,
        filename,
        file_index=None,
        total_files=None,
    ):
        self.sending = True
        self.progress_active = True

        if file_index is not None:
            if (
                file_index == 0
                and self.progress <= 0
            ):
                self.progress = 0.0

        self.status_text = (
            "Sending files…"
        )

        self.status_color = SECONDARY

        self.root.after(
            0,
            self._draw,
        )

    def on_progress(
        self,
        filename,
        sent,
        total,
    ):
        self.sending = True
        self.progress_active = True

        if total > 0:
            self.progress = (
                sent / total
            )
        else:
            self.progress = 0.0

        self.progress = max(
            0.0,
            min(
                1.0,
                self.progress,
            ),
        )

        self.status_text = (
            "Sending files…"
        )

        self.status_color = SECONDARY

        self.root.after(
            0,
            self._draw,
        )

    def on_complete(self):
        self.sending = False
        self.files = []

        self.progress = 1.0
        self.progress_active = False

        self.status_text = "Sent"
        self.status_color = SUCCESS

        self.root.after(
            0,
            self._draw,
        )

    # =========================================================
    # RECEIVING
    # =========================================================

    def on_receive_progress(
        self,
        filename,
        received,
        total,
    ):
        self.receiving = True
        self.progress_active = True

        if total > 0:
            self.progress = (
                received / total
            )
        else:
            self.progress = 0.0

        self.progress = max(
            0.0,
            min(
                1.0,
                self.progress,
            ),
        )

        self.status_text = (
            "Receiving files…"
        )

        self.status_color = SECONDARY

        self.root.after(
            0,
            self._draw,
        )

    def on_file_received(
        self,
        filename,
        path,
    ):
        with self._received_lock:
            self.receiving = True

            self.status_text = (
                "Receiving files…"
            )

            self.status_color = SECONDARY

            self._pending_received.append(
                (
                    filename,
                    path,
                )
            )

            if (
                self._received_notification_timer
                is not None
            ):
                self._received_notification_timer.cancel()

            self._received_notification_timer = (
                threading.Timer(
                    2.0,
                    self._flush_received_notifications,
                )
            )

            self._received_notification_timer.daemon = True

            self._received_notification_timer.start()

        self.root.after(
            0,
            self._draw,
        )

    def _flush_received_notifications(
        self,
    ):
        with self._received_lock:
            received_files = list(
                self._pending_received
            )

            self._pending_received.clear()

            self._received_notification_timer = (
                None
            )

        if not received_files:
            return

        count = len(
            received_files
        )

        if count == 1:
            message = (
                "Received 1 file"
            )
        else:
            message = (
                f"Received {count} files"
            )

        first_path = (
            received_files[0][1]
        )

        folder = os.path.dirname(
            os.path.abspath(
                first_path
            )
        )

        self.receiving = False
        self.progress = 1.0
        self.progress_active = False

        self.status_text = "Received"
        self.status_color = SUCCESS

        self.root.after(
            0,
            self._draw,
        )

        threading.Thread(
            target=self._show_notification,
            args=(
                message,
                folder,
            ),
            daemon=True,
        ).start()

    def _show_notification(
        self,
        message,
        folder,
    ):
        try:

            def open_folder(
                args=None,
            ):
                try:
                    subprocess.Popen(
                        [
                            "explorer.exe",
                            folder,
                        ]
                    )

                except Exception as error:
                    print(
                        "[IU] Could not open "
                        f"folder: {error}",
                        flush=True,
                    )

            toast(
                "IU",
                message,
                on_click=open_folder,
            )

            print(
                "[IU] Notification shown: "
                f"{message}",
                flush=True,
            )

        except Exception as error:
            print(
                "[IU] Notification error: "
                f"{error!r}",
                flush=True,
            )

    # =========================================================
    # ERRORS
    # =========================================================

    def on_error(
        self,
        error,
    ):
        print(
            f"[IU] UI error: {error}",
            flush=True,
        )

        self.sending = False
        self.receiving = False

        self.progress = 0.0
        self.progress_active = False

        self.status_text = ""
        self.status_color = SECONDARY

        self.root.after(
            0,
            self._draw,
        )