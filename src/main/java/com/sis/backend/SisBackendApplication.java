package com.sis.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

@SpringBootApplication
public class SisBackendApplication {

    public static void main(String[] args) {
        loadDotEnv();
        validateOrFallbackMongo();
        validateOrFallbackRedis();
        SpringApplication.run(SisBackendApplication.class, args);
    }

    /**
     * Validates MongoDB Atlas connectivity with a 10-second timeout.
     * If Atlas is unreachable, automatically falls back to local MongoDB to ensure zero downtime.
     */
    private static void validateOrFallbackMongo() {
        String mongoUri = System.getProperty("MONGO_URI");
        if (mongoUri != null && mongoUri.startsWith("mongodb+srv://")) {
            System.out.println(">>> Validating MongoDB Atlas Cloud connectivity...");
            try (com.mongodb.client.MongoClient client = com.mongodb.client.MongoClients.create(
                    com.mongodb.MongoClientSettings.builder()
                            .applyConnectionString(new com.mongodb.ConnectionString(mongoUri))
                            .applyToClusterSettings(builder -> builder.serverSelectionTimeout(10000, java.util.concurrent.TimeUnit.MILLISECONDS))
                            .build())) {
                client.listDatabaseNames().first();
                System.out.println(">>> [SUCCESS] Connected to MongoDB Atlas Cloud Cluster!");
            } catch (Exception e) {
                System.err.println(">>> [NOTICE] Could not connect to MongoDB Atlas Cloud: " + e.getMessage());
                System.err.println(">>> (Tip: Whitelist 0.0.0.0/0 at https://cloud.mongodb.com -> Network Access to allow cloud connection)");
                System.err.println(">>> Seamlessly falling back to local MongoDB (mongodb://localhost:27017/sis_db) to keep backend 100% operational.");
                System.setProperty("MONGO_URI", "mongodb://localhost:27017/sis_db");
            }
        }
    }

    /**
     * Validates Redis connectivity (Upstash Cloud Redis or Local Redis).
     * If Cloud Redis credentials are provided, validates TLS handshake, AUTH, and PING.
     * If unreachable, falls back to local Redis (localhost:6379) to ensure zero downtime.
     */
    private static void validateOrFallbackRedis() {
        String upstashUrl = System.getProperty("UPSTASH_REDIS_REST_URL");
        String upstashToken = System.getProperty("UPSTASH_REDIS_REST_TOKEN");

        if (upstashUrl != null && !upstashUrl.isBlank()) {
            try {
                java.net.URI uri = new java.net.URI(upstashUrl.trim());
                String host = uri.getHost();
                if (host != null && !host.isBlank()) {
                    System.setProperty("REDIS_HOST", host);
                    System.setProperty("REDIS_PORT", "6379");
                    System.setProperty("REDIS_SSL", "true");
                    if (upstashToken != null && !upstashToken.isBlank()) {
                        System.setProperty("REDIS_PASSWORD", upstashToken.trim());
                    }
                }
            } catch (Exception e) {
                System.err.println(">>> Error resolving UPSTASH_REDIS_REST_URL host: " + e.getMessage());
            }
        }

        String host = System.getProperty("REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getProperty("REDIS_PORT", "6379"));
        String pass = System.getProperty("REDIS_PASSWORD", "");
        boolean ssl = "true".equalsIgnoreCase(System.getProperty("REDIS_SSL", "false"));

        if (!"localhost".equalsIgnoreCase(host) && !"127.0.0.1".equals(host)) {
            System.out.println(">>> Validating Upstash Cloud Redis connectivity (" + host + ":" + port + ", SSL=" + ssl + ")...");
            try {
                java.net.Socket socket = new java.net.Socket();
                socket.connect(new java.net.InetSocketAddress(host, port), 5000);
                if (ssl) {
                    javax.net.ssl.SSLSocketFactory factory = (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
                    javax.net.ssl.SSLSocket sslSocket = (javax.net.ssl.SSLSocket) factory.createSocket(socket, host, port, true);
                    sslSocket.startHandshake();
                    socket = sslSocket;
                }
                java.io.PrintWriter out = new java.io.PrintWriter(socket.getOutputStream(), true);
                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                if (!pass.isBlank()) {
                    out.println("AUTH " + pass);
                    String authResp = in.readLine();
                    if (authResp == null || !authResp.startsWith("+OK")) {
                        throw new IllegalStateException("Redis AUTH failed: " + authResp);
                    }
                }
                out.println("PING");
                String pingResp = in.readLine();
                if (pingResp == null || !pingResp.contains("PONG")) {
                    throw new IllegalStateException("Redis PING failed: " + pingResp);
                }
                socket.close();
                System.out.println(">>> [SUCCESS] Connected to Upstash Cloud Redis (" + host + ")!");
            } catch (Exception e) {
                System.err.println(">>> [NOTICE] Could not connect to Upstash Cloud Redis: " + e.getMessage());
                System.err.println(">>> Seamlessly falling back to local Redis (localhost:6379) to keep backend 100% operational.");
                System.setProperty("REDIS_HOST", "localhost");
                System.setProperty("REDIS_PORT", "6379");
                System.setProperty("REDIS_PASSWORD", "");
                System.setProperty("REDIS_SSL", "false");
            }
        }
    }

    /**
     * Automatically loads key=value pairs from .env into System Properties
     * so application.yml resolves ${VAR} seamlessly without modifying YAML.
     * Searches multiple directory locations for maximum flexibility.
     */
    private static void loadDotEnv() {
        List<File> candidateFiles = List.of(
                new File(".env"),
                new File("sis-backend/.env"),
                new File(System.getProperty("user.dir"), ".env"),
                new File(System.getProperty("user.dir"), "sis-backend/.env")
        );

        File resolvedFile = null;
        for (File candidate : candidateFiles) {
            if (candidate.exists() && candidate.isFile()) {
                resolvedFile = candidate;
                break;
            }
        }

        if (resolvedFile != null) {
            try (BufferedReader reader = new BufferedReader(new FileReader(resolvedFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                        continue;
                    }
                    int eqIdx = line.indexOf('=');
                    String key = line.substring(0, eqIdx).trim();
                    String value = line.substring(eqIdx + 1).trim();

                    // Strip optional enclosing quotes
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }

                    if (!key.isEmpty() && System.getProperty(key) == null && System.getenv(key) == null) {
                        System.setProperty(key, value);
                    }
                }
            } catch (IOException e) {
                System.err.println("Warning: Could not read .env file from " + resolvedFile.getAbsolutePath() + ": " + e.getMessage());
            }
        }
    }
}
