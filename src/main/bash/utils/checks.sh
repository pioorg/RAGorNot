#!/bin/bash

# Check required environment variables
check_env_vars() {
    local required_vars=("$@")

    for var in "${required_vars[@]}"; do
        if [ -z "${!var}" ]; then
            echo "Error: Required environment variable $var is not set"
            exit 1
        fi
    done
}

# Test Elasticsearch connectivity
test_elasticsearch() {
    # Ensure API key is available
    if [ -z "$ES_APIKEY" ]; then
        error_log "ES_APIKEY environment variable is not set"
        exit 1
    fi

    debug "Testing Elasticsearch connectivity..."
    local response=$(curl -s -k -H "Authorization: ApiKey ${ES_APIKEY}" "${ES_URL}/_cluster/health")
    if [ $? -ne 0 ] || [ -z "$response" ]; then
        error_log "Cannot connect to Elasticsearch at ${ES_URL}"
        exit 1
    fi
    debug "Elasticsearch connection successful"

    # Check if search index exists
    local index_exists=$(curl -s -k -H "Authorization: ApiKey ${ES_APIKEY}" "${ES_URL}/${SEARCH_INDEX}")
    if echo "$index_exists" | jq -e '.error' > /dev/null; then
        error_log "Search index ${SEARCH_INDEX} does not exist"
        exit 1
    fi
    debug "Search index ${SEARCH_INDEX} exists"
}

# Test Ollama connectivity
test_ollama() {
    debug "Testing Ollama connectivity..."
    local response=$(curl -s "${OLLAMA_URL}/api/embeddings" \
        -H "Content-Type: application/json" \
        -d "{
            \"model\": \"${OLLAMA_EMBEDDING_MODEL}\",
            \"prompt\": \"test\"
        }")
    if [ $? -ne 0 ] || [ -z "$response" ] || ! echo "$response" | jq -e '.embedding' > /dev/null; then
        echo "Error: Cannot get embeddings from Ollama at ${OLLAMA_URL}"
        exit 1
    fi
    debug "Ollama connection successful"
}
