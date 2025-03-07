/*
 * Copyright 2025 Piotr Przybył
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.przybyl.rag.example.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/// This is how one can connect o Elasticsearch using vanilla Java and Jackson as the only dependency.
///
/// Please *be aware, this is not the recommended way of connecting to Elasticsearch*, it's much
/// better to use the official and dedicated client library, which can be found on [Github](https://github.com/elastic/elasticsearch-java)
public class ElasticsearchConnector {
    private final String esUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ElasticsearchConnector(ObjectMapper objectMapper, String esUrl, HttpClient httpClient) {
        this.esUrl = esUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public ElasticsearchConnector(ObjectMapper objectMapper, String esUrl) {
        this(objectMapper, esUrl, HttpClient.newHttpClient());
    }

    public ElasticsearchConnector(ObjectMapper objectMapper) {
        String apiKey = System.getenv("ES_APIKEY");
        if (apiKey == null) {
            throw new IllegalStateException("Missing required environment variable ES_APIKEY");
        }
        String esUrl = System.getenv("ES_URL");
        if (esUrl == null) {
            throw new IllegalStateException("Missing required environment variable ES_URL");
        }
        this(objectMapper, esUrl, HttpClient.newHttpClient());

    }

    public void createIndex(String indexName, String mappingJson) throws IOException, InterruptedException {
        String createIndexUrl = esUrl + "/" + indexName;
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(createIndexUrl))
            .header("Authorization", getAuthHeader())
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(mappingJson))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to create index. Status code: " + response.statusCode() +
                ", Response: " + response.body());
        }
    }

    public void bulkIndex(String indexName, List<Map<String, Object>> documents) throws IOException, InterruptedException {
        String bulkUrl = esUrl + "/_bulk";
        StringBuilder bulkRequestBody = new StringBuilder();

        for (Map<String, Object> doc : documents) {
            // Create index action
            bulkRequestBody.append(String.format("""
                {"index":{"_index":"%s","_id":"%s"}}
                """, indexName, doc.get("id")));

            bulkRequestBody.append(objectMapper.writeValueAsString(doc)).append("\n");
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(bulkUrl))
            .header("Authorization", getAuthHeader())
            .header("Content-Type", "application/x-ndjson")
            .POST(HttpRequest.BodyPublishers.ofString(bulkRequestBody.toString()))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to index documents. Status code: " + response.statusCode() +
                ", Response: " + response.body());
        }
    }

    public String search(String indexName, int from, int size) throws IOException, InterruptedException {
        String queryJson = String.format("""
            {
                "query": {"match_all": {}},
                "from": %d,
                "size": %d
            }""", from, size);
        return searchWithCustomQuery(indexName, queryJson);
    }

    public String searchWithCustomQuery(String indexName, String queryJson) throws IOException, InterruptedException {
        String searchUrl = esUrl + "/" + indexName + "/_search";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(searchUrl))
            .header("Authorization", getAuthHeader())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(queryJson))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to search documents. Status code: " + response.statusCode() +
                ", Response: " + response.body());
        }

        return response.body();
    }

    private String getAuthHeader() {
        return "ApiKey " + System.getenv("ES_APIKEY");
    }

    public String getIndexMapping(String indexName) throws IOException, InterruptedException {
        String mappingUrl = esUrl + "/" + indexName + "/_mapping";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(mappingUrl))
            .header("Authorization", getAuthHeader())
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to get index mapping. Status code: " + response.statusCode() +
                ", Response: " + response.body());
        }

        return response.body();
    }

    public String mergeMapping(String sourceMapping, Map<String, String> additionalFields) throws IOException {
        // Parse source mapping to get properties
        JsonNode sourceMappingNode = objectMapper.readTree(sourceMapping);
        JsonNode sourceProperties = sourceMappingNode.fields().next().getValue()
            .path("mappings").path("properties");

        // Create new mapping structure
        Map<String, Object> properties = new HashMap<>();

        // Add source properties
        sourceProperties.fields().forEachRemaining(field -> {
            properties.put(field.getKey(), objectMapper.convertValue(field.getValue(), Map.class));
        });

        // Add additional fields
        additionalFields.forEach((key, value) -> {
            try {
                properties.put(key, objectMapper.readValue(value, Map.class));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Create the final structure
        Map<String, Object> mappings = Map.of("properties", properties);
        Map<String, Object> result = Map.of("mappings", mappings);

        return objectMapper.writeValueAsString(result);
    }
}
