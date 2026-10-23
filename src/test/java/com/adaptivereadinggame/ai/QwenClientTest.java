package com.adaptivereadinggame.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class QwenClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static String valid(String action) {
        return "{\"suggested_action\":\"" + action + "\",\"model_id\":\"" + QwenClient.MODEL_ID
                + "\",\"revision\":\"" + QwenClient.REVISION + "\"}";
    }

    private static void withResponse(int status, String body, boolean expectSuccess) throws Exception {
        withResponse(status, body, "application/json", expectSuccess);
    }

    private static void withResponse(int status, String body, String contentType, boolean expectSuccess) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/infer", exchange -> {
            JsonNode json = JSON.readTree(exchange.getRequestBody().readAllBytes());
            assertEquals(2, json.size());
            assertEquals("Java-created instruction", json.get("instruction").textValue());
            assertEquals("Java-created input", json.get("input").textValue());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var client = new QwenClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/infer"));
            var prompt = new DifficultyService.Prompt("Java-created instruction", "Java-created input");
            if (expectSuccess) assertEquals(DifficultyService.Action.KEEP, client.infer(prompt));
            else assertThrows(IOException.class, () -> client.infer(prompt));
        } finally {
            server.stop(0);
        }
    }

    @Test void acceptsPinnedModelContract() throws Exception { withResponse(200, valid("keep"), true); }
    @Test void rejectsInvalidAction() throws Exception { withResponse(200, valid("hard"), false); }
    @Test void rejectsMalformedJson() throws Exception { withResponse(200, "not json", false); }
    @Test void rejectsLookalikeContentType() throws Exception {
        withResponse(200, valid("keep"), "application/jsonp", false);
    }
    @Test void rejectsWrongModel() throws Exception {
        withResponse(200, valid("keep").replace(QwenClient.MODEL_ID, "other"), false);
    }
    @Test void rejectsUnavailableService() throws Exception { withResponse(503, "{}", false); }
    @Test void rejectsOversizedResponse() throws Exception { withResponse(200, "x".repeat(9000), false); }

    @Test void rejectsRemoteEndpoints() {
        assertThrows(IllegalArgumentException.class,
                () -> new QwenClient(URI.create("http://example.com/infer")));
        assertThrows(IllegalArgumentException.class,
                () -> new QwenClient(URI.create("http://127.0.0.1:8765/infer?token=secret")));
    }
}
