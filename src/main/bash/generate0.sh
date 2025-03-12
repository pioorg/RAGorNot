#!/bin/bash

# Exit on error, undefined vars, and pipe failures
set -euo pipefail

# Source utility scripts
SCRIPT_DIR="$(dirname "$0")"
source "${SCRIPT_DIR}/utils/checks.sh"

# Check required environment variables
check_env_vars "OLLAMA_URL" "OLLAMA_GENERATING_MODEL"


curl -s -N -f -X POST "${OLLAMA_URL}/api/generate" -d '{
  "model": "'"$OLLAMA_GENERATING_MODEL"'",
  "prompt": "What are Stream Gatherers in Java",
  "options": {"temperature": 0.6}
}'  # | ./utils/stream_printer.sh
