package com.equisplit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

@SpringBootApplication
public class EquiSplitApplication {

    private static final Logger log = LoggerFactory.getLogger(EquiSplitApplication.class);

    public static void main(String[] args) {
        loadDotEnvIfPresent();
        SpringApplication.run(EquiSplitApplication.class, args);
    }

    private static void loadDotEnvIfPresent() {
        File envFile = new File(".env");
        if (!envFile.exists()) {
            envFile = new File("../.env");
        }
        if (!envFile.exists()) {
            envFile = new File("../ExpenseSplitter/backend/.env");
        }

        if (envFile.exists() && envFile.isFile()) {
            log.info("Loading environment configuration from {}", envFile.getAbsolutePath());
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eqIdx = line.indexOf('=');
                    if (eqIdx > 0) {
                        String key = line.substring(0, eqIdx).trim();
                        String value = line.substring(eqIdx + 1).trim();
                        // Remove surrounding quotes if present
                        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to read .env file: {}", e.getMessage());
            }
        }
    }
}
