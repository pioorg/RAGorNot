package org.przybyl.rag.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.przybyl.rag.example.utils.OllamaEmbeddingService;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

class OllamaEmbeddingServiceTest {
    private static class TestHttpClient extends HttpClient {
        private HttpRequest lastRequest;
        private String lastRequestBody;
        private final String responseBody;
        private final IOException error;

        TestHttpClient(String responseBody) {
            this.responseBody = responseBody;
            this.error = null;
        }

        TestHttpClient(IOException error) {
            this.responseBody = null;
            this.error = error;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            this.lastRequest = request;
            this.lastRequestBody = request.bodyPublisher()
                .map(p -> {
                    try {
                        var subscriber = new StringSubscriber();
                        p.subscribe(subscriber);
                        return subscriber.getBody();
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(null);

            if (error != null) {
                throw error;
            }

            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) new HttpResponse<String>() {
                @Override
                public int statusCode() { return 200; }

                @Override
                public HttpRequest request() { return request; }

                @Override
                public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }

                @Override
                public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (s1, s2) -> true); }

                @Override
                public String body() { return responseBody; }

                @Override
                public Optional<SSLSession> sslSession() { return Optional.empty(); }

                @Override
                public URI uri() { return request.uri(); }

                @Override
                public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
            };
            return response;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException("Async operations not supported in test client");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException("Async operations not supported in test client");
        }

        @Override
        public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }

        @Override
        public Optional<Duration> connectTimeout() { return Optional.empty(); }

        @Override
        public Redirect followRedirects() { return Redirect.NEVER; }

        @Override
        public Optional<ProxySelector> proxy() { return Optional.empty(); }

        @Override
        public SSLContext sslContext() { return null; }

        @Override
        public SSLParameters sslParameters() { return null; }

        @Override
        public Optional<Authenticator> authenticator() { return Optional.empty(); }

        @Override
        public Version version() { return Version.HTTP_1_1; }

        @Override
        public Optional<Executor> executor() { return Optional.empty(); }

        public HttpRequest getLastRequest() {
            return lastRequest;
        }

        public String getLastRequestBody() {
            return lastRequestBody;
        }

        private static class StringSubscriber implements Flow.Subscriber<ByteBuffer> {
            private final CompletableFuture<String> body = new CompletableFuture<>();
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                body.complete(new String(bytes, StandardCharsets.UTF_8));
            }

            @Override
            public void onError(Throwable throwable) {
                body.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                if (!body.isDone()) {
                    body.complete("");
                }
            }

            public String getBody() throws InterruptedException {
                try {
                    return body.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return null;
                }
            }
        }
    }

    private static final String EXPECTED_URL = "http://localhost:11434/api/embeddings";
    private static final String TEST_REQUEST = """
        {"model": "test-model", "prompt": "test text"}
        """;
    private static final String TEST_RESPONSE = """
        {"embedding": [0.1, 0.2, 0.3]}
        """;

    private TestHttpClient httpClient;
    private OllamaEmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        httpClient = new TestHttpClient(TEST_RESPONSE);
        embeddingService = new OllamaEmbeddingService(httpClient);
    }

    @Test
    void shouldMakeProperHttpRequest() throws IOException, InterruptedException {
        // when
        String response = embeddingService.requestEmbedding(TEST_REQUEST);

        // Debug log for request and response
        System.out.println("[DEBUG_LOG] Request body: " + httpClient.getLastRequestBody());
        System.out.println("[DEBUG_LOG] Response: " + response);

        // then
        HttpRequest request = httpClient.getLastRequest();
        assertNotNull(request, "Request should be captured");
        assertEquals(URI.create(EXPECTED_URL), request.uri(), "Should use correct URL");
        assertEquals("POST", request.method(), "Should use POST method");
        assertTrue(request.headers().firstValue("Content-Type").orElse("").contains("application/json"),
            "Should set Content-Type header");

        String requestBody = httpClient.getLastRequestBody();
        assertNotNull(requestBody, "Request body should not be null");
        assertEquals(TEST_REQUEST.trim(), requestBody.trim(), "Request body should match test request");
        assertEquals(TEST_RESPONSE, response, "Should return response body");
    }

    @Test
    void shouldHandleHttpError() {
        // given
        IOException expectedError = new IOException("Service unavailable");
        httpClient = new TestHttpClient(expectedError);
        embeddingService = new OllamaEmbeddingService(httpClient);

        // when/then
        IOException thrown = assertThrows(IOException.class, 
            () -> embeddingService.requestEmbedding(TEST_REQUEST),
            "Should throw IOException for HTTP error");
        assertEquals(expectedError.getMessage(), thrown.getMessage(), 
            "Should preserve error message");
    }
}
