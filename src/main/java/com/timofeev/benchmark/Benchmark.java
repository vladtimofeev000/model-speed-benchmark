package com.timofeev.benchmark;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.timofeev.llm.OpenAiLlmClient;
import com.timofeev.prompt.IInlinePromptComputer;
import com.timofeev.prompt.RepoEvalQwenPromptComputer;
import com.timofeev.prompt.TokenizedCompletionPrompt;
import org.apache.commons.collections4.ListUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class Benchmark {
    private static final Logger LOG = LoggerFactory.getLogger(Benchmark.class);

    private static final Random RANDOM = new Random(0);

    private static final Map<String, String> DEFAULT_TOKENIZER_OPTIONS = Map.of(
            "padding", "true",
            "modelMaxLength", "32000"
    );

    private static final IInlinePromptComputer PROMPT_COMPUTER = new RepoEvalQwenPromptComputer();

    public static void run(@NotNull BenchmarkParams params) throws IOException {
        final LlmTimingHolder timingHolder = new LlmTimingHolder();
        final OpenAiLlmClient llmClient = getLlmClient(
                params,
                timingHolder
        );

        final List<TokenizedCompletionPrompt> tokenizedPrompts = getTokenizedPrompts(
                params
        );

        LOG.info("Parsed prompts number: {}", tokenizedPrompts.size());

        final List<List<TokenizedCompletionPrompt>> batches = ListUtils.partition(
                tokenizedPrompts,
                tokenizedPrompts.size() / params.threads
        );

        final List<CompletableFuture<Void>> futures = new ArrayList<>(batches.size());
        try (
                final ExecutorService executor = Executors.newFixedThreadPool(params.threads)
        ) {
            AtomicInteger counter = new AtomicInteger(0);
            for (List<TokenizedCompletionPrompt> batch : batches) {
                final CompletableFuture<Void> future = CompletableFuture.runAsync(
                        () -> compute(
                                params,
                                llmClient,
                                batch,
                                counter,
                                batches.stream().mapToLong(Collection::size).sum()
                                ),
                        executor
                );
                futures.add(future);
            }

            final CompletableFuture<Void> mergedFuture = CompletableFuture.allOf(
                    futures.toArray(CompletableFuture[]::new)
            );

            mergedFuture.join();
        } finally {
            final StringBuilder report = new StringBuilder();

            report.append("INFO: ").append(params).append("\n\n");

            report.append(timingHolder.getTimingReport());

            LOG.info("Report: \n\n {}", report);

            final File reportFile = new File("report.csv");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportFile))) {
                writer.write(report.toString());
            }

            LOG.info("Report saved to: {}", reportFile.getAbsolutePath());
        }
    }

    private static @NotNull OpenAiLlmClient getLlmClient(@NotNull BenchmarkParams params, LlmTimingHolder timingHolder) {
        final OpenAiLlmClient.OpenAiLlmClientInfo clientInfo = new OpenAiLlmClient.OpenAiLlmClientInfo(
                params.modelName,
                params.modelUrl,
                params.contextSize,
                params.apiKey
        );

        final OpenAiLlmClient.IHttpClientCall clientCall;
        if (params.mock) {
            clientCall = new OpenAiLlmClient.MockedHttpClientCall();
        } else {
            clientCall = new OpenAiLlmClient.RealHttpClientCall();
        }
        final OpenAiLlmClient llmClient = new OpenAiLlmClient(
                clientInfo,
                clientCall,
                timingHolder
        );
        return llmClient;
    }

    private static void compute(
            @NotNull BenchmarkParams params,
            @NotNull OpenAiLlmClient llmClient,
            @NotNull List<TokenizedCompletionPrompt> batch,
            @NotNull AtomicInteger counter,
            long totalPrompts
            ) {
        for (TokenizedCompletionPrompt prompt : batch) {
            llmClient.generate(prompt);
            try {
                Thread.sleep(params.delayMs);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            int i = counter.incrementAndGet();
            if (i % 100 == 0) {
                LOG.info("Processed {}/{}", i, totalPrompts);
            }
        }
    }

    @NotNull
    private static List<TokenizedCompletionPrompt> getTokenizedPrompts(
            @NotNull BenchmarkParams params
    ) throws IOException {

        final HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.newInstance(
                Files.newInputStream(params.tokenizer.toPath()),
                DEFAULT_TOKENIZER_OPTIONS
        );

        final List<String> prompts = new ArrayList<>();

        try (
                final Stream<String> lines = Files.lines(params.dataset.toPath())
        ) {
            lines.forEach(line -> {
                        String prompt = PROMPT_COMPUTER.computeInlinePrompt(line);
                        if (prompt != null) {
                            prompts.add(prompt);
                        }
                    }
            );
        }

        List<String> promptsShuffled = new ArrayList<>(prompts);

        Collections.shuffle(
                promptsShuffled,
                RANDOM
        );

        if (params.sampleLimit != null) {
            promptsShuffled = promptsShuffled.subList(0, params.sampleLimit);
        }

        final List<TokenizedCompletionPrompt> tokenizedPrompts = new ArrayList<>();

        for (String prompt : promptsShuffled) {
            final List<Long> tokens = new ArrayList<>();
            final Encoding encoding = tokenizer.encode(prompt);
            for (long id : encoding.getIds()) {
                tokens.add(id);
            }
            tokenizedPrompts.add(new TokenizedCompletionPrompt(tokens, prompt));
        }

        return tokenizedPrompts;
    }


    public static class BenchmarkParams {
        // null means unlimited
        @Nullable
        public final Integer sampleLimit;
        public final int threads;
        public final long delayMs;
        @Nullable
        public final String gpuConfig;
        @NotNull
        public final String modelName;
        @NotNull
        public final String modelUrl;
        @Nullable
        public final String apiKey;
        public final int contextSize;
        @NotNull
        public final File tokenizer;
        @NotNull
        public final File dataset;
        public final boolean mock;

        public BenchmarkParams(
                @Nullable Integer sampleLimit,
                int threads,
                long delayMs,
                @Nullable String gpuConfig,
                @NotNull String modelName,
                @NotNull String modelUrl,
                @Nullable String apiKey,
                int contextSize,
                @NotNull File tokenizer,
                @NotNull File dataset,
                boolean mock
        ) {
            this.sampleLimit = sampleLimit;
            this.threads = threads;
            this.delayMs = delayMs;
            this.gpuConfig = gpuConfig;
            this.modelName = modelName;
            this.modelUrl = modelUrl;
            this.apiKey = apiKey;
            this.contextSize = contextSize;
            this.tokenizer = tokenizer;
            this.dataset = dataset;
            this.mock = mock;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        public String toString() {
            return "BenchmarkParams{" +
                    "sampleLimit=" + sampleLimit +
                    ", threads=" + threads +
                    ", delayMs=" + delayMs +
                    ", gpuConfig='" + gpuConfig + '\'' +
                    ", modelName='" + modelName + '\'' +
                    ", modelUrl='" + modelUrl + '\'' +
                    ", apiKey='" + apiKey + '\'' +
                    ", contextSize=" + contextSize +
                    ", tokenizer=" + tokenizer +
                    ", dataset=" + dataset +
                    ", mock=" + mock +
                    '}';
        }
    }

    public static class Builder {
        @Nullable
        public final static Integer SAMPLE_LIMIT_DEFAULT = null;
        public final static int THREADS_DEFAULT = 1;
        public final static long DELAY_MS_DEFAULT = 0;
        @Nullable
        public final static String API_KEY_DEFAULT = null;
        @NotNull
        public final static File TOKENIZER_DEFAULT = new File(
                Objects.requireNonNull(Builder.class.getResource("/tokenizer/Qwen2.5-Coder-14B.json")).getPath()
        );
        @NotNull
        public final static File DATASET_DEFAULT = new File(
                Objects.requireNonNull(Builder.class.getResource("/dataset/repoeval/line_level.java.test.jsonl")).getFile()
        );

        public final static boolean MOCK_DEFAULT = false;

        private Integer sampleLimit = SAMPLE_LIMIT_DEFAULT;
        private Integer threads = THREADS_DEFAULT;
        private Long delayMs = DELAY_MS_DEFAULT;
        private String gpuConfig;
        private String modelName;
        private String modelUrl;
        private String apiKey = API_KEY_DEFAULT;
        private Integer contextSize;
        private File tokenizer;
        private File dataset;
        private Boolean mock;

        public Builder withSampleLimit(@Nullable Integer sampleLimit) {
            this.sampleLimit = sampleLimit;
            return this;
        }

        public Builder withThreads(@Nullable Integer threads) {
            this.threads = threads;
            return this;
        }

        public Builder withDelayMs(@Nullable Long delayMs) {
            this.delayMs = delayMs;
            return this;
        }

        public Builder withGpuConfig(@Nullable String gpuConfig) {
            this.gpuConfig = gpuConfig == null || gpuConfig.isEmpty() ? null : gpuConfig;
            return this;
        }

        public Builder withModelName(@Nullable String modelName) {
            this.modelName = modelName == null || modelName.isEmpty() ? null : modelName;
            return this;
        }

        public Builder withModelUrl(@Nullable String modelUrl) {
            this.modelUrl = modelUrl == null || modelUrl.isEmpty() ? null : modelUrl;
            return this;
        }

        public Builder withApiKey(@Nullable String apiKey) {
            this.apiKey = apiKey == null || apiKey.isEmpty() ? null : apiKey;
            return this;
        }

        public Builder withContextSize(@Nullable Integer contextSize) {
            this.contextSize = contextSize;
            return this;
        }

        public Builder withTokenizer(@Nullable File tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public Builder withTokenizer(@Nullable String tokenizerStr) {
            this.tokenizer = tokenizerStr == null || tokenizerStr.isEmpty() ? null : new File(tokenizerStr);
            return this;
        }

        public Builder withDataset(@Nullable File dataset) {
            this.dataset = dataset;
            return this;
        }

        public Builder withDataset(@Nullable String datasetStr) {
            this.dataset = datasetStr == null || datasetStr.isEmpty() ? null : new File(datasetStr);
            return this;
        }

        public Builder withMock(@Nullable Boolean mock) {
            this.mock = mock;
            return this;
        }

        public BenchmarkParams build() {
            if (sampleLimit == null) {
                sampleLimit = SAMPLE_LIMIT_DEFAULT;
            }
            if (threads == null) {
                threads = THREADS_DEFAULT;
            }
            if (delayMs == null) {
                delayMs = DELAY_MS_DEFAULT;
            }
            if (apiKey == null) {
                apiKey = API_KEY_DEFAULT;
            }
            if (tokenizer == null) {
                tokenizer = TOKENIZER_DEFAULT;
            }
            if (dataset == null) {
                dataset = DATASET_DEFAULT;
            }
            if (mock == null) {
                mock = MOCK_DEFAULT;
            }
            if (modelName == null) {
                throw new IllegalArgumentException("modelName can't be null");
            }
            if (modelUrl == null) {
                throw new IllegalArgumentException("modelUrl can't be null");
            }
            if (contextSize == null) {
                throw new IllegalArgumentException("contextSize can't be null");
            }
            if (!tokenizer.exists()) {
                throw new IllegalArgumentException("tokenizer can't be found: " + tokenizer.getAbsolutePath());
            }
            if (!dataset.exists()) {
                throw new IllegalArgumentException("dataset can't be found: " + dataset.getAbsolutePath());
            }
            return new BenchmarkParams(
                    sampleLimit,
                    threads,
                    delayMs,
                    gpuConfig,
                    modelName,
                    modelUrl,
                    apiKey,
                    contextSize,
                    tokenizer,
                    dataset,
                    mock
            );
        }
    }
}
