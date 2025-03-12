#!/bin/bash

# Exit on error, undefined vars, and pipe failures
set -euo pipefail

# Source utility scripts
SCRIPT_DIR="$(dirname "$0")"
source "${SCRIPT_DIR}/utils/checks.sh"

# Check required environment variables
check_env_vars "OLLAMA_URL" "OLLAMA_GENERATING_MODEL"

html_content=$(curl -s "https://openjdk.org/jeps/485" | pup 'div#main text{}')

prompt="What are Stream Gatherers in Java?"

combined_prompt="
Given the context information provided below, answer the following user prompt in English: $prompt

Context information follows:
---------------------
$html_content
---------------------
"

echo $combined_prompt

# Properly escape the combined prompt for JSON
json_prompt=$(jq -n --arg prompt "$combined_prompt" '$prompt' || {
    echo "Error: Failed to escape prompt for JSON"
    exit 1
})


curl -s -N -f -X POST "${OLLAMA_URL}/api/generate" -d '{
  "model": "'"$OLLAMA_GENERATING_MODEL"'",
  "prompt": '"$json_prompt"',
  "options": {"temperature": 0.6}
}' | ./utils/stream_printer.sh
