#!/bin/bash

# Load environment variables from the bash directory
ENV_FILE="$(dirname "$(dirname "$0")")/.env"
if [ ! -f "$ENV_FILE" ]; then
    echo "Error: .env file not found at $ENV_FILE"
    exit 1
fi
source "$ENV_FILE"

# Get embeddings from Ollama with retries
get_embedding() {
    local text="$1"
    local max_retries=3
    local retry_delay=10
    local attempt=1

    while [ $attempt -le $max_retries ]; do
        local response=$(curl -s "${OLLAMA_URL}/api/embeddings" \
            -H "Content-Type: application/json" \
            -d "{
                \"model\": \"${OLLAMA_EMBEDDING_MODEL}\",
                \"prompt\": $(echo "$text" | jq -R -s '.')
            }")

        if [ $? -eq 0 ] && [ ! -z "$response" ] && echo "$response" | jq -e '.embedding' > /dev/null; then
            echo "$response" | jq -c '.embedding'
            return 0
        fi
        debug "Embedding attempt $attempt failed, retrying in ${retry_delay}s..."
        sleep $retry_delay
        attempt=$((attempt + 1))
        retry_delay=$((retry_delay * 2))
    done

    error_log "Failed to get embedding after $max_retries attempts" "Text preview: ${text:0:50}..."
    return 1
}

# Utility function for error logging
error_log() {
    echo "[ERROR] $1" >&2
    if [ ! -z "$2" ]; then
        echo "[ERROR] Details: $2" >&2
    fi
}

# Utility function for debug logging
debug() {
    if [ "${DEBUG:-false}" = "true" ]; then
        echo "[DEBUG] $1" >&2
    fi
}
