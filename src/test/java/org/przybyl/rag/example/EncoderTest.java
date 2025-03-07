package org.przybyl.rag.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.przybyl.rag.example.utils.EmbeddingService;
import org.przybyl.rag.example.utils.Encoder;

import java.io.IOException;

class EncoderTest {

    private static class TestEmbeddingService implements EmbeddingService {
        private final String responseJson;
        private final Exception error;

        TestEmbeddingService(String responseJson) {
            this.responseJson = responseJson;
            this.error = null;
        }

        TestEmbeddingService(Exception error) {
            this.responseJson = null;
            this.error = error;
        }

        @Override
        public String requestEmbedding(String requestBody) throws IOException, InterruptedException {
            if (error != null) {
                if (error instanceof IOException) {
                    throw (IOException) error;
                }
                if (error instanceof InterruptedException) {
                    throw (InterruptedException) error;
                }
                throw new IOException("Unexpected error", error);
            }
            return responseJson;
        }
    }

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldEncodeText() {
        // given
        String text = "The sky is blue because of Rayleigh scattering";
        String mockResponse = """
            {"embedding": [0.1, 0.2, 0.3, 0.4]}
            """;
        Encoder encoder = new Encoder(new TestEmbeddingService(mockResponse), OBJECT_MAPPER);

        // when
        double[] result = encoder.encode(text);

        // then
        assertNotNull(result);
        assertEquals(4, result.length);
        assertArrayEquals(new double[]{0.1, 0.2, 0.3, 0.4}, result, 0.0001);
    }

    @Test
    void shouldHandleEmptyText() {
        // given
        String text = "";
        String mockResponse = """
            {"embedding": [-0.1, 0.0, 0.1]}
            """;
        Encoder encoder = new Encoder(new TestEmbeddingService(mockResponse), OBJECT_MAPPER);

        // when
        double[] result = encoder.encode(text);

        // then
        assertNotNull(result);
        assertEquals(3, result.length);
        assertArrayEquals(new double[]{-0.1, 0.0, 0.1}, result, 0.0001);
    }

    @Test
    void shouldThrowExceptionOnNullText() {
        // given
        Encoder encoder = new Encoder(new TestEmbeddingService("""
            {"embedding": [0.1]}
            """), OBJECT_MAPPER);

        // when/then
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> encoder.encode(null));
        assertEquals("Text to encode cannot be null", thrown.getMessage());
    }

    @Test
    void shouldHandleServiceError() {
        // given
        String text = "Some text";
        IOException expectedError = new IOException("Service unavailable");
        Encoder encoder = new Encoder(new TestEmbeddingService(expectedError), OBJECT_MAPPER);

        // when/then
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> encoder.encode(text));
        assertEquals("Failed to encode text", thrown.getMessage());
        assertEquals(expectedError, thrown.getCause());
    }
}
