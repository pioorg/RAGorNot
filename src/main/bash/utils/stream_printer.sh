#!/bin/bash

# Function to print streaming response from stdin
print_stream_response() {
  while IFS= read -r line; do
    # If the line indicates the stream is done, break out of the loop.
    if echo "$line" | grep -q '"done":true'; then
      break
    fi
    # Extract the response text.
    chunk=$(echo "$line" | sed -E 's/.*"response":"(.*)","done":false}.*/\1/')
    # Replace Unicode escapes for '<', '>', and '\"' with actual characters.
    chunk=$(echo "$chunk" | sed 's/\\u003c/</g; s/\\u003e/>/g; s/\\"/"/g')
    # Print while interpreting escape sequences like \n.
    printf "%b" "$chunk"
  done
}

# If the script is sourced, only define the function
# If the script is run directly, read from stdin
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  print_stream_response
fi