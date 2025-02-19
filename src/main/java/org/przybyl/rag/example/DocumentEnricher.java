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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DocumentEnricher {
    private static final String INDEX_MAPPING = """
        {
            "mappings": {
                "properties": {
                    "titleEmbedding": {
                        "type": "dense_vector",
                        "dims": 384
                    },
                    "bodyChunks": {
                        "type": "nested",
                        "properties": {
                            "passage": {
                                "type": "text"
                            },
                            "predictedValue": {
                                "type": "dense_vector",
                                "dims": 384,
                                "index": false
                            }
                        }
                    }
                }
            }
        }""";

    private final ElasticsearchConnector esClient;
    private final TextProcessor textProcessor;
    private final Encoder encoder;

    public DocumentEnricher(ElasticsearchConnector esClient, TextProcessor textProcessor,
                            Encoder encoder) {
        this.esClient = esClient;
        this.textProcessor = textProcessor;
        this.encoder = encoder;
    }

    public void processDocuments(String indexName) throws IOException, InterruptedException {
        String targetIndexName = indexName + "-with-embeddings";

        // Create target index with mapping
        esClient.createIndex(targetIndexName, INDEX_MAPPING);

        // Process documents in batches
        int from = 0;
        final int size = 10;
        int totalProcessed = 0;
        long totalHits = 0;

        while (true) {
            // Fetch documents from source index
            JsonNode searchResponse = esClient.search(indexName, from, size);
            JsonNode hits = searchResponse.path("hits");

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
                String id = doc.path("_id").asText();
                JsonNode source = doc.path("_source");
                String title = source.path("title").asText();

                if (title == null || title.trim().isEmpty()) {
                    System.out.printf("Skipping document %s - empty or null title%n", id);
                    continue;
                }

                // Create document map
                Map<String, Object> enrichedDoc = new HashMap<>();
                enrichedDoc.put("id", id);
                enrichedDoc.put("title", title);
                enrichedDoc.put("url", source.path("url").asText());

                // Process title
                double[] titleEmbedding = encoder.encode(title);
                enrichedDoc.put("titleEmbedding", titleEmbedding);

                // Process body
                String body = source.path("body").asText();
                List<Map<String, Object>> bodyChunks = new ArrayList<>();

                if (body != null && !body.trim().isEmpty()) {
                    List<String> passages = textProcessor.splitIntoPassages(body);
                    System.out.printf("Processing %d passages from document %s (%s)%n", passages.size(), id, source.path("url").asText());

                    for (String passage : passages) {
                        Map<String, Object> chunk = new HashMap<>();
                        chunk.put("passage", passage);
                        chunk.put("predictedValue", encoder.encode(passage));
                        bodyChunks.add(chunk);
                    }
                }
                enrichedDoc.put("bodyChunks", bodyChunks);

                // Add remaining fields
                source.fields().forEachRemaining(field -> {
                    if (!List.of("title", "url", "body").contains(field.getKey())) {
                        enrichedDoc.put(field.getKey(), field.getValue().asText());
                    }
                });

                enrichedDocs.add(enrichedDoc);
            }

            // Index processed documents
            esClient.bulkIndex(targetIndexName, enrichedDocs);

            totalProcessed += enrichedDocs.size();
            System.out.printf("Processed and reindexed %d/%d documents%n", totalProcessed, totalHits);

            from += size;
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: DocumentEnricher <index_name>");
            System.exit(1);
        }

        String indexName = args[0];

        // Create shared ObjectMapper instance
        ObjectMapper objectMapper = new ObjectMapper();

        // Create dependencies
        ElasticsearchConnector esClient = new ElasticsearchConnector(objectMapper);
        TextProcessor textProcessor = new TextProcessor();
        Encoder encoder = new Encoder(new LocalOllamaRequestSender(), objectMapper);

        // Create enricher
        DocumentEnricher enricher = new DocumentEnricher(esClient, textProcessor, encoder);

        try {
            enricher.processDocuments(indexName);
        } catch (Exception e) {
            System.err.println("Error while processing documents: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
