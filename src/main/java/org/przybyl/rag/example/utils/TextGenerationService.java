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
import java.util.Map;
import java.util.stream.Stream;

/**
 * Service for generating text responses using language models.
 */
public interface TextGenerationService {
    /**
     * Generates text based on the given prompt and configuration options.
     *
     * @param prompt The input text to generate a response for
     * @param options Configuration options for the generation (e.g., temperature, max tokens)
     * @return A stream of generated text chunks
     * @throws IOException if there's an error in communication
     * @throws InterruptedException if the request is interrupted
     */
    Stream<String> generate(String prompt, Map<String, ?> options) throws IOException, InterruptedException;
}