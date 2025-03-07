#!/bin/bash

# Exit on any error
set -e

# Enable debug logging
debug_log() {
    echo "[DEBUG_LOG] $1"
}

# Check if ES_APIKEY is set
if [ -z "$ES_APIKEY" ]; then
    echo "Error: ES_APIKEY environment variable is not set"
    exit 1
fi

# Create configuration file
CONFIG_FILE="crawl-config.yml"
debug_log "Creating configuration file: $CONFIG_FILE"
cat > "$CONFIG_FILE" << EOL
domains:
  - url: https://openjdk.org         # The base URL for this domain
    seed_urls:                         # The entry point(s) for crawl jobs
      - https://openjdk.org/jeps/0

    crawl_rules:
      - policy: allow
        type: begins
        pattern: /jeps
      - policy: deny
        type: regex
        pattern: .*      # catch-all pattern

output_sink: elasticsearch

output_index: my-index-jeps-bash

max_crawl_depth: 3

head_requests_enabled: false

purge_crawl_enabled: false

full_html_extraction_enabled: false

ssl_verification_mode: none

compression_enabled: true

log_level: info

event_logs: false

socket_timeout: 30
connect_timeout: 30
request_timeout: 120

elasticsearch:
  host: http://host.docker.internal
  port: 9200
  api_key: $ES_APIKEY
EOL

CONTAINER_NAME="crawler-42"

# Run the crawler container
debug_log "Starting crawler"
docker run --network bridge -v "$(pwd)/$CONFIG_FILE:/tmp/my-crawler.yml" docker.elastic.co/integrations/crawler:0.2.1 -c "bin/crawler crawl /tmp/my-crawler.yml"

# Check if container executed successfully
if [ $? -ne 0 ]; then
    echo "Error: Container execution failed"
    # Cleanup on failure
    rm -f "$CONFIG_FILE"
    rmdir config || true
    docker network rm "$NETWORK_NAME" || true
    exit 1
fi

# Cleanup
debug_log "Starting cleanup process..."
rm -f "$CONFIG_FILE"

debug_log "Crawler execution completed successfully."
