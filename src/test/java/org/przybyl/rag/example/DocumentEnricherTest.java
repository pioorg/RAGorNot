package org.przybyl.rag.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

class DocumentEnricherTest {

    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();
    private final PrintStream standardOut = System.out;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outputStreamCaptor));
    }

    @Test
    void shouldRequireIndexNameArgument() {
        String[] emptyArgs = new String[]{};
        DocumentEnricher.main(emptyArgs);
        assertTrue(outputStreamCaptor.toString().contains("Usage: DocumentEnricher <index_name>"),
            "Should print usage message when no index name is provided");
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        System.setOut(standardOut);
    }
}
