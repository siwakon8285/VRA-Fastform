package dev.vra.async.simulator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.vra.async.contract.ReservationCreatedEventV1.Payload;
import dev.vra.inventory.domain.StockStatus;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Separate-JVM, test-only simulator HTTP surface. */
public final class SimulatorHttpServer implements AutoCloseable {
    private enum Gate { BLOCK, DROP }

    private final SimulatorStore store;
    private final HttpServer server;
    private final ExecutorService requests = Executors.newCachedThreadPool();
    private final ObjectMapper json = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).build();
    private final AtomicReference<Gate> nextGate = new AtomicReference<>();
    private final AtomicReference<CountDownLatch> activeBlock = new AtomicReference<>();
    private final Object admission = new Object();

    public SimulatorHttpServer(SimulatorStore store) throws IOException {
        this.store = store;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(requests);
        server.createContext("/health", this::health);
        server.createContext("/execute", this::execute);
        server.createContext("/operations/", this::observe);
        server.createContext("/test/effects/", this::effect);
        server.createContext("/test/gates/", this::gate);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }

    private void health(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        send(exchange, 200, "{\"result\":\"UP\"}");
    }

    private void execute(HttpExchange exchange) throws IOException {
        try {
            UUID operationId;
            SimulatorStore.Registration registration;
            synchronized (admission) {
                if (!method(exchange, "POST")) return;
                byte[] bytes = exchange.getRequestBody().readNBytes(4097);
                if (bytes.length > 4096) throw new IllegalArgumentException("Request too large");
                JsonNode request = json.readTree(new String(bytes, StandardCharsets.UTF_8));
                requireFields(request, Set.of("eventId", "payload"));
                operationId = uuid(request.get("eventId"));
                JsonNode fields = request.get("payload");
                requireFields(fields, Set.of("reservationId", "skuId", "ownerId", "locationId",
                        "stockStatus", "quantity"));
                JsonNode quantity = fields.get("quantity");
                if (!quantity.isIntegralNumber() || !quantity.canConvertToLong()) {
                    throw new IllegalArgumentException("Invalid quantity");
                }
                Payload payload = new Payload(uuid(fields.get("reservationId")), uuid(fields.get("skuId")),
                        uuid(fields.get("ownerId")), uuid(fields.get("locationId")),
                        StockStatus.valueOf(text(fields.get("stockStatus"))), quantity.longValue());
                registration = store.register(operationId, payload);
                try { store.recordRequest(operationId, "EXECUTE"); }
                catch (SQLException diagnosticsUnavailable) { /* Observation semantics do not depend on test evidence. */ }
            }
            switch (registration) {
                case CONFLICT -> send(exchange, 409, "{\"result\":\"CONFLICT\"}");
                case SUCCEEDED -> send(exchange, 200, "{\"result\":\"SUCCEEDED\"}");
                case PENDING -> send(exchange, 202, "{\"result\":\"INDETERMINATE\"}");
                case NEW -> {
                    Gate selected = nextGate.getAndSet(null);
                    if (selected == Gate.BLOCK) {
                        CountDownLatch latch = new CountDownLatch(1);
                        activeBlock.set(latch);
                        latch.await();
                        activeBlock.compareAndSet(latch, null);
                    }
                    store.complete(operationId);
                    if (selected == Gate.DROP) exchange.close();
                    else send(exchange, 200, "{\"result\":\"SUCCEEDED\"}");
                }
            }
        } catch (IllegalArgumentException error) {
            send(exchange, 400, "{\"result\":\"INVALID_REQUEST\"}");
        } catch (SQLException error) {
            send(exchange, 500, "{\"result\":\"STORE_ERROR\"}");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            exchange.close();
        }
    }

    private void observe(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        try {
            UUID id = UUID.fromString(exchange.getRequestURI().getPath().substring("/operations/".length()));
            SimulatorStore.Snapshot row;
            synchronized (admission) {
                row = store.read(id);
                try { store.recordRequest(id, "OBSERVE"); }
                catch (SQLException diagnosticsUnavailable) { /* Test evidence is separate from authority. */ }
            }
            String result = row == null ? "CONFIRMED_NO_EFFECT"
                    : row.state().equals("SUCCEEDED") ? "CONFIRMED_SUCCEEDED" : "INDETERMINATE";
            send(exchange, 200, "{\"result\":\"" + result + "\"}");
        } catch (IllegalArgumentException error) {
            send(exchange, 400, "{\"result\":\"INVALID_REQUEST\"}");
        } catch (SQLException error) {
            send(exchange, 500, "{\"result\":\"STORE_ERROR\"}");
        }
    }

    private void effect(HttpExchange exchange) throws IOException {
        if (!method(exchange, "GET")) return;
        try {
            UUID id = UUID.fromString(exchange.getRequestURI().getPath().substring("/test/effects/".length()));
            SimulatorStore.Snapshot row = store.read(id);
            if (row == null) {
                send(exchange, 404, "{\"result\":\"ABSENT\"}");
                return;
            }
            send(exchange, 200, "{\"operationId\":\"" + row.operationId() + "\",\"digest\":\""
                    + row.digest() + "\",\"state\":\"" + row.state() + "\",\"effectCount\":"
                    + row.effectCount() + "}");
        } catch (IllegalArgumentException error) {
            send(exchange, 400, "{\"result\":\"INVALID_REQUEST\"}");
        } catch (SQLException error) {
            send(exchange, 500, "{\"result\":\"STORE_ERROR\"}");
        }
    }

    private void gate(HttpExchange exchange) throws IOException {
        if (!method(exchange, "POST")) return;
        String action = exchange.getRequestURI().getPath().substring("/test/gates/".length());
        switch (action) {
            case "block-next" -> {
                if (!nextGate.compareAndSet(null, Gate.BLOCK)) {
                    send(exchange, 409, "{}"); return;
                }
            }
            case "drop-next" -> {
                if (!nextGate.compareAndSet(null, Gate.DROP)) {
                    send(exchange, 409, "{}"); return;
                }
            }
            case "release" -> {
                CountDownLatch latch = activeBlock.get();
                if (latch == null) { send(exchange, 409, "{}"); return; }
                latch.countDown();
            }
            case "clear" -> {
                nextGate.set(null);
                CountDownLatch latch = activeBlock.getAndSet(null);
                if (latch != null) latch.countDown();
            }
            default -> { send(exchange, 404, "{}"); return; }
        }
        send(exchange, 204, "");
    }

    private static void requireFields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject()
                || !node.properties().stream().map(java.util.Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toSet()).equals(expected)) {
            throw new IllegalArgumentException("Invalid semantic shape");
        }
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isTextual()) throw new IllegalArgumentException("Invalid text");
        return node.asText();
    }

    private static UUID uuid(JsonNode node) {
        String raw = text(node);
        UUID value = UUID.fromString(raw);
        if (!value.toString().equals(raw)) throw new IllegalArgumentException("Noncanonical UUID");
        return value;
    }

    private static boolean method(HttpExchange exchange, String expected) throws IOException {
        if (expected.equals(exchange.getRequestMethod())) return true;
        send(exchange, 405, "{}");
        return false;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Override
    public void close() {
        CountDownLatch latch = activeBlock.getAndSet(null);
        if (latch != null) latch.countDown();
        server.stop(0);
        requests.shutdownNow();
    }
}
