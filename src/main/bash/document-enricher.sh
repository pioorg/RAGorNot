#!/bin/bash

set -e

# Load environment variables
ENV_FILE="$(dirname "$0")/.env"
if [ ! -f "$ENV_FILE" ]; then
    echo "Error: .env file not found at $ENV_FILE"
    exit 1
fi
source "$ENV_FILE"

# Debug function
debug() {
    echo "[DEBUG] $1"
}

. "$(dirname "$0")/utils/checks.sh"

# Initialize
check_env_vars "ES_URL" "ES_APIKEY" "CRAWL_INDEX" "SEARCH_INDEX" "OLLAMA_URL" "OLLAMA_EMBEDDING_MODEL"
test_elasticsearch
test_ollama

# Source the embedding utilities
. "$(dirname "$0")/utils/embeddings.sh"

# Split text into sentences
split_into_sentences() {
    local text="$1"
    # Split on ., ! or ? followed by space, newline, or quotes, preserving the delimiter
    # Also handle common abbreviations to avoid incorrect splits
    echo "$text" | sed -E '
        # Protect common abbreviations
        s/Mr\./Mr_/g;
        s/Mrs\./Mrs_/g;
        s/Dr\./Dr_/g;
        s/Ph\.D\./PhD_/g;
        s/i\.e\./ie_/g;
        s/e\.g\./eg_/g;
        # Split on sentence endings
        s/([.!?])(["\047]?[[:space:]\n])/\1\2\n/g;
        # Remove empty lines
        /^[[:space:]]*$/d;
        # Restore abbreviations
        s/Mr_/Mr./g;
        s/Mrs_/Mrs./g;
        s/Dr_/Dr./g;
        s/PhD_/Ph.D./g;
        s/ie_/i.e./g;
        s/eg_/e.g./g'
}

# Count words in text
count_words() {
    local text="$1"
    echo "$text" | wc -w
}

# Create passages from sentences respecting MAX_WORDS_PER_PASSAGE
create_passages() {
    local current_passage=""
    local current_words=0
    local max_words=$((MAX_WORDS_PER_PASSAGE * 9 / 10))  # Target 90% of max to avoid overflow

    while IFS= read -r sentence; do
        # Skip empty sentences
        if [ -z "$sentence" ]; then
            continue
        fi

        local sentence_words=$(count_words "$sentence")

        # If single sentence exceeds limit, split it into smaller chunks
        if [ $sentence_words -gt $MAX_WORDS_PER_PASSAGE ]; then
            if [ ! -z "$current_passage" ]; then
                echo "$current_passage"
                current_passage=""
                current_words=0
            fi
            # Split long sentence into chunks of roughly max_words
            local words=($sentence)
            local chunk=""
            local chunk_words=0
            for word in "${words[@]}"; do
                if [ $chunk_words -ge $max_words ]; then
                    echo "$chunk"
                    chunk="$word"
                    chunk_words=1
                else
                    if [ -z "$chunk" ]; then
                        chunk="$word"
                        chunk_words=1
                    else
                        chunk="$chunk $word"
                        chunk_words=$((chunk_words + 1))
                    fi
                fi
            done
            if [ ! -z "$chunk" ]; then
                echo "$chunk"
            fi
            continue
        fi

        # If adding this sentence would exceed the limit, output current passage and start new one
        if [ $((current_words + sentence_words)) -gt $max_words ] && [ ! -z "$current_passage" ]; then
            echo "$current_passage"
            current_passage="$sentence"
            current_words=$sentence_words
        else
            if [ -z "$current_passage" ]; then
                current_passage="$sentence"
            else
                current_passage="$current_passage $sentence"
            fi
            current_words=$((current_words + sentence_words))
        fi
    done

    # Output last passage if not empty
    if [ ! -z "$current_passage" ]; then
        echo "$current_passage"
    fi
}

# Create target index with vector mapping
create_target_index() {
    local mapping='{
        "mappings": {
            "properties": {
                "titleEmbedding": {
                    "type": "dense_vector",
                    "dims": 384,
                    "index": true,
                    "similarity": "cosine"
                },
                "bodyChunks": {
                    "type": "nested",
                    "properties": {
                        "passage": {
                            "type": "text"
                        },
                        "predictedValue": {
                            "type": "dense_vector",
                            "dims": 384,
                            "index": true,
                            "similarity": "cosine"
                        }
                    }
                }
            }
        }
    }'

    debug "Checking if target index ${SEARCH_INDEX} exists..."

    # Check if target index exists
    local target_exists=$(curl -s -k -H "Authorization: ApiKey ${ES_APIKEY}" "${ES_URL}/${SEARCH_INDEX}")
    if ! echo "$target_exists" | jq -e '.error.type == "index_not_found_exception"' > /dev/null; then
        error_log "Target index ${SEARCH_INDEX} already exists" "Please use a different target index name or delete the existing one manually"
        exit 1
    fi

    debug "Creating target index ${SEARCH_INDEX}..."

    # Get source index mapping
    local source_mapping=$(curl -s -k -H "Authorization: ApiKey ${ES_APIKEY}" "${ES_URL}/${CRAWL_INDEX}/_mapping")
    if [ $? -ne 0 ] || [ -z "$source_mapping" ]; then
        error_log "Failed to get source index mapping"
        exit 1
    fi
    debug "Got source index mapping"

    # Create target index
    local response=$(curl -s -k -X PUT -H "Authorization: ApiKey ${ES_APIKEY}" "${ES_URL}/${SEARCH_INDEX}" \
        -H "Content-Type: application/json" \
        -d "$mapping")

    if [ $? -ne 0 ] || [ -z "$response" ] || echo "$response" | jq -e '.error' > /dev/null; then
        echo "Error: Failed to create target index"
        echo "$response"
        exit 1
    fi
    debug "Target index created successfully"
}

# Handle interrupts and timeouts gracefully
cleanup() {
    local exit_code=$?
    local signal=$1
    echo ""
    if [ "$signal" = "TERM" ]; then
        echo "Script timed out at document $from"
    else
        echo "Script interrupted at document $from"
    fi
    # Ensure temp files are cleaned up
    rm -f "$tmp_bulk_file" 2>/dev/null
    exit $exit_code
}

error_log() {
    echo "[ERROR] $1" >&2
    if [ ! -z "$2" ]; then
        echo "[ERROR] Details: $2" >&2
    fi
}

# Set up signal handling
trap 'cleanup INT' INT
trap 'cleanup TERM' TERM

# Process documents in batches
process_documents() {
    local from=0
    local size=10  # Process ten documents at a time
    local total_processed=0
    local total_hits=0

    # Get total number of documents first
    local response=$(curl -s -k -H "Authorization: ApiKey ${ES_APIKEY}" "${ES_URL}/${CRAWL_INDEX}/_count")
    if [ $? -ne 0 ] || [ -z "$response" ]; then
        error_log "Failed to get document count"
        exit 1
    fi
    total_hits=$(echo "$response" | jq -r '.count')
    if [ -z "$total_hits" ] || [ "$total_hits" = "null" ]; then
        error_log "Invalid document count response" "Response: $response"
        exit 1
    fi
    debug "Total documents to process: $total_hits"

    debug "Starting document processing..."

    while true; do
        # Fetch documents from source index
        local response=$(curl -s -k -H "Authorization: ApiKey ${ES_APIKEY}" "${ES_URL}/${CRAWL_INDEX}/_search" \
            -H "Content-Type: application/json" \
            -d "{
                \"from\": $from,
                \"size\": $size,
                \"query\": {\"match_all\": {}}
            }")

        # Get total hits on first iteration
        if [ $? -ne 0 ] || [ -z "$response" ] || ! echo "$response" | jq -e '.hits' > /dev/null; then
            echo "Error: Failed to fetch documents from source index"
            exit 1
        fi

        if [ $total_processed -eq 0 ]; then
            local total_hits=$(echo "$response" | jq '.hits.total.value')
            if [ -z "$total_hits" ] || [ "$total_hits" = "null" ]; then
                echo "Error: No documents found in source index"
                exit 1
            fi
            echo "Total documents to process: $total_hits"
            debug "Found $total_hits documents to process"
        fi

        # Process each document
        local docs=$(echo "$response" | jq -c '.hits.hits[]')
        if [ -z "$docs" ]; then
            break
        fi

        # Prepare bulk index request
        local bulk_request=""
        while IFS= read -r doc; do
            local id=$(echo "$doc" | jq -r '._id')
            local source=$(echo "$doc" | jq -r '._source')
            local doc_url=$(echo "$source" | jq -r '.url // "no URL"')
            echo "[DOCUMENT] Processing ID: $id (URL: $doc_url)"

            local title=$(echo "$source" | jq -r '.title')
            local body=$(echo "$source" | jq -r '.body')

            # Create temporary files for JSON processing
            local tmp_dir=$(mktemp -d)
            local source_file="$tmp_dir/source.json"
            local enriched_file="$tmp_dir/enriched.json"

            # Save source to file
            echo "$source" > "$source_file"

            # Get title embedding
            local title_embedding="null"
            if [ ! -z "$title" ] && [ "$title" != "null" ]; then
                title_embedding=$(get_embedding "$title")
                if [ $? -ne 0 ]; then
                    title_embedding="null"
                fi
            fi

            # Process body chunks
            local body_chunks="[]"
            if [ ! -z "$body" ] && [ "$body" != "null" ]; then
                # Split text into sentences and create passages
                local sentences=$(split_into_sentences "$body")
                body_chunks="["
                local first=true

                while IFS= read -r passage; do
                    if [ ! -z "$passage" ] && [ "$passage" != "null" ]; then
                        local passage_embedding=$(get_embedding "$passage")
                        if [ $? -eq 0 ]; then
                            # Escape special characters in passage
                            local escaped_passage=$(echo "$passage" | jq -R -s '.')
                            if [ "$first" = true ]; then
                                body_chunks="${body_chunks}{\"passage\":${escaped_passage},\"predictedValue\":${passage_embedding}}"
                                first=false
                            else
                                body_chunks="${body_chunks},{\"passage\":${escaped_passage},\"predictedValue\":${passage_embedding}}"
                            fi
                            # Passage processed successfully
                        fi
                    fi
                done < <(echo "$sentences" | create_passages)
                body_chunks="${body_chunks}]"
            fi

            # Prepare document with embeddings using temporary files
            jq --argjson te "$title_embedding" --argjson bc "$body_chunks" \
                '. + {titleEmbedding: $te, bodyChunks: $bc}' "$source_file" > "$enriched_file"

            local enriched_doc=$(cat "$enriched_file")
            rm -rf "$tmp_dir"

            # Add to bulk request with proper newlines and ensure JSON is compact
            local action=$(jq -c -n --arg index "$SEARCH_INDEX" --arg id "$id" \
                '{index: {_index: $index, _id: $id}}')
            local compact_doc=$(echo "$enriched_doc" | jq -c '.')

            if [ -z "$bulk_request" ]; then
                bulk_request="${action}"$'\n'"${compact_doc}"
            else
                bulk_request="${bulk_request}"$'\n'"${action}"$'\n'"${compact_doc}"
            fi

            # Document has been processed and added to bulk request
        done <<< "$docs"

        # Execute bulk index
        if [ ! -z "$bulk_request" ]; then
            # Add final newline
            bulk_request="${bulk_request}"$'\n'

            # Save bulk request to temporary file for debugging
            local tmp_bulk_file=$(mktemp)
            echo "$bulk_request" > "$tmp_bulk_file"

            local max_retries=3
            local retry_delay=10
            local attempt=1
            local success=false

            while [ $attempt -le $max_retries ] && [ "$success" = false ]; do
                debug "Executing bulk request (attempt $attempt)..."
                local response=$(curl -s -k -H "Authorization: ApiKey ${ES_APIKEY}" "${ES_URL}/_bulk" \
                    -H "Content-Type: application/x-ndjson" \
                    --data-binary "@$tmp_bulk_file")

                if [ $? -ne 0 ] || [ -z "$response" ]; then
                    debug "Bulk request failed with network error, retrying in ${retry_delay}s..."
                elif echo "$response" | jq -e '.errors == true' > /dev/null; then
                    debug "Bulk request had errors:"
                    echo "$response" | jq '.items[] | select(.index.error != null) | .index.error'
                else
                    success=true
                    continue
                fi

                if [ $attempt -lt $max_retries ]; then
                    sleep $retry_delay
                    attempt=$((attempt + 1))
                    retry_delay=$((retry_delay * 2))
                else
                    error_log "Bulk request failed after $max_retries attempts" "$(cat "$tmp_bulk_file" | head -n 20)..."
                    exit 1
                fi
            done

            rm -f "$tmp_bulk_file"

        fi

        total_processed=$((total_processed + size))
        from=$((from + size))
        echo "Processed and reindexed $total_processed/$total_hits documents"

        # Add delay to avoid overwhelming services
        sleep 3
    done

    debug "Processing completed"
}

# Main execution
create_target_index
process_documents
