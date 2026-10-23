import json
import hashlib
import threading
import tempfile
import unittest
from pathlib import Path
from http.server import ThreadingHTTPServer
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from unittest.mock import patch

import server


class ContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tool = server.ModelTool()
        cls.tool.loading = False
        cls.tool.scorer = lambda instruction, input_text: "keep"
        server.Handler.tool = cls.tool
        cls.http = ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        cls.thread = threading.Thread(target=cls.http.serve_forever, daemon=True)
        cls.thread.start()
        cls.base = f"http://127.0.0.1:{cls.http.server_address[1]}"

    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown()
        cls.http.server_close()

    def request(self, payload, content_type="application/json"):
        data = json.dumps(payload).encode("utf-8")
        req = Request(self.base + "/infer", data=data, headers={"Content-Type": content_type})
        try:
            with urlopen(req, timeout=3) as response:
                return response.status, json.loads(response.read())
        except HTTPError as error:
            with error:
                return error.code, json.loads(error.read())

    def test_prompt_only_contract(self):
        status, response = self.request({"instruction": "Java rule", "input": "80%, 80%, 100%"})
        self.assertEqual(200, status)
        self.assertEqual("keep", response["suggested_action"])
        self.assertEqual(server.REVISION, response["revision"])

    def test_rejects_game_history_and_extra_fields(self):
        status, _ = self.request({"instruction": "rule", "input": "summary", "student_id": "hidden"})
        self.assertEqual(400, status)

    def test_invalid_prompt(self):
        status, _ = self.request({"instruction": "", "input": "summary"})
        self.assertEqual(400, status)

    def test_rejects_oversized_request_body(self):
        status, _ = self.request({"instruction": "rule", "input": "x" * 9000})
        self.assertEqual(413, status)

    def test_routes_and_content_type(self):
        req = Request(self.base + "/other", data=b"{}", headers={"Content-Type": "application/json"})
        try:
            urlopen(req, timeout=3)
            self.fail("The unknown route should return HTTP 404")
        except HTTPError as error:
            with error:
                self.assertEqual(404, error.code)
        status, _ = self.request({"instruction": "rule", "input": "summary"}, "text/plain")
        self.assertEqual(415, status)

    def test_unavailable_or_busy(self):
        original = self.tool.scorer
        self.tool.scorer = None
        self.assertEqual(503, self.request({"instruction": "rule", "input": "summary"})[0])
        self.tool.scorer = original
        self.tool.lock.acquire()
        try:
            self.assertEqual(503, self.request({"instruction": "rule", "input": "summary"})[0])
        finally:
            self.tool.lock.release()

    def test_bundled_model_manifest_checks_revision_size_and_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            weights = folder / "model.safetensors"
            weights.write_bytes(b"test weights")
            manifest = {
                "model_id": server.MODEL_ID,
                "revision": server.REVISION,
                "weights_file": weights.name,
                "weights_size_bytes": weights.stat().st_size,
                "weights_sha256": hashlib.sha256(weights.read_bytes()).hexdigest(),
            }
            for name in ("config.json", "generation_config.json", "merges.txt", "tokenizer.json",
                         "tokenizer_config.json", "vocab.json", "LICENSE"):
                (folder / name).write_text("fixture", encoding="utf-8")
            (folder / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
            with patch.object(server, "LOCAL_MODEL_DIRECTORY", folder), \
                    patch.object(server, "WEIGHTS_SIZE_BYTES", weights.stat().st_size), \
                    patch.object(server, "WEIGHTS_SHA256", manifest["weights_sha256"]):
                self.assertEqual(folder, server.bundled_model_directory())
                weights.write_bytes(b"changed weights")
                self.assertIsNone(server.bundled_model_directory())
                weights.write_bytes(b"test weights")
                manifest["weights_file"] = "../../etc/passwd"
                (folder / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
                self.assertIsNone(server.bundled_model_directory())

    def test_failed_inference_is_not_a_game_fallback(self):
        self.tool.scorer = lambda instruction, input_text: (_ for _ in ()).throw(RuntimeError("test"))
        self.assertEqual(503, self.request({"instruction": "rule", "input": "summary"})[0])
        self.tool.scorer = lambda instruction, input_text: "keep"


if __name__ == "__main__":
    unittest.main()
