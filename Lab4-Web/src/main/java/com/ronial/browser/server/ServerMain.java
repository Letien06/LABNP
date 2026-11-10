package com.ronial.browser.server;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/** Command-line entry point: java ...ServerMain --host 127.0.0.1 --port 8080 --public public --uploads downloads. */
public final class ServerMain {
    private ServerMain() { }
    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1"; int port = 8080; Path publicDirectory = Path.of("public"), uploads = Path.of("downloads");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--help")) { System.out.println("ServerMain [--host 127.0.0.1] [--port 8080] [--public public] [--uploads downloads]"); return; }
            String option = args[i]; if (++i >= args.length) throw new IllegalArgumentException("Missing value for " + option);
            switch (option) { case "--host" -> host = args[i]; case "--port" -> port = Integer.parseInt(args[i]); case "--public" -> publicDirectory = Path.of(args[i]); case "--uploads" -> uploads = Path.of(args[i]); default -> throw new IllegalArgumentException("Unknown option " + option); }
        }
        LabHttpServer server = new LabHttpServer(host, port, publicDirectory, uploads);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "lab4-server-shutdown"));
        server.start(); System.out.println("Lab4-Web listening on " + server.baseUri()); new CountDownLatch(1).await();
    }
}
