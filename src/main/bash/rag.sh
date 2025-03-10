#!/bin/bash

# Exit on error, undefined vars, and pipe failures
set -euo pipefail

#
# Requirements:
# - Ollama server running with required models
# - Elasticsearch with index containing embeddings
# - Environment variables:
#   OLLAMA_URL: Ollama server URL (e.g., http://localhost:11434)
#   OLLAMA_EMBEDDING_MODEL: Model for generating embeddings (e.g., all-minilm)
#   OLLAMA_GENERATING_MODEL: Model for generating responses (e.g., mistral)
#   ES_URL: Elasticsearch URL
#   ES_APIKEY: Elasticsearch API key
#   SEARCH_INDEX: Elasticsearch index name

# Source utility scripts
SCRIPT_DIR="$(dirname "$0")"
source "${SCRIPT_DIR}/utils/checks.sh"
source "${SCRIPT_DIR}/utils/embeddings.sh"
source "${SCRIPT_DIR}/utils/searching.sh"

# Check if debug mode is enabled
DEBUG=false
for arg in "$@"; do
    if [ "$arg" = "--debug" ]; then
        DEBUG=true
        break
    fi
done

# Check required environment variables
check_env_vars "ES_URL" "ES_APIKEY" "SEARCH_INDEX" "SEARCH_K" "SEARCH_NUM_CANDIDATES" "OLLAMA_URL" "OLLAMA_EMBEDDING_MODEL" "OLLAMA_GENERATING_MODEL"

# Test connections
test_elasticsearch
test_ollama

# Check if required models are available
echo "Checking required models..."
models=$(curl -s -f "${OLLAMA_URL}/api/tags" || {
    error_log "Failed to get models from Ollama"
    exit 1
})
if ! (echo "$models" | grep -q "$OLLAMA_EMBEDDING_MODEL" && echo "$models" | grep -q "$OLLAMA_GENERATING_MODEL"); then
    error_log "Required models ($OLLAMA_EMBEDDING_MODEL and $OLLAMA_GENERATING_MODEL) are not available"
    echo "Please pull the models using:"
    echo "  ollama pull $OLLAMA_EMBEDDING_MODEL"
    echo "  ollama pull $OLLAMA_GENERATING_MODEL"
    exit 1
fi

# Ask for the prompt
echo "Enter your prompt:"
read -r prompt || exit 1
if [ -z "${prompt:-}" ]; then
    echo "Error: Empty prompt"
    exit 1
fi

# Get vector embeddings from Ollama
echo "Getting vector embeddings..."
query_embedding=$(get_embedding "$prompt")

if [ $? -ne 0 ]; then
    error_log "Failed to get embedding for the prompt"
    exit 1
fi

# Get relevant documents from Elasticsearch
echo "Searching for relevant documents..."
search_results=$(perform_vector_search "$query_embedding" "$SEARCH_K" "$SEARCH_NUM_CANDIDATES")

if [ $? -ne 0 ]; then
    error_log "Failed to perform vector search"
    exit 1
fi

# Display results
display_results "$search_results"

# Extract document bodies
documents=$(extract_document_bodies "$search_results")

# Create a combined prompt
combined_prompt="
Given the context information provided below, answer the following user prompt: $prompt

Context information is below.
---------------------
$documents
---------------------
"

if [ "$DEBUG" = "true" ]; then
    debug "Combined prompt is: '$combined_prompt'"
fi

# Get the final response from Ollama
echo -e "\nGetting the answer...\n"

# Properly escape the combined prompt for JSON
json_prompt=$(jq -n --arg prompt "$combined_prompt" '$prompt' || {
    echo "Error: Failed to escape prompt for JSON"
    exit 1
})

curl -s -N -f -X POST "${OLLAMA_URL}/api/generate" -d '{
  "model": "'"$OLLAMA_GENERATING_MODEL"'",
  "prompt": '"$json_prompt"'
}' | while IFS= read -r line; do
  # If the line indicates the stream is done, break out of the loop.
  if echo "$line" | grep -q '"done":true'; then
    break
  fi
  # Extract the response text.
  chunk=$(echo "$line" | sed -E 's/.*"response":"(.*)","done":(false|true)}.*/\1/')
  # Replace Unicode escapes for '<' and '>' with actual characters.
  chunk=$(echo "$chunk" | sed 's/\\u003c/</g; s/\\u003e/>/g')
  # Print while interpreting escape sequences like \n.
  printf "%b" "$chunk"
done

# Add final newline for readability
echo
