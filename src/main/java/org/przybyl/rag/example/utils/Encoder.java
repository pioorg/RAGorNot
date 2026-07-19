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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

public class Encoder {

    private final String model;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public Encoder(EmbeddingService embeddingService, ObjectMapper objectMapper) {
        this(embeddingService,
            objectMapper,
            System.getenv().getOrDefault("EMBEDDING_MODEL",
                "hf.co/jinaai/jina-embeddings-v5-text-nano-retrieval-gguf:Q4_K_M"));
    }

    public Encoder(EmbeddingService embeddingService, ObjectMapper objectMapper, String model) {
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.model = model;
    }

    public double[] encode(String text) {
        if (text == null) {
            throw new NullPointerException("Text to encode cannot be null");
        }
        try {
            EncodingRequest request = new EncodingRequest(model, text);
            String requestBody = objectMapper.writeValueAsString(request);
            String responseBody = embeddingService.requestEmbedding(requestBody);
            EncodingResponse response = objectMapper.readValue(responseBody, EncodingResponse.class);
            return response.data().get(0).embedding();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to encode text", e);
        }
    }

    private record EncodingRequest(
        String model,
        String input
    ) {}

    private record EncodingResponse(
        @JsonProperty("data")
        List<EmbeddingData> data
    ) {}

    private record EmbeddingData(
        @JsonProperty("embedding")
        double[] embedding
    ) {}
}
