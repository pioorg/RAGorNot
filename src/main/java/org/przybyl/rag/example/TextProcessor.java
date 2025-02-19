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

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TextProcessor {
    private static final int MAX_WORDS_PER_PASSAGE = Integer.parseInt(System.getenv().getOrDefault("MAX_WORDS_PER_PASSAGE", "300"));

    public List<String> splitIntoPassages(String text) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }

        return combineIntoPassages(splitIntoSentences(text));
    }

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(text);

        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    private List<String> combineIntoPassages(List<String> sentences) {
        if (sentences == null || sentences.isEmpty()) {
            return List.of();
        }

        List<String> passages = new ArrayList<>();
        StringBuilder currentPassage = new StringBuilder();
        int currentWordCount = 0;

        for (String sentence : sentences) {
            int sentenceWordCount = sentence.split("\\s+").length;

            if (currentWordCount + sentenceWordCount > MAX_WORDS_PER_PASSAGE && currentWordCount > 0) {
                passages.add(currentPassage.toString().trim());
                currentPassage = new StringBuilder();
                currentWordCount = 0;
            }

            if (currentWordCount > 0) {
                currentPassage.append(" ");
            }
            currentPassage.append(sentence);
            currentWordCount += sentenceWordCount;
        }

        if (currentWordCount > 0) {
            passages.add(currentPassage.toString().trim());
        }

        return passages;
    }
}
