package com.timofeev;


import com.timofeev.benchmark.Benchmark;
import org.apache.commons.cli.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        printWelcomeMessage();

        Benchmark.BenchmarkParams params = parseArguments(args);
        if (params == null) {
            params = interactiveConfig();
        }

        System.out.println("\nConfiguration complete. Starting LLM client with:");
        System.out.println(params);

        try {
            Benchmark.run(params);
        } catch (RuntimeException exception) {
            LOG.error("Benchmark failed", exception);
        }
    }

    private static Benchmark.BenchmarkParams parseArguments(String[] args) {
        final Options options = new Options();

        options.addOption(Option.builder("sl")
                .longOpt("sample-limit")
                .hasArg()
                .type(Integer.class)
                .desc("Sample limit")
                .build());

        options.addOption(Option.builder("t")
                .longOpt("threads")
                .hasArg()
                .type(Integer.class)
                .desc("Thread count")
                .build());

        options.addOption(Option.builder("d")
                .longOpt("delay")
                .hasArg()
                .type(Long.class)
                .desc("Delay in ms")
                .build());

        options.addOption(Option.builder("g")
                .longOpt("gpu")
                .hasArg()
                .type(String.class)
                .desc("GPU config")
                .build());

        options.addOption(Option.builder("m")
                .longOpt("model")
                .hasArg()
                .type(String.class)
                .required()
                .desc("Model name")
                .build());

        options.addOption(Option.builder("u")
                .longOpt("url")
                .hasArg()
                .type(String.class)
                .required()
                .desc("Model URL")
                .build());

        options.addOption(Option.builder("k")
                .longOpt("key")
                .hasArg()
                .type(String.class)
                .desc("API key")
                .build());

        options.addOption(Option.builder("c")
                .longOpt("context")
                .hasArg()
                .type(Integer.class)
                .required()
                .desc("Context size")
                .build());

        options.addOption(Option.builder("tk")
                .longOpt("tokenizer")
                .hasArg()
                .type(String.class)
                .required()
                .desc("Tokenizer path")
                .build());

        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine cmd = parser.parse(options, args);

            return Benchmark.BenchmarkParams.builder()
                    .withSampleLimit(cmd.getParsedOptionValue("sl"))
                    .withThreads(cmd.getParsedOptionValue("t"))
                    .withDelayMs(cmd.getParsedOptionValue("d"))
                    .withGpuConfig(cmd.getOptionValue("g"))
                    .withModelName(cmd.getOptionValue("m"))
                    .withModelUrl(cmd.getOptionValue("u"))
                    .withApiKey(cmd.getOptionValue("k"))
                    .withContextSize(cmd.getParsedOptionValue("c"))
                    .withTokenizerPath(cmd.getOptionValue("tk"))
                    .build();
        } catch (ParseException e) {
            System.err.println("Error parsing command line: " + e.getMessage());
            return null;
        }
    }

    private static Benchmark.BenchmarkParams interactiveConfig() {
        final Scanner scanner = new Scanner(System.in);
        final Benchmark.Builder builder = Benchmark.BenchmarkParams.builder();

        System.out.print("Sample limit [null]: ");
        String sampleLimit = scanner.nextLine().trim();
        builder.withSampleLimit(sampleLimit.isEmpty() ? null : Integer.parseInt(sampleLimit));

        System.out.print("Thread count [1]: ");
        String threads = scanner.nextLine().trim();
        builder.withThreads(threads.isEmpty() ? null : Integer.parseInt(threads));

        System.out.print("Delay between requests (ms) [0]: ");
        String delay = scanner.nextLine().trim();
        builder.withDelayMs(delay.isEmpty() ? null : Long.parseLong(delay));

        System.out.print("GPU configuration [null]: ");
        builder.withGpuConfig(getInputOrNull(scanner));

        System.out.print("Model name (required): ");
        String modelName = scanner.nextLine().trim();
        while (modelName.isEmpty()) {
            System.out.print("Model name is required: ");
            modelName = scanner.nextLine().trim();
        }
        builder.withModelName(modelName);

        System.out.print("Model URL (required): ");
        String modelUrl = scanner.nextLine().trim();
        while (modelUrl.isEmpty()) {
            System.out.print("Model URL is required: ");
            modelUrl = scanner.nextLine().trim();
        }
        builder.withModelUrl(modelUrl);

        System.out.print("API key [null]: ");
        builder.withApiKey(getInputOrNull(scanner));

        System.out.print("Context size (required): ");
        int contextSize;
        while (true) {
            String context = scanner.nextLine().trim();
            if (!context.isEmpty()) {
                try {
                    contextSize = Integer.parseInt(context);
                    break;
                } catch (NumberFormatException e) {
                    System.out.print("Invalid number. Context size: ");
                }
            } else {
                System.out.print("Context size is required: ");
            }
        }
        builder.withContextSize(contextSize);

        System.out.print("Path to tokenizer.json (required): ");
        String tokenizerPath = scanner.nextLine().trim();
        while (tokenizerPath.isEmpty()) {
            System.out.print("Tokenizer path is required: ");
            tokenizerPath = scanner.nextLine().trim();
        }
        builder.withTokenizerPath(tokenizerPath);

        return builder.build();
    }

    @Nullable
    private static String getInputOrNull(@NotNull Scanner scanner) {
        final String input = scanner.nextLine().trim();
        return input.isEmpty() ? null : input;
    }

    private static void printWelcomeMessage() {
        System.out.println(
            """
            *********************************************
            *  LLM Client Configuration                 *
            *  Provide parameters via command line or   *
            *  enter them interactively below           *
            *********************************************
            Parameters:
            1) Sample limit (default: null)
            2) Thread count for parallelism (default: 1)
            3) Delay between requests in ms (default: 0)
            4) GPU configuration string (default: null)
            5) Model name (required)
            6) Model URL (required)
            7) API key (default: null)
            8) Context size (required)
            9) Path to tokenizer.json (required)
            """
        );
    }
}