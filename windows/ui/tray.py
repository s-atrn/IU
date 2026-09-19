import pystray

from PIL import Image
from PIL import ImageDraw
from PIL import ImageFont


class IUTray:

    def __init__(
        self,
        acceptor,
        window,
        quit_callback,
    ):
        self.acceptor = acceptor
        self.window = window
        self.quit_callback = quit_callback
        self.icon = None

    def _create_icon(self):
        image = Image.new(
            "RGBA",
            (64, 64),
            (0, 0, 0, 0),
        )

        draw = ImageDraw.Draw(
            image
        )

        try:
            font = ImageFont.truetype(
                "arialbd.ttf",
                48,
            )
        except OSError:
            font = ImageFont.truetype(
                "C:/Windows/Fonts/arialbd.ttf",
                48,
            )

        draw.text(
            (32, 32),
            "IU",
            font=font,
            fill=(255, 255, 255, 255),
            anchor="mm",
            stroke_width=1,
            stroke_fill=(255, 255, 255, 255),
        )

        return image

    def _build_menu(self):
        return pystray.Menu(
            pystray.MenuItem(
                "Open IU",
                self._open_window,
                default=True,
            ),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem(
                "Quit IU",
                self._quit,
            ),
        )

    def _open_window(
        self,
        icon=None,
        item=None,
    ):
        self.window.root.after(
            0,
            self.window.show,
        )

    def _quit(
        self,
        icon=None,
        item=None,
    ):
        self.quit_callback()

    def start(self):
        self.icon = pystray.Icon(
            "IU",
            self._create_icon(),
            "IU",
            self._build_menu(),
        )

        self.icon.run()

    def stop(self):
        if self.icon is not None:
            try:
                self.icon.stop()
            except Exception:
                pass