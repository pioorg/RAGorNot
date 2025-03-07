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
import java.util.ArrayList;
import java.util.List;

public class Searcher {
    private final Encoder encoder;
    private final ElasticsearchConnector esConnector;
    private final ObjectMapper objectMapper;

    public Searcher(Encoder encoder, ElasticsearchConnector esConnector, ObjectMapper objectMapper) {
        this.encoder = encoder;
        this.esConnector = esConnector;
        this.objectMapper = objectMapper;
    }

    public List<SearchResult> search(String indexName, String query) throws IOException, InterruptedException {
        // Convert query to embedding using Ollama
        double[] queryEmbedding = encoder.encode(query);

        // Create kNN search query
        String searchQuery = createKnnQuery(queryEmbedding);
        String searchResponse = esConnector.searchWithCustomQuery(indexName, searchQuery);

        // Parse response and return top 3 results
        JsonNode searchResponseJson = objectMapper.readTree(searchResponse);
        return parseSearchResponse(searchResponseJson);
    }

    private String createKnnQuery(double[] queryEmbedding) {
        // Convert queryEmbedding to JSON array as string
        String queryVector = objectMapper.valueToTree(queryEmbedding).toString();

        return String.format("""
                {
                  "_source": false,
                  "fields": ["title", "url", "body"],
                  "knn": {
                    "field": "bodyChunks.predictedValue",
                    "k": %s,
                    "num_candidates": %s,
                    "query_vector": %s
                  }
                }
                """,
            System.getenv().getOrDefault("SEARCH_K", "3"),
            System.getenv().getOrDefault("SEARCH_NUM_CANDIDATES", "100"),
            queryVector
        );
    }

    private List<SearchResult> parseSearchResponse(JsonNode response) {
        List<SearchResult> results = new ArrayList<>();
        JsonNode hits = response.get("hits").get("hits");

        for (JsonNode hit : hits) {
            JsonNode fields = hit.get("fields");
            float score = hit.get("_score").floatValue();

            var bodyNode = fields.get("body");
            String body = bodyNode != null && bodyNode.isArray() && bodyNode.size() > 0 ? bodyNode.get(0).asText() : "";

            results.add(new SearchResult(
                hit.get("_id").asText(),
                fields.get("title").get(0).asText(),
                fields.get("url").get(0).asText(),
                body,
                score
            ));
        }

        return results;
    }

}
