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
package org.przybyl.rag.example.demos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.przybyl.rag.example.utils.ElasticsearchConnector;
import org.przybyl.rag.example.utils.Encoder;
import org.przybyl.rag.example.utils.OllamaEmbeddingService;
import org.przybyl.rag.example.utils.TextSplitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DocumentEnricher {

    public static void main(String[] args) {

        String sourceIndexName = System.getenv("CRAWL_INDEX");
        String targetIndexName = System.getenv("SEARCH_INDEX");

        try {
            // Create shared ObjectMapper instance
            ObjectMapper objectMapper = new ObjectMapper();

            // Create enricher
            DocumentEnricher enricher = new DocumentEnricher(
                new Encoder(new OllamaEmbeddingService(), objectMapper),
                new ElasticsearchConnector(objectMapper),
                objectMapper,
                new TextSplitter()
            );

            enricher.processDocuments(sourceIndexName, targetIndexName);
        } catch (Exception e) {
            System.err.println("Error while processing documents: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }


    private static final Map<String, String> VECTOR_FIELDS_MAPPING = Map.of(
        "titleEmbedding",
        """
            {
                "type": "dense_vector",
                "dims": 384
            }""",
        "bodyChunks",
        """
            {
                "type": "nested",
                "properties": {
                    "passage": {
                        "type": "text",
                        "index": false
                    },
                    "predictedValue": {
                        "type": "dense_vector",
                        "dims": 384
                    }
                }
              }
            """);

    private final ElasticsearchConnector esClient;
    private final TextSplitter textSplitter;
    private final Encoder encoder;
    private final ObjectMapper objectMapper;

    public DocumentEnricher(Encoder encoder, ElasticsearchConnector esClient, ObjectMapper objectMapper, TextSplitter textSplitter) {
        this.esClient = esClient;
        this.textSplitter = textSplitter;
        this.encoder = encoder;
        this.objectMapper = objectMapper;
    }

    public void processDocuments(String sourceIndexName, String targetIndexName) throws IOException, InterruptedException {

        createTargetIndex(sourceIndexName, targetIndexName);

        // Process documents in batches
        int from = 0;
        final int size = 10;
        int totalProcessed = 0;
        long totalHits = 0;

        while (true) {
            // Fetch documents from source index
            String searchResponseBatch = esClient.search(sourceIndexName, from, size);
            JsonNode hits = objectMapper.readTree(searchResponseBatch).path("hits");

            if (totalHits == 0) {
                totalHits = hits.path("total").path("value").asLong();
                System.out.printf("Total documents to process: %d%n", totalHits);
            }

            List<JsonNode> documents = new ArrayList<>();
            hits.path("hits").forEach(documents::add);

            if (documents.isEmpty()) {
                break;
            }

            List<Map<String, Object>> enrichedDocs = new ArrayList<>();

            for (JsonNode doc : documents) {

                Map<String, Object> enrichedDoc = new HashMap<>();

                copyFields(doc, enrichedDoc);
                addEmbeddings(doc, enrichedDoc);

                enrichedDocs.add(enrichedDoc);
            }

            // Index processed documents
            esClient.bulkIndex(targetIndexName, enrichedDocs);

            totalProcessed += enrichedDocs.size();
            System.out.printf("Processed and reindexed %d/%d documents%n", totalProcessed, totalHits);

            from += size;
        }
    }

    private void createTargetIndex(String sourceIndexName, String targetIndexName) throws IOException, InterruptedException {
        // Get source index mapping and merge it with additional fields
        String sourceMapping = esClient.getIndexMapping(sourceIndexName);
        String mergedMapping = esClient.mergeMapping(sourceMapping, VECTOR_FIELDS_MAPPING);

        // Create target index with merged mapping
        esClient.createIndex(targetIndexName, mergedMapping);
    }

    private void copyFields(JsonNode doc, Map<String, Object> enrichedDoc) {

        doc.path("_source").fields().forEachRemaining(field -> {
            JsonNode value = field.getValue();
            Object fieldValue;
            if (value.isTextual()) {
                fieldValue = value.asText();
            } else if (value.isNumber()) {
                fieldValue = value.numberValue();
            } else if (value.isBoolean()) {
                fieldValue = value.asBoolean();
            } else if (value.isNull()) {
                fieldValue = null;
            } else {
                // For arrays, objects and other types, preserve the structure
                fieldValue = value;
            }
            enrichedDoc.put(field.getKey(), fieldValue);
        });
    }

    private void addEmbeddings(JsonNode doc, Map<String, Object> enrichedDoc) {
        JsonNode source = doc.path("_source");
        String title = source.path("title").asText();
        if (title != null && !title.trim().isEmpty()) {
            double[] titleEmbedding = encoder.encode(title);
            enrichedDoc.put("titleEmbedding", titleEmbedding);
        }

        // Process body
        String body = source.path("body").asText();
        List<Map<String, Object>> bodyChunks = new ArrayList<>();

        if (body != null && !body.trim().isEmpty()) {
            List<String> passages = textSplitter.splitIntoPassages(body);
            System.out.printf("Processing %d passages from document %s (%s)%n", passages.size(), doc.path("_id"), source.path("url").asText());

            for (String passage : passages) {
                Map<String, Object> chunk = new HashMap<>();
                chunk.put("passage", passage);
                chunk.put("predictedValue", encoder.encode(passage));
                bodyChunks.add(chunk);
            }
        }
        enrichedDoc.put("bodyChunks", bodyChunks);
    }
}
