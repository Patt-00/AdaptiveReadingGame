package com.adaptivereadinggame.ai;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class QwenClientTest {
    private String valid(String action) {
        return "{\"suggested_action\":\"" + action + "\",\"model_id\":\"" + QwenClient.MODEL_ID
                + "\",\"revision\":\"" + QwenClient.REVISION + "\"}";
    }
    private void withResponse(int status, String body, boolean expectSuccess) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/infer", exchange -> {
            var json = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(2, json.size()); assertEquals("Java-created instruction", json.get("instruction").getAsString());
            assertEquals("Java-created input", json.get("input").getAsString());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var client = new QwenClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/infer"));
            var prompt = new DifficultyService.Prompt("Java-created instruction", "Java-created input");
            if (expectSuccess) assertEquals(DifficultyService.Action.KEEP, client.infer(prompt));
            else assertThrows(java.io.IOException.class, () -> client.infer(prompt));
        } finally { server.stop(0); }
    }
    @Test void acceptsActualContract() throws Exception { withResponse(200, valid("keep"), true); }
    @Test void rejectsInvalidLabel() throws Exception { withResponse(200, valid("hard"), false); }
    @Test void rejectsMalformedJson() throws Exception { withResponse(200, "not json", false); }
    @Test void rejectsWrongModel() throws Exception { withResponse(200, valid("keep").replace(QwenClient.MODEL_ID, "other"), false); }
    @Test void rejectsUnavailableService() throws Exception { withResponse(503, "{}", false); }
    @Test void rejectsOversizedResponse() throws Exception { withResponse(200, "x".repeat(9000), false); }
    @Test void rejectsExternalEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> new QwenClient(URI.create("http://example.com/infer")));
    }
}
