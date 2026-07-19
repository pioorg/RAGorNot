#!/bin/bash

# Reads an OpenAI-compatible SSE stream from stdin and prints it.
# Reasoning models emit thinking in reasoning_content before the answer in content.
# Thinking is wrapped in <think>...</think> tags.

in_reasoning=false
while IFS= read -r line; do
    [[ "$line" != data:* ]] && continue
    json="${line#data: }"
    [ "$json" = "[DONE]" ] && break

    reasoning=$(echo "$json" | jq -r '.choices[0].delta.reasoning_content // empty' 2>/dev/null)
    content=$(echo "$json" | jq -r '.choices[0].delta.content // empty' 2>/dev/null)

    if [ -n "$reasoning" ]; then
        if [ "$in_reasoning" = false ]; then
            printf "<think>"
            in_reasoning=true
        fi
        printf "%s" "$reasoning"
    fi

    if [ -n "$content" ]; then
        if [ "$in_reasoning" = true ]; then
            printf "</think>\n"
            in_reasoning=false
        fi
        printf "%s" "$content"
    fi
done

if [ "$in_reasoning" = true ]; then
    printf "</think>\n"
fi
