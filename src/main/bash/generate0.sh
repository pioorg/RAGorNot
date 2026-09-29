#!/bin/bash

# Exit on error, undefined vars, and pipe failures
set -euo pipefail

# Source utility scripts
SCRIPT_DIR="$(dirname "$0")"
source "${SCRIPT_DIR}/utils/checks.sh"

# Check required environment variables
check_env_vars "GENERATING_ENDPOINT" "GENERATING_MODEL"

curl -s -N -f -X POST "${GENERATING_ENDPOINT}" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "'"$GENERATING_MODEL"'",
    "messages": [{"role": "user", "content": "What are Stream Gatherers in Java"}],
    "stream": true,
    "temperature": 0.6
  }'  # | ./utils/stream_printer.sh
