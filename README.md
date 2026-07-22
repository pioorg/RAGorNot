## Dummy example of a RAG

The purpose of this demo is to show that RAG is not a rocket science and that actually it's just some (HTTP) calls to AI models combined with a vector store (in this case Elasticsearch).

The only library used here is Jackson, to handle JSON (because Java has no native handling in its SDK).
Please remember, that for any production-grade code you should be using trusted client libraries, like [elasticsearch-java](https://github.com/elastic/elasticsearch-java).

## Some setup

This demo assumes you have Elasticsearch and Docker Model Runner running locally.

### Setting up Elasticsearch
If you have Docker (with compose) available, you can start Elasticsearch using [start-local](https://github.com/elastic/start-local).

### Setting up Docker Model Runner
Docker Model Runner is included with Docker Desktop. Enable it in Docker Desktop settings, then pull the required models:
```shell
docker model pull hf.co/jinaai/jina-embeddings-v5-text-nano-retrieval-gguf:Q4_K_M
docker model pull ai/deepseek-r1-distill-llama
```

The demo works fully offline once the models are downloaded.

### Setting up env variables

Copy `.env-example` to `.env` and fill in your values:
```shell
export ES_URL=http://localhost:9200
export ES_APIKEY=PUT_THE_KEY_HERE

export CRAWL_INDEX=MY_CRAWL_INDEX
export SEARCH_INDEX=MY_INDEX_WITH_EMBEDDINGS
export MAX_WORDS_PER_PASSAGE=150
export SEARCH_K=3
export SEARCH_NUM_CANDIDATES=100

export EMBEDDING_ENDPOINT=http://localhost:12434/engines/v1/embeddings
export EMBEDDING_MODEL=hf.co/jinaai/jina-embeddings-v5-text-nano-retrieval-gguf:Q4_K_M

export GENERATING_ENDPOINT=http://localhost:12434/engines/v1/chat/completions
export GENERATING_MODEL=ai/deepseek-r1-distill-llama
```

Then load it with `source .env` before running any scripts.
