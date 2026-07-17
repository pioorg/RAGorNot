package org.przybyl.rag.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.przybyl.rag.example.demos.DocumentEnricher;
import org.przybyl.rag.example.utils.ElasticsearchConnector;
import org.przybyl.rag.example.utils.Encoder;
import org.przybyl.rag.example.utils.TextSplitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DocumentEnricherTest {

    private static class TestEncoder extends Encoder {
        TestEncoder() {
            super(null, null);
        }

        @Override
        public double[] encode(String text) {
            return new double[384];
        }
    }

    private static class TestElasticsearchConnector extends ElasticsearchConnector {
        private int searchCallCount = 0;
        private final List<Map<String, Object>> indexedDocuments = new ArrayList<>();

        TestElasticsearchConnector(ObjectMapper objectMapper) {
            super(objectMapper, "http://test:9200");
        }

        @Override
        public String getIndexMapping(String indexName) {
            return """
                {"%s": {"mappings": {"properties": {"title": {"type": "text"}, "body": {"type": "text"}}}}}
                """.formatted(indexName);
        }

        @Override
        public void createIndex(String indexName, String mappingJson) {}

        @Override
        public String search(String indexName, int from, int size) {
            if (searchCallCount++ == 0) {
                return """
                    {
                        "hits": {
                            "total": {"value": 2},
                            "hits": [
                                {
                                    "_id": "jep-485",
                                    "_source": {
                                        "title": "Stream Gatherers",
                                        "url": "https://openjdk.org/jeps/485",
                                        "body": "Stream Gatherers allow users to create custom intermediate stream operations."
                                    }
                                },
                                {
                                    "_id": "jep-461",
                                    "_source": {
                                        "title": "Stream Gatherers Preview",
                                        "url": "https://openjdk.org/jeps/461",
                                        "body": "Preview of the Stream Gatherers feature."
                                    }
                                }
                            ]
                        }
                    }
                    """;
            }
            return "{\"hits\": {\"total\": {\"value\": 2}, \"hits\": []}}";
        }

        @Override
        public void bulkIndex(String indexName, List<Map<String, Object>> documents) {
            indexedDocuments.addAll(documents);
        }

        List<Map<String, Object>> getIndexedDocuments() {
            return List.copyOf(indexedDocuments);
        }
    }

    @Test
    void shouldEnrichDocumentsWithEmbeddings() throws IOException, InterruptedException {
        var objectMapper = new ObjectMapper();
        var connector = new TestElasticsearchConnector(objectMapper);
        var enricher = new DocumentEnricher(
            new TestEncoder(),
            connector,
            objectMapper,
            new TextSplitter()
        );

        enricher.processDocuments("source-index", "target-index");

        var indexed = connector.getIndexedDocuments();
        assertEquals(2, indexed.size());

        var first = indexed.get(0);
        assertEquals("Stream Gatherers", first.get("title"));
        assertInstanceOf(double[].class, first.get("titleEmbedding"));
        assertInstanceOf(List.class, first.get("bodyChunks"));

        @SuppressWarnings("unchecked")
        var chunks = (List<Map<String, Object>>) first.get("bodyChunks");
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).containsKey("passage"));
        assertTrue(chunks.get(0).containsKey("predictedValue"));
    }
}
