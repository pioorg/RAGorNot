#!/bin/bash

# Get embeddings via the OpenAI-compatible embeddings endpoint with retries
get_embedding() {
    local text="$1"
    local max_retries=3
    local retry_delay=2
    local attempt=1

    while [ $attempt -le $max_retries ]; do
        local response=$(curl -s --max-time 30 "${EMBEDDING_ENDPOINT}" \
            -H "Content-Type: application/json" \
            -d "{
                \"model\": \"${EMBEDDING_MODEL}\",
                \"input\": $(echo "$text" | jq -R -s '.')
            }")

        if [ $? -eq 0 ] && [ -n "$response" ] && echo "$response" | jq -e '.data[0].embedding' > /dev/null 2>&1; then
            echo "$response" | jq -c '.data[0].embedding'
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
    if [ -n "$2" ]; then
        echo "[ERROR] Details: $2" >&2
    fi
}

# Utility function for debug logging
debug() {
    if [ "${DEBUG:-false}" = "true" ]; then
        echo "[DEBUG] $1" >&2
    fi
}
