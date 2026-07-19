#!/bin/bash

error_log() {
    echo "[ERROR] $1" >&2
    if [ -n "$2" ]; then
        echo "[ERROR] Details: $2" >&2
    fi
}

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
}

# Test the embedding endpoint (OpenAI-compatible)
test_embedding_service() {
    debug "Testing embedding service at ${EMBEDDING_ENDPOINT}..."
    local response=$(curl -s "${EMBEDDING_ENDPOINT}" \
        -H "Content-Type: application/json" \
        -d "{\"model\": \"${EMBEDDING_MODEL}\", \"input\": \"test\"}")
    if [ $? -ne 0 ] || [ -z "$response" ] || ! echo "$response" | jq -e '.data[0].embedding' > /dev/null 2>&1; then
        error_log "Cannot get embeddings from ${EMBEDDING_ENDPOINT} using model ${EMBEDDING_MODEL}"
        exit 1
    fi
    debug "Embedding service connection successful"
}
