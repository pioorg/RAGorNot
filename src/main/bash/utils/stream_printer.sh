#!/bin/bash

# Reads an OpenAI-compatible SSE stream from stdin and prints it.
# Reasoning models emit thinking in reasoning_content before the answer in content.
# Thinking is wrapped in <think>...</think> tags.

in_reasoning=false
while IFS= read -r line; do
    [[ "$line" != data:* ]] && continue
    json="${line#data: }"
    [ "$json" = "[DONE]" ] && break

    # Use | type to check presence — safe to capture with $() since "string"/"null" have no newlines.
    # Use jq -j to print content directly (no added trailing newline), preserving \n in the model output.
    reasoning_type=$(echo "$json" | jq -r '.choices[0].delta.reasoning_content | type' 2>/dev/null)
    content_type=$(echo "$json" | jq -r '.choices[0].delta.content | type' 2>/dev/null)

    if [ "$reasoning_type" = "string" ]; then
        if [ "$in_reasoning" = false ]; then
            printf "<think>"
            in_reasoning=true
        fi
        echo "$json" | jq -j '.choices[0].delta.reasoning_content'
    fi

    if [ "$content_type" = "string" ]; then
        if [ "$in_reasoning" = true ]; then
            printf "</think>\n"
            in_reasoning=false
        fi
        echo "$json" | jq -j '.choices[0].delta.content'
    fi
done

if [ "$in_reasoning" = true ]; then
    printf "</think>\n"
fi
