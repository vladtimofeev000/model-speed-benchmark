package com.timofeev.prompt;

import com.timofeev.llm.OpenAiLlmClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepoEvalQwenPromptComputer implements IInlinePromptComputer {
    private static final Logger LOG = LoggerFactory.getLogger(RepoEvalQwenPromptComputer.class);

    @NotNull
    public static final String FILE_SEPARATOR_TOKEN = "<|file_sep|>";

    @NotNull
    public static final String PREFIX_TOKEN = "<|fim_prefix|>";

    @NotNull
    public static final String SUFFIX_TOKEN = "<|fim_suffix|>";

    @NotNull
    public static final String FIM_TOKEN = "<|fim_middle|>";


    @Override
    public @Nullable String computeInlinePrompt(@NotNull String promptJsonData) {
        final RepoEvalQwenPromptComputer.DatasetRow datasetRow = OpenAiLlmClient.GSON.fromJson(
                promptJsonData,
                RepoEvalQwenPromptComputer.DatasetRow.class
        );
        if (datasetRow == null) {
            LOG.error("Failed to parse json line: {}", promptJsonData);
            return null;
        }

        final String promptText = insertLine(
                datasetRow.prompt,
                SUFFIX_TOKEN,
                datasetRow.metadata.line_no + 1
        );

        return FILE_SEPARATOR_TOKEN + "\n" + PREFIX_TOKEN + promptText + FIM_TOKEN;
    }

    @NotNull
    public static String insertLine(
            @NotNull String original,
            @NotNull String textToInsert,
            int lineNumber
    ) {
        String[] lines = original.split("\n", -1); // -1 keeps trailing empty strings

        if (lineNumber < 1 || lineNumber > lines.length + 1) {
            throw new IllegalArgumentException("Invalid line number");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == lineNumber - 1) {
                sb.append(textToInsert).append("\n");
            }
            sb.append(lines[i]);
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }

        // Handle insertion after last line
        if (lineNumber == lines.length + 1) {
            sb.append(textToInsert);
        }

        return sb.toString();
    }

    public static class DatasetRow {
        @NotNull
        public final String prompt;

        @NotNull
        public final MetaData metadata;

        public DatasetRow(
                @NotNull String prompt,
                @NotNull MetaData metadata
        ) {
            this.prompt = prompt;
            this.metadata = metadata;
        }

        @Override
        public String toString() {
            return "DatasetRow{" +
                    "prompt='" + prompt + '\'' +
                    ", metaData=" + metadata +
                    '}';
        }

        public static class MetaData {
            public final int line_no;

            public MetaData(int lineNo) {
                line_no = lineNo;
            }

            @Override
            public String toString() {
                return "MetaData{" +
                        "line_no=" + line_no +
                        '}';
            }
        }
    }
}
