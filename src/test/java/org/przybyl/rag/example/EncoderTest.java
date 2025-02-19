package org.przybyl.rag.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EncoderTest {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldEncodeText() {
        // given
        String text = "The sky is blue because of Rayleigh scattering";
        Encoder encoder = new Encoder(new LocalOllamaRequestSender(), OBJECT_MAPPER);

        // when
        double[] result = encoder.encode(text);

        // then
        assertNotNull(result);
        assertEquals(384, result.length);
        
        // Check if values are within reasonable bounds
        for (double value : result) {
            assertTrue(value >= -1.0 && value <= 1.0, "Embedding values should be between -1 and 1");
        }
        
        System.out.println("[DEBUG_LOG] First few values: " + 
            result[0] + ", " + result[1] + ", " + result[2]);
    }
}