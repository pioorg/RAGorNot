#!/bin/bash

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
check_env_vars "ES_URL" "ES_APIKEY" "SEARCH_INDEX" "SEARCH_K" "SEARCH_NUM_CANDIDATES" "OLLAMA_URL" "OLLAMA_EMBEDDING_MODEL"

# Test connections
test_elasticsearch
test_ollama


echo "Enter your query:"
read -r query || exit 1
if [ -z "${query:-}" ]; then
    error_log "Empty query"
    exit 1
fi

if [ "$DEBUG" = "true" ]; then
    debug "Raw query: '$query'"
fi

# Get embedding for the query
debug "Getting embedding for query: $query"
query_embedding=$(get_embedding "$query")

if [ $? -ne 0 ]; then
    error_log "Failed to get embedding for the query"
    exit 1
fi

# Perform vector search
debug "Performing vector search"
search_results=$(perform_vector_search "$query_embedding" "$SEARCH_K" "$SEARCH_NUM_CANDIDATES")

if [ $? -ne 0 ] || [ -z "$search_results" ]; then
    error_log "Failed to perform vector search"
    exit 1
fi

# Display results
display_results "$search_results"
