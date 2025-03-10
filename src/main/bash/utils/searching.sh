#!/bin/bash

# Function to perform vector search
perform_vector_search() {
    local query_embedding="$1"
    local k="${2:-1}"  # Default to 1 if not provided
    local num_candidates="${3:-100}"  # Default to 100 if not provided

    local es_query=$(jq -n \
        --argjson vector "$query_embedding" \
        --argjson k "$k" \
        --argjson candidates "$num_candidates" \
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

# Function to extract document bodies from search results
extract_document_bodies() {
    local results="$1"
    if [ "$(echo "$results" | jq -r '.hits.hits | length')" -gt 0 ]; then
        echo "$results" | jq -e -r '.hits.hits[].fields.body[0]' | tr '\n' ' '
    else
        echo ""
    fi
}