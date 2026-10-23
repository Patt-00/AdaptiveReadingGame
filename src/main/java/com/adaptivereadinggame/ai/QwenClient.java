package com.adaptivereadinggame.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Sends only Java-created numeric summaries to a loopback model helper. */
public final class QwenClient implements DifficultyService.ModelTool {
    public static final String MODEL_ID = "Qwen/Qwen2.5-0.5B-Instruct";
    public static final String REVISION = "7ae557604adf67be50417f59c2c2f167def9a775";
    public static final URI DEFAULT_ENDPOINT = URI.create("http://127.0.0.1:8765/infer");
    private static final int MAX_RESPONSE_BYTES = 8192;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    private final URI endpoint;
    private final HttpClient http;
    private final ObjectMapper json;

    public QwenClient(URI endpoint) {
        this(endpoint, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), new ObjectMapper());
    }

    QwenClient(URI endpoint, HttpClient http, ObjectMapper json) {
        this.endpoint = validateEndpoint(endpoint);
        this.http = Objects.requireNonNull(http);
        this.json = Objects.requireNonNull(json);
    }

    /** `ARG_MODEL_URL` may change the local port. Remote model hosts are rejected. */
    public static QwenClient fromEnvironment() {
        String configured = System.getenv("ARG_MODEL_URL");
        URI endpoint = configured == null || configured.isBlank() ? DEFAULT_ENDPOINT : URI.create(configured);
        return new QwenClient(endpoint);
    }

    @Override public DifficultyService.Action infer(DifficultyService.Prompt prompt)
            throws IOException, InterruptedException {
        Objects.requireNonNull(prompt);
        byte[] requestBody = json.writeValueAsBytes(Map.of(
                "instruction", Objects.requireNonNull(prompt.instruction()),
                "input", Objects.requireNonNull(prompt.input())));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();
        CompletableFuture<HttpResponse<byte[]>> future = http.sendAsync(request,
                ignored -> new BoundedBodySubscriber(MAX_RESPONSE_BYTES));
        HttpResponse<byte[]> response;
        try {
            response = future.get(REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | java.util.concurrent.ExecutionException failure) {
            future.cancel(true);
            throw new IOException("Model request failed or timed out.", failure);
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw interrupted;
        }
        if (response.statusCode() != 200)
            throw new IOException("Model helper returned HTTP " + response.statusCode() + ".");
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String mediaType = contentType.split(";", 2)[0].trim();
        if (!"application/json".equalsIgnoreCase(mediaType))
            throw new IOException("Model helper returned an unsupported content type.");
        return parseAction(response.body());
    }

    private DifficultyService.Action parseAction(byte[] body) throws IOException {
        try {
            JsonNode response = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
            String modelId = textField(response, "model_id");
            String revision = textField(response, "revision");
            String action = textField(response, "suggested_action");
            if (!MODEL_ID.equals(modelId) || !REVISION.equals(revision))
                throw new IllegalArgumentException("Unexpected model identity.");
            return DifficultyService.Action.valueOf(action.toUpperCase(Locale.ROOT));
        } catch (RuntimeException malformed) {
            throw new IOException("Model helper returned an invalid response.", malformed);
        }
    }

    private static String textField(JsonNode object, String field) {
        if (object == null || !object.isObject() || object.get(field) == null || !object.get(field).isTextual())
            throw new IllegalArgumentException("Missing text field: " + field);
        return object.get(field).textValue();
    }

    private static URI validateEndpoint(URI endpoint) {
        Objects.requireNonNull(endpoint);
        String host = endpoint.getHost();
        if (!"http".equals(endpoint.getScheme())
                || !("127.0.0.1".equals(host) || "localhost".equals(host))
                || endpoint.getUserInfo() != null || endpoint.getFragment() != null
                || endpoint.getQuery() != null || !"/infer".equals(endpoint.getPath()))
            throw new IllegalArgumentException("Use a local http://127.0.0.1:<port>/infer endpoint.");
        return endpoint;
    }

    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit;
        private Flow.Subscription subscription;

        private BoundedBodySubscriber(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("Model response exceeds the size limit."));
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { body.completeExceptionally(error); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
