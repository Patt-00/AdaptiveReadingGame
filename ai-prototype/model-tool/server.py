"""Generic local model tool. No scores, history, difficulty rules, or game state."""
import argparse
import json
import math
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MODEL_ID = "Qwen/Qwen2.5-0.5B-Instruct"
REVISION = "7ae557604adf67be50417f59c2c2f167def9a775"
LABELS = ("increase", "keep", "decrease")


class ModelTool:
    def __init__(self):
        self.scorer = None
        self.loading = True
        self.error = None
        self.lock = threading.Lock()

    def load(self):
        try:
            import torch
            from transformers import AutoModelForCausalLM, AutoTokenizer
            torch.set_num_threads(2)
            tokenizer = AutoTokenizer.from_pretrained(MODEL_ID, revision=REVISION)
            model = AutoModelForCausalLM.from_pretrained(
                MODEL_ID, revision=REVISION, dtype=torch.float32,
                use_safetensors=True, trust_remote_code=False,
            ).to("cpu").eval()

            def score(instruction, input_text):
                messages = [
                    {"role": "system", "content": instruction},
                    {"role": "user", "content": input_text},
                ]
                text = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
                prefix = tokenizer.encode(text, add_special_tokens=False)
                scores = {}
                with torch.inference_mode():
                    for label in LABELS:
                        completion = tokenizer.encode(label, add_special_tokens=False)
                        tokens = torch.tensor([prefix + completion], dtype=torch.long)
                        logits = model(input_ids=tokens).logits
                        log_probs = logits[0, len(prefix)-1:len(prefix)+len(completion)-1].log_softmax(dim=-1)
                        selected = log_probs.gather(1, torch.tensor(completion).unsqueeze(1)).squeeze(1)
                        scores[label] = selected.mean().item()
                if not all(math.isfinite(value) for value in scores.values()):
                    raise ValueError("Non-finite model output")
                # Model likelihood selects a label; Python does not interpret player performance.
                return max(scores, key=scores.get)

            self.scorer = score
        except Exception as error:
            self.error = type(error).__name__
            print(f"Model loading failed: {self.error}. Check dependencies, network, and free RAM.", flush=True)
        finally:
            self.loading = False

    def health(self):
        return {"model_id": MODEL_ID, "revision": REVISION, "ready": self.scorer is not None,
                "loading": self.loading, "error": self.error, "busy": self.lock.locked()}


class Handler(BaseHTTPRequestHandler):
    tool = None

    def setup(self):
        super().setup()
        self.connection.settimeout(10)

    def reply(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        self.reply(200, self.tool.health()) if self.path == "/health" else self.reply(404, {"error": "Not found"})

    def do_POST(self):
        if self.path != "/infer":
            self.reply(404, {"error": "Not found"})
            return
        if self.headers.get("Content-Type", "").split(";")[0].strip() != "application/json":
            self.reply(415, {"error": "application/json required"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 1 <= length <= 8192:
                self.reply(413, {"error": "Request size must be 1..8192 bytes"})
                return
            payload = json.loads(self.rfile.read(length))
            if not isinstance(payload, dict) or set(payload) != {"instruction", "input"}:
                raise ValueError("Only instruction and input text are accepted; game data belongs in Java")
            instruction, input_text = payload["instruction"], payload["input"]
            if any(not isinstance(value, str) or not 1 <= len(value) <= 2000 for value in (instruction, input_text)):
                raise ValueError("Each prompt part must be a string of 1..2000 characters")
        except (ValueError, UnicodeDecodeError, TimeoutError):
            self.reply(400, {"error": "Invalid prompt request"})
            return
        if self.tool.scorer is None:
            self.reply(503, {"error": "Model loading or unavailable"})
            return
        if not self.tool.lock.acquire(blocking=False):
            self.reply(503, {"error": "Model busy"})
            return
        try:
            start = time.monotonic()
            label = self.tool.scorer(instruction, input_text)
            if label not in LABELS:
                raise ValueError("Invalid model label")
            self.reply(200, {"suggested_action": label, "model_id": MODEL_ID,
                             "revision": REVISION, "elapsed_ms": round((time.monotonic()-start)*1000)})
        except Exception as error:
            self.reply(503, {"error": f"Inference failed ({type(error).__name__})"})
        finally:
            self.tool.lock.release()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    Handler.tool = ModelTool()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    server.daemon_threads = True
    threading.Thread(target=Handler.tool.load, daemon=True).start()
    print(f"Model tool: http://127.0.0.1:{args.port}/health (first run downloads model weights)", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
