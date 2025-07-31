package com.timofeev.llm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import com.google.gson.ToNumberPolicy;
import com.timofeev.benchmark.LlmTimingHolder;
import com.timofeev.prompt.ICompletionPrompt;
import com.timofeev.prompt.TokenizedCompletionPrompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Random;

public class OpenAiLlmClient implements ILlmInlineClient {
    private static final Logger LOG = LoggerFactory.getLogger(OpenAiLlmClient.class);

    public static final Gson GSON = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .setStrictness(Strictness.LENIENT)
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();

    private static final int MAX_TOKENS = 100;

    @NotNull
    private static final List<String> STOP_TOKENS = List.of("\n");

    @NotNull
    private final OpenAiLlmClientInfo llmClientInfo;

    @NotNull
    private final OpenAiLlmClient.IHttpClientCall clientCall;

    @NotNull
    private final LlmTimingHolder timingHolder;


    public OpenAiLlmClient(
            @NotNull OpenAiLlmClientInfo llmClientInfo,
            @NotNull IHttpClientCall clientCall,
            @NotNull LlmTimingHolder timingHolder

    ) {
        this.llmClientInfo = llmClientInfo;
        this.clientCall = clientCall;
        this.timingHolder = timingHolder;
    }

    @Override
    public @Nullable String generate(@NotNull ICompletionPrompt<?> prompt) {
        if (!isValid(prompt)) {
            return null;
        }

        final TokenizedCompletionPrompt tokenizedCompletionPrompt = (TokenizedCompletionPrompt) prompt;

        try (
                final HttpClient httpClient = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(20))
                        .build()
        ) {
            final OpenAiLlmClientInlineRequest request = OpenAiLlmClientInlineRequest.builder()
                    .withModel(llmClientInfo.modelName)
                    .withMaxTokens(MAX_TOKENS)
                    .withStop(STOP_TOKENS)
                    .withPrompt(tokenizedCompletionPrompt.getValue())
                    .build();
            final String jsonRequest = GSON.toJson(request);
            final HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(llmClientInfo.modelUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest));

            if (llmClientInfo.apiKey != null) {
                httpRequestBuilder.header("Authorization", "Bearer " + llmClientInfo.apiKey);
            }

            final HttpRequest httpRequest = httpRequestBuilder.build();

            final long startMs = System.currentTimeMillis();
            final HttpResponse<String> httpResponse = clientCall.call(
                    httpClient,
                    httpRequest,
                    request
            );
            final long endMs = System.currentTimeMillis();

            final String responseText;
            if (httpResponse == null) {
                responseText = "// MOCKED ANSWER";
            } else {
                final String body = httpResponse.body();
                int statusCode = httpResponse.statusCode();
                if (statusCode != 200) {
                    LOG.error("Request failed with code: {}; body:{}", statusCode, body);
                    return null;
                }

                final OpenAiLlmClientInlineResponse response = GSON.fromJson(
                        body,
                        OpenAiLlmClientInlineResponse.class
                );

                if (response == null) {
                    LOG.error("Failed to parse response: {}", body);
                    return null;
                }

                if (response.choices.isEmpty()) {
                   LOG.error("Choices are empty!");
                   return null;
                }

                responseText = response.choices.getFirst().text();
            }

            timingHolder.addTimingInfo(
                    new LlmTimingHolder.TimingInfo(
                            endMs - startMs,
                            tokenizedCompletionPrompt.getValue().size(),
                            responseText.length()
                    )
            );

            LOG.debug("Response: {}; Request: {}", responseText, request);

            return responseText;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isValid(@NotNull ICompletionPrompt<?> prompt) {
        if (!(prompt instanceof TokenizedCompletionPrompt tokenizedCompletionPrompt)) {
            throw new IllegalArgumentException("prompt should implement TokenizedCompletionPrompt");
        }

        if (tokenizedCompletionPrompt.getValue().size() + MAX_TOKENS >= llmClientInfo.contextSize) {
            LOG.warn(
                    "Prompt will be ignored: Context overflow(modelSize:{}, promptSize:{}, maxTokens:{})",
                    llmClientInfo.contextSize,
                    tokenizedCompletionPrompt.getValue().size(),
                    MAX_TOKENS
            );
            return false;
        }

        return true;
    }

    // Dumb interface for mocking http calls.
    // Dumb parameters to exclude any other calculations time.
    public interface IHttpClientCall {
        @Nullable
        HttpResponse<String> call(
                @NotNull HttpClient client,
                @NotNull HttpRequest httpRequest,
                @NotNull OpenAiLlmClientInlineRequest request
        ) throws IOException, InterruptedException;
    }

    public static class RealHttpClientCall implements IHttpClientCall {

        @Override
        public @NotNull HttpResponse<String> call(
                @NotNull HttpClient client,
                @NotNull HttpRequest httpRequest,
                @NotNull OpenAiLlmClientInlineRequest request
        ) throws IOException, InterruptedException {
            return client.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );
        }
    }

    public static class MockedHttpClientCall implements IHttpClientCall {
        private static final long MODEL_ANSWER_TIME_MS = 300;
        private static final Random RANDOM = new Random();

        @Override
        public @Nullable HttpResponse<String> call(
                @NotNull HttpClient client,
                @NotNull HttpRequest httpRequest,
                @NotNull OpenAiLlmClientInlineRequest request
        ) throws InterruptedException {
            // 100 tokens prompt => +10ms
            // 2000 tokens prompt => +200ms
            // + random (0ms - 50ms)
            final int additionalTimeMs = request.prompt.size() / 10 + RANDOM.nextInt(50);

            Thread.sleep(MODEL_ANSWER_TIME_MS + additionalTimeMs);
            return null;
        }
    }

    public record OpenAiLlmClientInfo(
            @NotNull String modelName,
            @NotNull String modelUrl,
            int contextSize,
            @Nullable String apiKey
    ) {
        @Override
        public @NotNull String toString() {
            return "OpenAiLlmClientInfo{" +
                    "modelName='" + modelName + '\'' +
                    ", modelUrl='" + modelUrl + '\'' +
                    ", contextSize=" + contextSize +
                    ", apiKey='" + apiKey + '\'' +
                    '}';
        }
    }

    public record OpenAiLlmClientInlineResponse(@NotNull List<Choice> choices) {

        public record Choice(@NotNull String text) {
            @Override
            public @NotNull String toString() {
                return "Choice{" +
                        "text='" + text + '\'' +
                        '}';
            }
        }

        @Override
        public @NotNull String toString() {
            return "OpenAiLlmClientInlineResponse{" +
                    "choices=" + choices +
                    '}';
        }
    }

    public static class OpenAiLlmClientInlineRequest {
        @NotNull
        public final String model;
        @NotNull
        public final List<Long> prompt;
        public final boolean stream;
        @NotNull
        public final List<String> stop;
        public final int max_tokens;
        public final int n;
        public final double temperature;

        public static Builder builder() {
            return new Builder();
        }


        public OpenAiLlmClientInlineRequest(
                @NotNull String model,
                @NotNull List<Long> prompt,
                boolean stream,
                @NotNull List<String> stop,
                int maxTokens,
                int n,
                double temperature
        ) {
            this.model = model;
            this.prompt = prompt;
            this.stream = stream;
            this.stop = stop;
            max_tokens = maxTokens;
            this.n = n;
            this.temperature = temperature;
        }

        @Override
        public String toString() {
            return "OpenAiLlmClientInlineRequest{" +
                    "model='" + model + '\'' +
                    ", prompt=" + prompt +
                    ", stream=" + stream +
                    ", stop=" + stop +
                    ", max_tokens=" + max_tokens +
                    ", n=" + n +
                    ", temperature=" + temperature +
                    '}';
        }

        public static class Builder {
            private String model;
            private List<Long> prompt;
            private boolean stream = false;
            private List<String> stop = STOP_TOKENS;
            private int maxTokens = 100;
            private int n = 1;
            private double temperature = 0.0;

            public Builder() {
            }

            public Builder withModel(@NotNull String model) {
                this.model = model;
                return this;
            }

            public Builder withPrompt(@NotNull List<Long> prompt) {
                this.prompt = prompt;
                return this;
            }

            public Builder withStream(boolean stream) {
                this.stream = stream;
                return this;
            }

            public Builder withStop(@NotNull List<String> stop) {
                this.stop = stop;
                return this;
            }

            public Builder withMaxTokens(int maxTokens) {
                this.maxTokens = maxTokens;
                return this;
            }

            public Builder withN(int n) {
                this.n = n;
                return this;
            }

            public Builder withTemperature(double temperature) {
                this.temperature = temperature;
                return this;
            }

            public OpenAiLlmClientInlineRequest build() {
                return new OpenAiLlmClientInlineRequest(
                        model,
                        prompt,
                        stream,
                        stop,
                        maxTokens,
                        n,
                        temperature
                );
            }
        }
    }
}
