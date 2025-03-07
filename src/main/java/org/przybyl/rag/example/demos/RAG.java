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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.przybyl.rag.example.utils.ElasticsearchConnector;
import org.przybyl.rag.example.utils.Encoder;
import org.przybyl.rag.example.utils.OllamaEmbeddingService;
import org.przybyl.rag.example.utils.OllamaTextGenerationService;
import org.przybyl.rag.example.utils.SearchResult;
import org.przybyl.rag.example.utils.Searcher;
import static org.przybyl.rag.example.demos.VectorSearch.displaySearchResults;
import static org.przybyl.rag.example.demos.VectorSearch.performSearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RAG {
    public static void main(String[] args) {
        try {
            boolean debugMode = args.length > 0 && List.of(args).contains("--debug");
            // Create shared ObjectMapper instance
            var objectMapper = new ObjectMapper();
            var searcher = new Searcher(
                new Encoder(new OllamaEmbeddingService(), objectMapper),
                new ElasticsearchConnector(objectMapper),
                objectMapper);
            var generationService = new OllamaTextGenerationService(objectMapper);

            // Get search query from user
            System.out.print("Enter your search query: ");
            String query = readUserInput();

            var searchResults = performSearch(searcher, query);

            displaySearchResults(searchResults, debugMode);

            String context = prepareContext(searchResults);

            String prompt = String.format("""
                Based on the following context:

                %s

                Answer this question: %s""", context, query);

            generationService
                .generate(prompt, Map.of("temperature", 0.6))
                .forEach(System.out::print);

        } catch (IOException | InterruptedException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static @NotNull String prepareContext(List<SearchResult> results) {
        return results.stream()
            .map(SearchResult::body)
            .filter(body -> body != null && !body.isBlank())
            .collect(Collectors.joining("\n\n"));
    }

    private static String readUserInput() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String input = reader.readLine();
        if (input == null || input.trim().isEmpty()) {
            throw new IOException("Invalid input: input cannot be empty");
        }
        return input.trim();
    }
}
