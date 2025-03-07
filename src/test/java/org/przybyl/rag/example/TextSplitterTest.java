package org.przybyl.rag.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.przybyl.rag.example.utils.TextSplitter;

import java.util.List;

class TextSplitterTest {

    private TextSplitter textSplitter;

    @BeforeEach
    void setUp() {
        textSplitter = new TextSplitter();
    }

    @Test
    void shouldHandleNullInput() {
        List<String> result = textSplitter.splitIntoPassages(null);
        assertTrue(result.isEmpty(), "Result should be empty for null input");
    }

    @Test
    void shouldHandleEmptyInput() {
        List<String> result = textSplitter.splitIntoPassages("");
        assertTrue(result.isEmpty(), "Result should be empty for empty input");

        result = textSplitter.splitIntoPassages("   ");
        assertTrue(result.isEmpty(), "Result should be empty for whitespace input");
    }

    @Test
    void shouldHandleSingleSentence() {
        String input = "This is a simple test sentence.";
        List<String> result = textSplitter.splitIntoPassages(input);

        assertEquals(1, result.size(), "Should create one passage");
        assertEquals(input.trim(), result.get(0), "Passage should match input");
    }

    @Test
    void shouldHandleMultipleSentencesWithinLimit() {
        String input = "First sentence. Second sentence. Third sentence.";
        List<String> result = textSplitter.splitIntoPassages(input);

        assertEquals(1, result.size(), "Should create one passage");
        assertEquals(input.trim(), result.get(0), "Passage should contain all sentences");
    }

    @Test
    void shouldSplitLongText() {
        // Create 100 unique sentences, each with 10 words
        StringBuilder input = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            input.append("This is sentence number ").append(i)
                .append(" with some extra words added. ");
        }

        List<String> result = textSplitter.splitIntoPassages(input.toString());

        System.out.println("[DEBUG_LOG] Number of passages: " + result.size());
        System.out.println("[DEBUG_LOG] First passage word count: " + 
            result.get(0).split("\\s+").length);

        assertTrue(result.size() > 1, "Should split into multiple passages");
        for (String passage : result) {
            String[] words = passage.split("\\s+");
            assertTrue(words.length <= 300, 
                "Passage exceeds 300 words: " + words.length + " words");

            // Verify that sentences are not split mid-sentence
            assertTrue(passage.trim().endsWith("."), 
                "Passage should end with a complete sentence");
        }
    }

    @Test
    void shouldPreserveSentenceBoundaries() {
        String input = "First sentence! Second sentence? Third sentence. Fourth sentence...";
        List<String> result = textSplitter.splitIntoPassages(input);

        assertEquals(1, result.size(), "Should create one passage");
        assertTrue(result.get(0).contains("First sentence!"), "Should preserve exclamation mark");
        assertTrue(result.get(0).contains("Second sentence?"), "Should preserve question mark");
        assertTrue(result.get(0).contains("Third sentence."), "Should preserve period");
        assertTrue(result.get(0).contains("Fourth sentence..."), "Should preserve ellipsis");
    }
}
