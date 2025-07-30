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

public class OpenAiLlmClient implements ILlmInlineClient {
    private static final Logger LOG = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private static final Gson GSON = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .setStrictness(Strictness.LENIENT)
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();

    private static final int MAX_TOKENS = 100;

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
    public @NotNull String generate(@NotNull ICompletionPrompt<?> prompt) {
        validate(prompt);

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
                    .withStop(List.of("\n"))
                    .withPrompt(tokenizedCompletionPrompt.getValue())
                    .build();
            final String jsonRequest = GSON.toJson(request);
            final HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(llmClientInfo.apiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonRequest));

            if (llmClientInfo.apiKey != null) {
                httpRequestBuilder.header("Authorization", "Bearer " + llmClientInfo.apiKey);
            }

            final HttpRequest httpRequest = httpRequestBuilder.build();

            final long startMs = System.currentTimeMillis();
            final HttpResponse<String> httpResponse = clientCall.call(
                    httpClient,
                    httpRequest
            );
            final long endMs = System.currentTimeMillis();

            final OpenAiLlmClientInlineResponse response = GSON.fromJson(
                    httpResponse.body(),
                    OpenAiLlmClientInlineResponse.class
            );

            if (response.choices.isEmpty()) {
                throw new RuntimeException("Choices empty");
            }

            final String responseText = response.choices.getFirst().text();

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

    private void validate(@NotNull ICompletionPrompt<?> prompt) {
        if (!(prompt instanceof TokenizedCompletionPrompt tokenizedCompletionPrompt)) {
            throw new IllegalArgumentException("prompt should implement TokenizedCompletionPrompt");
        }

        if (tokenizedCompletionPrompt.getValue().size() + MAX_TOKENS >= llmClientInfo.contextSize) {
            throw new IllegalArgumentException(
                    String.format(
                            "Context overflow(modelSize:%s, promptSize:%s, maxTokens:%s)",
                            llmClientInfo.contextSize,
                            tokenizedCompletionPrompt.getValue().size(),
                            MAX_TOKENS
                    )
            );
        }

    }

    //Dumb interface for mocking http calls
    public interface IHttpClientCall {
        @NotNull
        HttpResponse<String> call(
                @NotNull HttpClient client,
                @NotNull HttpRequest httpRequest
        ) throws IOException, InterruptedException;
    }

    public static class RealHttpClientCall implements IHttpClientCall{

        @Override
        public @NotNull HttpResponse<String> call(@NotNull HttpClient client, @NotNull HttpRequest httpRequest) throws IOException, InterruptedException {
            return client.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString()
            );
        }
    }

    public record OpenAiLlmClientInfo(
            @NotNull String modelName,
            int contextSize,
            @NotNull String apiUrl,
            @Nullable String apiKey
    ) {
        @Override
        public @NotNull String toString() {
            return "OpenAiLlmClientInfo{" +
                    "modelName='" + modelName + '\'' +
                    ", contextSize=" + contextSize +
                    ", apiUrl='" + apiUrl + '\'' +
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
            private List<String> stop;
            private int maxTokens;
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
