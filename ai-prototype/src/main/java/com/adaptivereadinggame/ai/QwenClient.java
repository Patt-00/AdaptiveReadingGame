package com.adaptivereadinggame.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/** Sends only a Java-created numeric prompt; never student identities or raw answers. */
public final class QwenClient implements DifficultyService.ModelTool {
    public static final String MODEL_ID = "Qwen/Qwen2.5-0.5B-Instruct";
    public static final String REVISION = "7ae557604adf67be50417f59c2c2f167def9a775";
    private final URI endpoint;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Gson gson = new Gson();
    public QwenClient(URI endpoint) {
        String host = endpoint.getHost();
        if (!"http".equals(endpoint.getScheme()) || !("127.0.0.1".equals(host) || "localhost".equals(host))
                || endpoint.getUserInfo() != null || endpoint.getFragment() != null || !"/infer".equals(endpoint.getPath()))
            throw new IllegalArgumentException("Use a local http://127.0.0.1:<port>/infer endpoint");
        this.endpoint = endpoint;
    }
    @Override public DifficultyService.Action infer(DifficultyService.Prompt prompt) throws IOException, InterruptedException {
        JsonObject payload = new JsonObject(); payload.addProperty("instruction", prompt.instruction());
        payload.addProperty("input", prompt.input());
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload))).build();
        // The deadline covers the entire response, not just arrival of headers.
        var future = http.sendAsync(request, ignored -> new BoundedBody());
        HttpResponse<byte[]> response;
        try { response = future.get(45, java.util.concurrent.TimeUnit.SECONDS); }
        catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException failure) {
            future.cancel(true); throw new IOException("Model request failed or timed out", failure);
        } catch (InterruptedException interrupted) { future.cancel(true); throw interrupted; }
        byte[] bytes = response.body();
        if (response.statusCode() != 200) throw new IOException("Invalid model service response");
        try {
            JsonObject json = gson.fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
            if (!MODEL_ID.equals(json.get("model_id").getAsString()) || !REVISION.equals(json.get("revision").getAsString()))
                throw new IllegalArgumentException("Unexpected model");
            return DifficultyService.Action.valueOf(json.get("suggested_action").getAsString().toUpperCase(Locale.ROOT));
        } catch (RuntimeException malformed) { throw new IOException("Invalid model suggestion", malformed); }
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final java.util.concurrent.CompletableFuture<byte[]> result = new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;
        @Override public java.util.concurrent.CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(java.util.concurrent.Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 8192 - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IOException("Model response too large")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
