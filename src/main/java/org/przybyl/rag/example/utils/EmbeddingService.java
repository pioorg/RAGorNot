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

/**
 * Service for creating vector embeddings from text using external embedding models.
 */
public interface EmbeddingService {
    /**
     * Sends a request to the embedding service and returns the response.
     *
     * @param requestBody JSON request body containing the text to embed and model configuration
     * @return JSON response containing the embedding vectors
     * @throws IOException if there's an error in communication
     * @throws InterruptedException if the request is interrupted
     */
    String requestEmbedding(String requestBody) throws IOException, InterruptedException;
}