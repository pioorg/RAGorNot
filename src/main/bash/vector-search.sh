#!/bin/bash

# Source utility scripts
SCRIPT_DIR="$(dirname "$0")"
source "${SCRIPT_DIR}/utils/checks.sh"
source "${SCRIPT_DIR}/utils/embeddings.sh"

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

# Function to perform vector search
perform_vector_search() {
    local query_embedding="$1"
    local es_query=$(jq -n \
        --argjson vector "$query_embedding" \
        --argjson k "$SEARCH_K" \
        --argjson candidates "$SEARCH_NUM_CANDIDATES" \
        '{
            "_source": false,
            "fields": ["title", "url", "body"],
            "knn": {
                "field": "bodyChunks.predictedValue",
                "k": $k,
                "num_candidates": $candidates,
                "query_vector": $vector
            }
        }')

    local response=$(curl -s -k \
        -H "Authorization: ApiKey ${ES_APIKEY}" \
        -H "Content-Type: application/json" \
        "${ES_URL}/${SEARCH_INDEX}/_search" \
        -d "$es_query")
    echo "$response"
}

# Function to display search results
display_results() {
    local results="$1"
    echo -e "\nSearch Results:"
    echo "=============="

    if [ "$(echo "$results" | jq -r '.hits.hits | length')" -gt 0 ]; then
        echo "$results" | jq -r '.hits.hits[] | {
            title: .fields.title[0],
            url: .fields.url[0],
            score: ._score
        } | "Title: \(.title)\nURL: \(.url)\nScore: \(.score)\n--------------"'

        if [ "$DEBUG" = "true" ]; then
            echo -e "\nDebug information:"
            echo "$results" | jq -r '.hits.hits[] | "Body: \(.fields.body[0])\n--------------"'
        fi
    else
        echo "No results found."
    fi
}


echo "Enter your query:"
read -r query || exit 1
if [ -z "${query:-}" ]; then
    echo "Error: Empty query"
    exit 1
fi


if [ "$DEBUG" = "true" ]; then
    echo "Raw query: '$query'" >&2
fi

if [ -z "$query" ]; then
    error_log "Search query cannot be empty"
    exit 1
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
search_results=$(perform_vector_search "$query_embedding")

if [ $? -ne 0 ] || [ -z "$search_results" ]; then
    error_log "Failed to perform vector search"
    exit 1
fi

# Display results
display_results "$search_results"
