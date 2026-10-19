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
        SpringApplication.run(SisBackendApplication.class, args);
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
