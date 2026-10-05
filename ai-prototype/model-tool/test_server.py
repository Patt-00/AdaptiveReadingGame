"""HTTP contract tests with a fake scorer. These do not prove model quality."""
import json
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from server import Handler, ModelTool


class ContractTests(unittest.TestCase):
    def setUp(self):
        self.tool = ModelTool()
        self.tool.loading = False
        self.tool.scorer = lambda instruction, input_text: "keep"
        handler = type("TestHandler", (Handler,), {"tool": self.tool, "log_message": lambda *args: None})
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def post(self, payload, content_type="application/json", path="/infer"):
        request = urllib.request.Request(self.url + path, data=json.dumps(payload).encode(),
                                         headers={"Content-Type": content_type})
        try:
            with urllib.request.urlopen(request, timeout=3) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as error:
            with error:
                return error.code, json.load(error)

    def prompt(self):
        return {"instruction": "Return one label", "input": "Return keep"}

    def test_prompt_only_contract(self):
        status, response = self.post(self.prompt())
        self.assertEqual(status, 200)
        self.assertEqual(response["suggested_action"], "keep")
        self.assertNotIn("next_difficulty", response)

    def test_rejects_game_history_and_extra_fields(self):
        self.assertEqual(self.post({**self.prompt(), "history": []})[0], 400)
        self.assertEqual(self.post({"current_difficulty": "medium", "history": []})[0], 400)

    def test_invalid_prompt(self):
        for value in (None, 1, "", "x" * 2001):
            self.assertEqual(self.post({**self.prompt(), "input": value})[0], 400)

    def test_unavailable_or_busy(self):
        self.tool.scorer = None
        self.assertEqual(self.post(self.prompt())[0], 503)
        self.tool.scorer = lambda instruction, input_text: "keep"
        self.tool.lock.acquire()
        try:
            self.assertEqual(self.post(self.prompt())[0], 503)
        finally:
            self.tool.lock.release()

    def test_failed_inference_is_not_a_game_fallback(self):
        self.tool.scorer = lambda instruction, input_text: "invalid"
        self.assertEqual(self.post(self.prompt())[0], 503)

    def test_routes_and_content_type(self):
        self.assertEqual(self.post(self.prompt(), "text/plain")[0], 415)
        self.assertEqual(self.post(self.prompt(), path="/recommend")[0], 404)


if __name__ == "__main__":
    unittest.main()
