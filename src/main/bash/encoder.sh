#!/bin/bash

set -e

# Check if text is provided
if [ -z "$1" ]; then
    echo "Error: No text provided"
    echo "Usage: $0 \"text to generate embedding for\""
    exit 1
fi

# Source the embedding utilities
. "$(dirname "$0")/utils/embeddings.sh"

# Get embedding for the provided text
text="$1"
echo "Generating embedding for text: \"$text\""
embedding=$(get_embedding "$text")

if [ $? -ne 0 ]; then
    echo "Error: Failed to get embedding"
    exit 1
fi

echo "Generated embedding:"
echo "$embedding"
