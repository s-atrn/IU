class IUReceiveController:
    def __init__(self):
        self.active = False
        self.port = -1
        self.current_filename = None
        self.current_received = 0
        self.current_total = 0

    def started(self, port):
        self.active = True
        self.port = port

    def stopped(self):
        self.active = False
        self.port = -1
        self.current_filename = None
        self.current_received = 0
        self.current_total = 0

    def progress(
        self,
        filename,
        received,
        total,
    ):
        self.current_filename = filename
        self.current_received = received
        self.current_total = total

    def file_received(
        self,
        filename,
        path,
    ):
        self.current_filename = filename
        self.current_received = 0
        self.current_total = 0