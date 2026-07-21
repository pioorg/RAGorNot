#!/bin/bash

# Exit on error, undefined vars, and pipe failures
set -euo pipefail

# Source utility scripts
SCRIPT_DIR="$(dirname "$0")"
source "${SCRIPT_DIR}/utils/checks.sh"

# Check required environment variables
check_env_vars "GENERATING_ENDPOINT" "GENERATING_MODEL"

echo "Fetching the context from the Internet...."

html_content=$(curl -s "https://openjdk.org/jeps/485" | xmllint --html --xpath "string(//div[@id='main'])" - 2>/dev/null)

prompt="What are Stream Gatherers in Java?"

combined_prompt="
Given the context information provided below, answer the following user prompt in English: $prompt

Context information follows:
---------------------
$html_content
---------------------
"

echo "$combined_prompt"

request_body=$(jq -n \
    --arg model "$GENERATING_MODEL" \
    --arg content "$combined_prompt" \
    '{model: $model, messages: [{role: "user", content: $content}], stream: true, temperature: 0.6}')

curl -s -N -f -X POST "${GENERATING_ENDPOINT}" \
    -H "Content-Type: application/json" \
    -d "$request_body" \
| "${SCRIPT_DIR}/utils/stream_printer.sh"
