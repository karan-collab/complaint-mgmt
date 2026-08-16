# Static file server for local development, on port 5500 because that is the
# port the backend's CORS configuration allows.
#
# The no-store headers matter: with a plain `python3 -m http.server` the browser
# caches the JS and CSS hard enough that edits appear not to apply at all.
#
#   python3 scripts/dev-server.py      (run from the repo root)
import http.server
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate")
        self.send_header("Pragma", "no-cache")
        super().end_headers()


if __name__ == "__main__":
    os.chdir(ROOT)
    http.server.test(HandlerClass=NoCacheHandler, port=5500, bind="127.0.0.1")
