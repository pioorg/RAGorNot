
package org.przybyl.rag.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.przybyl.rag.example.utils.ElasticsearchConnector;
import org.przybyl.rag.example.utils.Encoder;
import org.przybyl.rag.example.utils.SearchResult;
import org.przybyl.rag.example.utils.Searcher;

import java.io.IOException;
import java.util.List;

class SearcherTest {

    private static class TestEncoder extends Encoder {
        private static final double[] TEST_EMBEDDING = {0.1, 0.2, 0.3};

        public TestEncoder() {
            super(null, null); // We don't need these for testing
        }

        @Override
        public double[] encode(String text) {
            return TEST_EMBEDDING;
        }
    }

    private static class TestElasticsearchConnector extends ElasticsearchConnector {
        public final String testResponse;
        private final ObjectMapper objectMapper;

        public TestElasticsearchConnector(ObjectMapper objectMapper) {
            super(objectMapper, "http://test-es-url:9200");
            this.objectMapper = objectMapper;
            this.testResponse = """
                {
                    "hits": {
                        "hits": [
                            {
                                "_id": "1",
                                "_score": 0.95,
                                "fields": {
                                    "title": ["Test Document 1"],
                                    "url": ["http://example.com/1"],
                                    "body": ["Content of document 1"]
                                }
                            },
                            {
                                "_id": "2",
                                "_score": 0.85,
                                "fields": {
                                    "title": ["Test Document 2"],
                                    "url": ["http://example.com/2"],
                                    "body": ["Content of document 2"]
                                }
                            },
                            {
                                "_id": "3",
                                "_score": 0.75,
                                "fields": {
                                    "title": ["Test Document 3"],
                                    "url": ["http://example.com/3"],
                                    "body": ["Content of document 3"]
                                }
                            }
                        ]
                    }
                }
                """;
        }

        @Override
        public String searchWithCustomQuery(String indexName, String queryJson) throws IOException {
            return testResponse;
        }
    }

    private ObjectMapper objectMapper;
    private Searcher searcher;
    private TestElasticsearchConnector testConnector;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        TestEncoder encoder = new TestEncoder();
        testConnector = new TestElasticsearchConnector(objectMapper);
        searcher = new Searcher(encoder, testConnector, objectMapper);
    }

    @Test
    void testSearchWithKnnQuery() throws Exception {
        // Given
        String indexName = "my-index-jeps-with-embeddings";
        String query = "test query";

        // Debug test data
        System.out.println("[DEBUG_LOG] Test response data: " + testConnector.testResponse);

        // When
        List<SearchResult> results = searcher.search(indexName, query);

        // Then
        assertEquals(3, results.size(), "Should return exactly 3 results");
        assertEquals("1", results.get(0).id(), "First result should have ID 1");
        assertEquals("Test Document 1", results.get(0).title(), "First result should have correct title");
        assertEquals("Content of document 1", results.get(0).body(), "First result should have correct body");
        System.out.println("[DEBUG_LOG] First result: " + results.get(0));
        assertEquals(0.95f, results.get(0).score(), 0.001f, "First result should have correct score");

        System.out.println("[DEBUG_LOG] Search results:");
        results.forEach(result -> System.out.println("[DEBUG_LOG] " + result));
    }
}
