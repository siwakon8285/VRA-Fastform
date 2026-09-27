package dev.vra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import dev.vra.async.bootstrap.ReconciliationWorkerMode;
import dev.vra.async.bootstrap.OutboxWorkerMode;

@SpringBootApplication
public class VraApplication {

    public static void main(String[] args) {
        String mode = System.getenv().getOrDefault("VRA_PROCESS_MODE", "API");
        switch (mode) {
            case "API" -> SpringApplication.run(VraApplication.class, args);
            case "OUTBOX_WORKER" -> OutboxWorkerMode.run(System.getenv());
            case "RECONCILER" -> ReconciliationWorkerMode.run(System.getenv());
            default -> throw new IllegalArgumentException("Unknown VRA_PROCESS_MODE");
        }
    }
}
