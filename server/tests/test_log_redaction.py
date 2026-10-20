import logging
import unittest

from server.main import _WebSocketTokenLogFilter


class AccessLogRedactionTest(unittest.TestCase):
    def test_redacts_websocket_token_from_uvicorn_http_access_record(self):
        record = logging.LogRecord(
            "uvicorn.access",
            logging.INFO,
            "server.py",
            1,
            '%s - "%s %s HTTP/%s" %d',
            ("127.0.0.1", "GET", "/ws?token=secret-token&x=1", "1.1", 101),
            None,
        )

        self.assertTrue(_WebSocketTokenLogFilter().filter(record))
        rendered = record.getMessage()
        self.assertNotIn("secret-token", rendered)
        self.assertIn("token=[REDACTED]&x=1", rendered)

    def test_redacts_websocket_token_from_uvicorn_protocol_record(self):
        record = logging.LogRecord(
            "uvicorn.error",
            logging.INFO,
            "websockets_impl.py",
            1,
            '%s - "WebSocket %s" [accepted]',
            ("127.0.0.1:12345", "/ws?token=secret-token"),
            None,
        )

        self.assertTrue(_WebSocketTokenLogFilter().filter(record))
        rendered = record.getMessage()
        self.assertNotIn("secret-token", rendered)
        self.assertIn("token=[REDACTED]", rendered)


if __name__ == "__main__":
    unittest.main()
