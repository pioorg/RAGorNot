package org.przybyl.rag.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.przybyl.rag.example.utils.ElasticsearchConnector;

import java.util.Map;

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

    @Test
    void shouldMergeMappingsCorrectly() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ElasticsearchConnector connector = new ElasticsearchConnector(objectMapper, "http://dummy-url");

        String sourceMapping = """
            {
              "test-index": {
                "mappings": {
                  "properties": {
                    "title": {
                      "type": "text"
                    },
                    "body": {
                      "type": "text"
                    }
                  }
                }
              }
            }""";

        Map<String, String> additionalFields = Map.of(
            "newField", """
                {
                    "type": "keyword"
                }"""
        );

        String mergedMapping = connector.mergeMapping(
            sourceMapping,
            additionalFields
        );

        System.out.println("[DEBUG_LOG] Merged mapping: " + mergedMapping);

        // Verify the merged mapping contains all fields
        JsonNode merged = objectMapper.readTree(mergedMapping);
        JsonNode properties = merged.path("mappings").path("properties");

        assertTrue(properties.has("title"), "Should contain original 'title' field");
        assertTrue(properties.has("body"), "Should contain original 'body' field");
        assertTrue(properties.has("newField"), "Should contain new field");

        assertEquals("text", properties.path("title").path("type").asText(), "Should preserve title field type");
        assertEquals("text", properties.path("body").path("type").asText(), "Should preserve body field type");
        assertEquals("keyword", properties.path("newField").path("type").asText(), "Should add new field with correct type");
    }
}
