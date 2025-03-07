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

package org.przybyl.rag.example.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.stream.Stream;

public class OllamaTextGenerationService implements TextGenerationService {
    private static final String GENERATE_URL = System.getenv().getOrDefault("OLLAMA_URL", "http://localhost:11434")+"/api/generate";
    private static final String MODEL = System.getenv().getOrDefault("OLLAMA_GENERATING_MODEL", "deepseek-r1:14b");
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaTextGenerationService(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder().build());
    }

    public OllamaTextGenerationService(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /// for more options, please see [documentation](https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-completion)
    public Stream<String> generate(String prompt, Map<String, ?> options) throws IOException, InterruptedException {

        // Create request body
        var requestMap = Map.of(
            "model", MODEL,
            "prompt", prompt,
            "options", options,
            "stream", true
        );
        String requestBody = objectMapper.writeValueAsString(requestMap);

        // Send request to Ollama
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(GENERATE_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        // Process streaming response
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        return response.body().map(line -> {
            try {
                return objectMapper.readTree(line);
            } catch (IOException e) {
                throw new RuntimeException("Failed to process response line", e);
            }
        }).map(responseJson -> {
            String responseText = responseJson.get("response").asText();
            return switch(responseJson.get("done").asBoolean()) {
                case true ->
                    responseText + "\n";
                case false ->
                    responseText;
            };
        });
    }
}
