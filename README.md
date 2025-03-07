## Dummy example of a RAG

The purpose of this demo is to show that RAG is not a rocket science and that actually it's just  some (HTTP) calls to AI models combined with a vector store (in this case Elasticsearch).

The only library used here is Jackson, to handle JSON (because Java has no native handling in its SDK). 
Please remember, that for any production-grade code you should be using trusted client libraries, like [elasticsearch-java](https://github.com/elastic/elasticsearch-java) and [ollama4j](https://github.com/ollama4j/ollama4j).

## Some setup

This demo assumes, that you have Elasticsearch and Ollama running somewhere, e.g. locally.

### Setting up Elasticsearch
If you have Docker (with compose) available, you can start Elasticsearch using [start-local](https://github.com/elastic/start-local)

### Setting up Ollama
You can install Ollama for your operating system using [ollama.com](https://ollama.com/), and then pull & run some models. This demo needs at least two, one for creating embeddings and one for generation, e.g.
`ollama pull all-minilm:latest`
`ollama pull deepseek-r1:14b`

### Setting up env variables

Then the next step might be to set up nice .env file for convenience, e.g.
```shell
export ES_URL=http://localhost:9200
export ES_USERNAME=elastic
export ES_APIKEY=PUT_THE_KEY_HERE

export CRAWL_INDEX=MY_INDEX
export SEARCH_INDEX=MY_INDEX_WITH_EMBEDDINGS
export MAX_WORDS_PER_PASSAGE=300
export SEARCH_K=3
export SEARCH_NUM_CANDIDATES=100

export OLLAMA_URL=http://localhost:11434
export OLLAMA_EMBEDDING_MODEL=all-minilm
export OLLAMA_GENERATING_MODEL=deepseek-r1:14b
```