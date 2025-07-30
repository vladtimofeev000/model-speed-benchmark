package com.timofeev.benchmark;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Benchmark {

    public static void run(@NotNull BenchmarkParams params) {
        report.append(
                String.format(
                        "LLM info (model: %s; url: %s)\n",
                        llmClientInfo.modelName(),
                        llmClientInfo.apiUrl()
                )
        );
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
        public final String tokenizerPath;

        public BenchmarkParams(
                @Nullable Integer sampleLimit,
                int threads,
                long delayMs,
                @Nullable String gpuConfig,
                @NotNull String modelName,
                @NotNull String modelUrl,
                @Nullable String apiKey,
                int contextSize,
                @NotNull String tokenizerPath
        ) {
            this.sampleLimit = sampleLimit;
            this.threads = threads;
            this.delayMs = delayMs;
            this.gpuConfig = gpuConfig;
            this.modelName = modelName;
            this.modelUrl = modelUrl;
            this.apiKey = apiKey;
            this.contextSize = contextSize;
            this.tokenizerPath = tokenizerPath;
        }

        public static Builder builder() {
            return new Builder();
        }
    }

    public static class Builder {
        @Nullable
        public final static Integer SAMPLE_LIMIT_DEFAULT = null;
        public final static int THREADS_DEFAULT = 1;
        public final static long DELAY_MS_DEFAULT = 0;
        @Nullable
        public final static String API_KEY_DEFAULT = null;

        private Integer sampleLimit = SAMPLE_LIMIT_DEFAULT;
        private Integer threads = THREADS_DEFAULT;
        private Long delayMs = DELAY_MS_DEFAULT;
        private String gpuConfig;
        private String modelName;
        private String modelUrl;
        private String apiKey = API_KEY_DEFAULT;
        private Integer contextSize;
        private String tokenizerPath;

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
            this.gpuConfig = gpuConfig;
            return this;
        }

        public Builder withModelName(@NotNull String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder withModelUrl(@NotNull String modelUrl) {
            this.modelUrl = modelUrl;
            return this;
        }

        public Builder withApiKey(@Nullable String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder withContextSize(@NotNull Integer contextSize) {
            this.contextSize = contextSize;
            return this;
        }

        public Builder withTokenizerPath(@NotNull String tokenizerPath) {
            this.tokenizerPath = tokenizerPath;
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
            if (modelName == null) {
                throw new IllegalArgumentException("modelName can't be null");
            }
            if (modelUrl == null) {
                throw new IllegalArgumentException("modelUrl can't be null");
            }
            if (contextSize == null) {
                throw new IllegalArgumentException("contextSize can't be null");
            }
            if (tokenizerPath == null) {
                throw new IllegalArgumentException("tokenizerPath can't be null");
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
                    tokenizerPath
            );
        }
    }
}
