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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Implementation of EmbeddingService that uses Ollama's API to create embeddings.
 */
public class OllamaEmbeddingService implements EmbeddingService {

    private static final String ENCODE_URL = System.getenv().getOrDefault("OLLAMA_URL", "http://localhost:11434")+"/api/embeddings";
    private final HttpClient httpClient;

    public OllamaEmbeddingService() {
        this(HttpClient.newHttpClient());
    }

    public OllamaEmbeddingService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String requestEmbedding(String requestBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ENCODE_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}