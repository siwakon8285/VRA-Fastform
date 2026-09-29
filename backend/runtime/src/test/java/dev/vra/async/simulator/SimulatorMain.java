package dev.vra.async.simulator;

/** Test-only entry point; storage and server live independently of VRA workers. */
public final class SimulatorMain {
    private SimulatorMain() {}

    public static void main(String[] args) throws Exception {
        SimulatorStore store = new SimulatorStore(required("SIM_DB_URL"), required("SIM_DB_USER"),
                required("SIM_DB_PASSWORD"));
        store.initialize();
        SimulatorHttpServer server = new SimulatorHttpServer(store);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
        System.out.println("SIMULATOR_READY " + server.port() + " " + ProcessHandle.current().pid());
        System.out.flush();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
