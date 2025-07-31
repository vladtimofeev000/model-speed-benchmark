package com.timofeev;

import com.timofeev.benchmark.Benchmark;
import org.apache.commons.cli.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Scanner;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException {
        printWelcomeMessage();

        Benchmark.BenchmarkParams params = parseArguments(args);
        if (params == null) {
            params = interactiveConfig();
        }

        System.out.println("Configuration complete. Starting LLM client with: " + params);

        try {
            Benchmark.run(params);
        } catch (Exception exception) {
            LOG.error("Benchmark failed", exception);
        }
    }

    private static Benchmark.BenchmarkParams parseArguments(String[] args) throws InterruptedException {
        final Options options = new Options();

        options.addOption("h", "help", false, "Display help information");

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

        options.addOption(Option.builder("cs")
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
                .desc("Tokenizer path")
                .build());

        options.addOption(Option.builder("ds")
                .longOpt("dataset")
                .hasArg()
                .type(String.class)
                .desc("Dataset path")
                .build());

        options.addOption(Option.builder("mck")
                .longOpt("mock")
                .hasArg()
                .type(Boolean.class)
                .desc("Use mocked model")
                .build());

        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("h")) {
                printWelcomeMessage();
            }

            return Benchmark.BenchmarkParams.builder()
                    .withSampleLimit(cmd.getParsedOptionValue("sl"))
                    .withThreads(cmd.getParsedOptionValue("t"))
                    .withDelayMs(cmd.getParsedOptionValue("d"))
                    .withGpuConfig(cmd.getOptionValue("g"))
                    .withModelName(cmd.getOptionValue("m"))
                    .withModelUrl(cmd.getOptionValue("u"))
                    .withApiKey(cmd.getOptionValue("k"))
                    .withContextSize(cmd.getParsedOptionValue("cs"))
                    .withTokenizer(cmd.getOptionValue("tk"))
                    .withDataset(cmd.getOptionValue("ds"))
                    .withMock(cmd.getParsedOptionValue("mck"))
                    .build();
        } catch (ParseException e) {
            LOG.error("Error parsing command line: {}", e.getMessage());
            System.out.println("Interactive mode will be used.\n\n");
            Thread.sleep(100);
            return null;
        }
    }

    private static Benchmark.BenchmarkParams interactiveConfig() {
        final Scanner scanner = new Scanner(System.in);
        final Benchmark.Builder builder = Benchmark.BenchmarkParams.builder();

        System.out.println("\n=======================================\n");

        System.out.print("Sample limit [default: null]: ");
        String sampleLimit = scanner.nextLine().trim();
        builder.withSampleLimit(sampleLimit.isEmpty() ? null : Integer.parseInt(sampleLimit));

        System.out.print("Thread count [default: 1]: ");
        String threads = scanner.nextLine().trim();
        builder.withThreads(threads.isEmpty() ? null : Integer.parseInt(threads));

        System.out.print("Delay between requests (ms) [default: 0]: ");
        String delay = scanner.nextLine().trim();
        builder.withDelayMs(delay.isEmpty() ? null : Long.parseLong(delay));

        System.out.print("GPU configuration [default: null]: ");
        builder.withGpuConfig(getInputOrNull(scanner));

        System.out.print("Model name [required]: ");
        String modelName = scanner.nextLine().trim();
        while (modelName.isEmpty()) {
            System.out.print("Model name is required: ");
            modelName = scanner.nextLine().trim();
        }
        builder.withModelName(modelName);

        System.out.print("Model URL [required]: ");
        String modelUrl = scanner.nextLine().trim();
        while (modelUrl.isEmpty()) {
            System.out.print("Model URL is required: ");
            modelUrl = scanner.nextLine().trim();
        }
        builder.withModelUrl(modelUrl);

        System.out.print("API key [default: null]: ");
        builder.withApiKey(getInputOrNull(scanner));

        System.out.print("Context size [required]: ");
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

        System.out.print("Path to tokenizer json [default: Qwen2.5-Coder-14B tokenizer]: ");
        String tokenizer = scanner.nextLine().trim();
        while (tokenizer != null && !tokenizer.isEmpty() && !new File(tokenizer).exists()) {
            System.out.print("tokenizer can't be found: " + new File(tokenizer).getAbsolutePath());
            tokenizer = getInputOrNull(scanner);
        }
        builder.withTokenizer(tokenizer);

        System.out.print("Path to dataset json [default: repoeval line-level]: ");
        String dataset = getInputOrNull(scanner);
        while (dataset != null && !dataset.isEmpty() && !new File(tokenizer).exists()) {
            System.out.print("dataset can't be found: " + new File(dataset).getAbsolutePath());
            dataset = getInputOrNull(scanner);
        }
        builder.withDataset(dataset);

        System.out.print("Use mocked model [default: false]: ");
        final String mock = scanner.nextLine().trim();
        builder.withMock(mock.isEmpty() ? null : Boolean.parseBoolean(mock));

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
                        
                        [-h] to show this message.
                        
                        Parameters:
                        1) Sample limit (default: null) [-sl]
                        2) Thread count for parallelism (default: 1) [-t]
                        3) Delay between requests in ms (default: 0) [-d]
                        4) GPU configuration string (default: null) [-g]
                        5) Model name (required) [-m]
                        6) Model URL (required) [-u]
                        7) API key (default: null) [-k]
                        8) Context size (required) [-cs]
                        9) Path to tokenizer json (default: Qwen2.5-Coder-14B tokenizer) [-tk]
                        10) Path to dataset json (default: repoEval line-level) [-ds]
                        11) Use mocked model (default: false) [-mck]
                        
                        
                        """
        );
    }
}