#!/bin/bash
# RTFM: https://github.com/elastic/crawler/blob/main/README.md
# works by default with https://github.com/elastic/start-local

# Exit on any error
set -euo pipefail

# Install cleanup before validation and temporary-file creation can fail.
CONFIG_FILE="$(pwd)/crawl-config.yml"
LOG_FILE=""
cleanup() {
    rm -f -- "$CONFIG_FILE"
    if [ -n "$LOG_FILE" ]; then
        rm -f -- "$LOG_FILE"
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

# Check if ES_APIKEY is set
if [ -z "${ES_APIKEY:-}" ]; then
    echo "Error: ES_APIKEY environment variable is not set"
    exit 1
fi

# Check if CRAWL_INDEX is set
if [ -z "${CRAWL_INDEX:-}" ]; then
    echo "Error: CRAWL_INDEX environment variable is not set"
    exit 1
fi

# Derive ES_PORT from ES_URL if not explicitly set
if [ -z "${ES_PORT:-}" ]; then
    ES_PORT=$(echo "$ES_URL" | sed -En 's/.*:([0-9]+)$/\1/p')
    ES_PORT=${ES_PORT:-9200}
fi
ES_HOST=$(echo "$ES_URL" | sed 's/:[0-9]*$//')

# Create configuration file
LOG_FILE=$(mktemp)
echo "Creating configuration file: $CONFIG_FILE"
cat > "$CONFIG_FILE" << EOL
domains:
  - url: https://openjdk.org         # The base URL for this domain
    seed_urls:                       # The entry point(s) for crawl jobs
      - https://openjdk.org/jeps/0

    crawl_rules:
      - policy: allow
        type: begins
        pattern: /jeps
      - policy: deny
        type: regex
        pattern: .*      # catch-all pattern

output_sink: elasticsearch

output_index: $CRAWL_INDEX

max_crawl_depth: 3

head_requests_enabled: false

purge_crawl_enabled: false

full_html_extraction_enabled: false

ssl_verification_mode: none

compression_enabled: true

log_level: info

socket_timeout: 30
connect_timeout: 30
request_timeout: 120

elasticsearch:
  host: $ES_HOST
  port: $ES_PORT
  api_key: $ES_APIKEY
EOL

# Run the crawler container
echo "Starting crawler"
if docker run --network bridge -v "$CONFIG_FILE:/config/crawl-config.yml" docker.elastic.co/integrations/crawler:1.0.0 jruby bin/crawler crawl /config/crawl-config.yml 2>&1 | tee "$LOG_FILE"; then
    :
else
    status=$?
    echo "Error: Crawler execution failed (exit status $status)" >&2
    exit "$status"
fi

# Crawler 1.0.0 can log an ES connection failure and still exit with status 0.
if grep -Fq 'Failed to reach ES at ' "$LOG_FILE"; then
    echo "Error: Crawler could not connect to Elasticsearch" >&2
    exit 1
fi

echo "Crawler process completed without a detected connection failure."
