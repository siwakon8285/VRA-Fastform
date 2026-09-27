package dev.vra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import dev.vra.async.bootstrap.ReconciliationWorkerMode;

@SpringBootApplication
public class VraApplication {

    public static void main(String[] args) {
        if ("RECONCILER".equals(System.getenv("VRA_PROCESS_MODE"))) {
            ReconciliationWorkerMode.run(System.getenv());
            return;
        }
        SpringApplication.run(VraApplication.class, args);
    }
}
