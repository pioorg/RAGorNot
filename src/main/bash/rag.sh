#!/bin/bash

# Exit on error, undefined vars, and pipe failures
set -euo pipefail

#
# Requirements:
# - Docker Model Runner running with jina-embeddings-v5-text-nano-retrieval-gguf and a generating model
# - Elasticsearch with index containing embeddings
# - Environment variables:
#   EMBEDDING_ENDPOINT: Full URL of the OpenAI-compatible embeddings API
#   EMBEDDING_MODEL: Model identifier for embeddings
#   GENERATING_ENDPOINT: Full URL of the OpenAI-compatible chat completions API
#   GENERATING_MODEL: Model identifier for generation (e.g., ai/deepseek-r1-distill-llama)
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
check_env_vars "ES_URL" "ES_APIKEY" "SEARCH_INDEX" "SEARCH_K" "SEARCH_NUM_CANDIDATES" "EMBEDDING_ENDPOINT" "EMBEDDING_MODEL" "GENERATING_ENDPOINT" "GENERATING_MODEL"

# Test connections
test_elasticsearch
test_embedding_service
test_generating_service

# Ask for the prompt
echo "Enter your prompt:"
read -r prompt || exit 1
if [ -z "${prompt:-}" ]; then
    echo "Error: Empty prompt"
    exit 1
fi

# Get vector embeddings
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

# Get the final response from the generating model
echo -e "\nGetting the answer...\n"

request_body=$(jq -n \
    --arg model "$GENERATING_MODEL" \
    --arg content "$combined_prompt" \
    '{model: $model, messages: [{role: "user", content: $content}], stream: true, temperature: 0.6}')

curl -s -N -f -X POST "${GENERATING_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -d "$request_body" \
| "${SCRIPT_DIR}/utils/stream_printer.sh"

# Add final newline for readability
echo
