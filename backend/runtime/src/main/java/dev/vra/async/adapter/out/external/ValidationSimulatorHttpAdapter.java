package dev.vra.async.adapter.out.external;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import dev.vra.async.application.external.ExternalEffectPort;
import dev.vra.async.contract.ReservationCreatedEventV1.Payload;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** POC-only HTTP boundary. Ambiguous transport results remain UNKNOWN. */
public final class ValidationSimulatorHttpAdapter implements ExternalEffectPort {
    private final HttpClient client;
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper json = JsonMapper.builder().build();

    public ValidationSimulatorHttpAdapter(URI baseUri, Duration timeout) {
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (!"http".equals(baseUri.getScheme()) || baseUri.getHost() == null
                || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("A simulator HTTP URI and positive timeout are required");
        }
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public ExecuteOutcome execute(UUID eventId, Payload payload) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(payload, "payload");
        ObjectNode root = json.createObjectNode();
        root.put("eventId", eventId.toString());
        ObjectNode body = json.createObjectNode();
        body.put("reservationId", payload.reservationId().toString());
        body.put("skuId", payload.skuId().toString());
        body.put("ownerId", payload.ownerId().toString());
        body.put("locationId", payload.locationId().toString());
        body.put("stockStatus", payload.stockStatus().name());
        body.put("quantity", payload.quantity());
        root.set("payload", body);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/execute"))
                .timeout(timeout).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(root))).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 409) return ExecuteOutcome.CONFLICT;
            if (response.statusCode() == 202) return ExecuteOutcome.UNKNOWN_OUTCOME;
            if (response.statusCode() == 200 && "SUCCEEDED".equals(result(response.body()))) {
                return ExecuteOutcome.CONFIRMED_SUCCEEDED;
            }
            if (response.statusCode() >= 500) return ExecuteOutcome.UNKNOWN_OUTCOME;
            throw new IllegalStateException("Unexpected simulator execute response: " + response.statusCode());
        } catch (IOException error) {
            return ExecuteOutcome.UNKNOWN_OUTCOME;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return ExecuteOutcome.UNKNOWN_OUTCOME;
        }
    }

    @Override
    public Observation observe(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId");
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/operations/" + eventId))
                .timeout(timeout).GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Observation.valueOf(result(response.body()));
            }
            if (response.statusCode() >= 500) return Observation.INDETERMINATE;
            throw new IllegalStateException("Unexpected simulator observe response: " + response.statusCode());
        } catch (IOException error) {
            return Observation.INDETERMINATE;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return Observation.INDETERMINATE;
        }
    }

    private String result(String body) {
        JsonNode node = json.readTree(body);
        JsonNode value = node == null ? null : node.get("result");
        if (value == null || !value.isTextual()) {
            throw new IllegalStateException("Simulator response has no result code");
        }
        return value.asText();
    }
}
