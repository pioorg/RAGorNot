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
package org.przybyl.rag.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    public ElasticsearchConnector(ObjectMapper objectMapper) {
        String apiKey = System.getenv("ES_APIKEY");
        if (apiKey == null) {
            throw new IllegalStateException("Missing required environment variable ES_APIKEY");
        }
        String esUrl = System.getenv("ES_URL");
        if (esUrl == null) {
            throw new IllegalStateException("Missing required environment variable ES_URL");
        }
        this.esUrl = esUrl;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = objectMapper;
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

    public JsonNode search(String indexName, int from, int size) throws IOException, InterruptedException {
        String searchUrl = esUrl + "/" + indexName + "/_search";
        String queryJson = String.format("""
            {
                "query": {"match_all": {}},
                "from": %d,
                "size": %d
            }""", from, size);

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

        return objectMapper.readTree(response.body());
    }

    private String getAuthHeader() {
        return "ApiKey " + System.getenv("ES_APIKEY");
    }
}
