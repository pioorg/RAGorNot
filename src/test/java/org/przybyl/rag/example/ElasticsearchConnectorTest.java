package org.przybyl.rag.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

class ElasticsearchConnectorTest {

    @Test
    void shouldThrowExceptionWhenEnvironmentVariablesAreMissing() {
        // Store original environment variables
        String originalUrl = System.getenv("ES_URL");

        try {
            // Since we can't modify environment variables in Java, we can only test
            // when they are not set in the environment
            if (originalUrl == null) {
                ObjectMapper objectMapper = new ObjectMapper();
                assertThrows(IllegalStateException.class, 
                    () -> new ElasticsearchConnector(objectMapper),
                    "Should throw IllegalStateException when ES_URL is missing");
            } else {
                System.out.println("[DEBUG_LOG] Skipping environment variable test as ES_URL is set in the environment");
            }
        } finally {
            // No need to restore environment variables as they can't be modified in Java
        }
    }

    @Test
    void shouldThrowExceptionWhenApiKeyIsMissing() {
        // Store original environment variables
        String originalUrl = System.getenv("ES_URL");
        String originalApiKey = System.getenv("ES_APIKEY");

        try {
            // Since we can't modify environment variables in Java, we can only test
            // when ES_URL is set but ES_APIKEY is not
            if (originalUrl != null && originalApiKey == null) {
                ObjectMapper objectMapper = new ObjectMapper();
                ElasticsearchConnector connector = new ElasticsearchConnector(objectMapper);
                assertThrows(IllegalStateException.class, 
                    () -> connector.createIndex("test", "{}"),
                    "Should throw IllegalStateException when ES_APIKEY is missing");
            } else {
                System.out.println("[DEBUG_LOG] Skipping API key test as environment variables are not in the required state");
            }
        } finally {
            // No need to restore environment variables as they can't be modified in Java
        }
    }
}