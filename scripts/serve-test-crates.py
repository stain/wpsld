#!/usr/bin/env python3
"""Serve the test crates over HTTP, to try opening crates from a URL.

    python scripts/serve-test-crates.py [port]      (default 8000)

http://localhost:PORT/ serves ui/src/test/resources, so:

  /example-crate/ro-crate-metadata.json   an attached crate on the web
  /example-crate/                         a folder: the crate is beside it
  /doi/example                            redirects to the attached crate
  /web-crates/detached/example-ro-crate-metadata.json
                                          a detached crate (another name)
  /api/crate?id=example                   the same, from a service
                                          (a query: detached)
  /signposted/                            an HTML page whose Link header
                                          leads to the attached crate

It listens on 0.0.0.0 so that it is reachable from published ports; it
serves only that folder, read-only.
"""
import functools
import http.server
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent / "ui" / "src" / "test" / "resources"
REDIRECTS = {"/doi/example": "/example-crate/ro-crate-metadata.json"}
REWRITES = {"/api/crate": "/web-crates/detached/example-ro-crate-metadata.json"}


class Handler(http.server.SimpleHTTPRequestHandler):
    def send_head(self):
        path, _, _ = self.path.partition("?")
        if path in REDIRECTS:
            self.send_response(302)
            self.send_header("Location", REDIRECTS[path])
            self.send_header("Content-Length", "0")
            self.end_headers()
            return None
        if path == "/signposted/":
            body = b"<html><body>A landing page, not the crate</body></html>"
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.send_header("Link", '</example-crate/ro-crate-metadata.json>; rel="describedby"; '
                             'type="application/ld+json"; profile="https://w3id.org/ro/crate"')
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return None
        if path in REWRITES:
            self.path = REWRITES[path]
        return super().send_head()

    def guess_type(self, path):
        if str(path).endswith((".json", ".jsonld")):
            return "application/ld+json"
        return super().guess_type(path)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    handler = functools.partial(Handler, directory=str(ROOT))
    with http.server.ThreadingHTTPServer(("0.0.0.0", port), handler) as server:
        print(f"Serving {ROOT} on http://localhost:{port}/")
        server.serve_forever()
