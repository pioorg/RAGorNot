#!/bin/bash
# RTFM: https://github.com/elastic/crawler/blob/main/README.md
# works by default with https://github.com/elastic/start-local

# Exit on any error
set -e

# Check if ES_APIKEY is set
if [ -z "$ES_APIKEY" ]; then
    echo "Error: ES_APIKEY environment variable is not set"
    exit 1
fi

# Check if CRAWL_INDEX is set
if [ -z "$CRAWL_INDEX" ]; then
    echo "Error: CRAWL_INDEX environment variable is not set"
    exit 1
fi

# Create configuration file
CONFIG_FILE="crawl-config.yml"
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
  host: http://host.docker.internal
  port: 9200
  api_key: $ES_APIKEY
EOL

# Run the crawler container
echo "Starting crawler"
docker run --network bridge -v "$(pwd)/$CONFIG_FILE:/config/crawl-config.yml" docker.elastic.co/integrations/crawler:1.0.0 jruby bin/crawler crawl /config/crawl-config.yml

# Check if container executed successfully
if [ $? -ne 0 ]; then
    echo "Error: Container execution failed"
    rm -f "$CONFIG_FILE"
    exit 1
fi

# Cleanup
rm -f "$CONFIG_FILE"
echo "Crawler execution completed successfully."
