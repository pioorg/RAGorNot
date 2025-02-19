package org.przybyl.rag.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

class TextProcessorTest {

    private TextProcessor textProcessor;

    @BeforeEach
    void setUp() {
        textProcessor = new TextProcessor();
    }

    @Test
    void shouldReturnEmptyListForNullInput() {
        List<String> result = textProcessor.splitIntoPassages(null);
        assertTrue(result.isEmpty(), "Result should be empty for null input");
    }

    @Test
    void shouldReturnEmptyListForEmptyInput() {
        List<String> result = textProcessor.splitIntoPassages("");
        assertTrue(result.isEmpty(), "Result should be empty for empty input");

        result = textProcessor.splitIntoPassages("   ");
        assertTrue(result.isEmpty(), "Result should be empty for whitespace input");
    }

    @Test
    void shouldCreateSinglePassageForShortText() {
        String input = "This is a simple sentence. It should fit in one passage.";
        List<String> result = textProcessor.splitIntoPassages(input);

        assertEquals(1, result.size(), "Should create exactly one passage");
        assertEquals(input, result.getFirst(), "Passage content should match input");
    }

    @Test
    void shouldCreateMultiplePassagesForLongText() {
        // Creating a text with many words to exceed MAX_WORDS_PER_PASSAGE
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            longText.append("Word").append(i).append(" ");
            if (i % 10 == 0) longText.append(". ");
        }

        List<String> result = textProcessor.splitIntoPassages(longText.toString());

        assertTrue(result.size() > 1, "Should create multiple passages");
        for (String passage : result) {
            int wordCount = passage.split("\\s+").length;
            assertTrue(wordCount <= 300, "Each passage should not exceed MAX_WORDS_PER_PASSAGE (300 words)");
        }
    }

    @Test
    void shouldHandleSpecialCharacters() {
        String input = "This sentence has special chars: @#$%^&*()! Next sentence has numbers: 12345.";
        List<String> result = textProcessor.splitIntoPassages(input);

        assertEquals(1, result.size(), "Should create exactly one passage");
        assertEquals(input, result.getFirst(), "Passage should preserve special characters");
    }

    @Test
    void shouldPreserveSentenceBoundaries() {
        String input = "First sentence. Second sentence. Third sentence.";
        List<String> result = textProcessor.splitIntoPassages(input);

        assertEquals(1, result.size(), "Should create exactly one passage");
        assertEquals(input, result.getFirst(), "Should preserve sentence boundaries");
        assertEquals(3, result.getFirst().split("\\.").length, "Should contain three sentences");
    }

    @Test
    void shouldHandleLongSingleSentence() {
        StringBuilder longSentence = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            longSentence.append("word").append(i).append(" ");
        }
        longSentence.append(".");

        List<String> result = textProcessor.splitIntoPassages(longSentence.toString());

        assertFalse(result.isEmpty(), "Should handle long single sentence");
        assertTrue(result.getFirst().endsWith("."), "Should preserve sentence ending");
    }

    @Test
    void shouldHandleMixedParagraphsAndSentenceLengths() {
        String input = """
            This is a short first sentence. And another short one.

            Now this is a much longer sentence that contains more words and should contribute more significantly to the word count limit that we have set for our passages in the configuration.

            Back to some shorter sentences here. With varying punctuation! And some questions? Multiple short ones.

            And finally, we conclude with a medium-length sentence that contains enough words to make it interesting but not too long to break any limits.""";

        List<String> result = textProcessor.splitIntoPassages(input);

        assertFalse(result.isEmpty(), "Should create at least one passage");
        for (String passage : result) {
            int wordCount = passage.split("\\s+").length;
            assertTrue(wordCount <= 300, "Each passage should not exceed MAX_WORDS_PER_PASSAGE (300 words)");
            assertTrue(passage.trim().endsWith(".") || 
                      passage.trim().endsWith("!") || 
                      passage.trim().endsWith("?"), 
                      "Each passage should end with proper punctuation");
        }
    }
}
