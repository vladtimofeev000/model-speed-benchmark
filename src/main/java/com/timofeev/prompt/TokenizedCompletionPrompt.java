package com.timofeev.prompt;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TokenizedCompletionPrompt implements ICompletionPrompt<List<Long>> {

    @NotNull
    private final List<Long> tokens;

    public final String promptStr;

    public TokenizedCompletionPrompt(@NotNull List<Long> tokens, String promptStr) {
        this.tokens = tokens;
        this.promptStr = promptStr;
    }

    @Override
    public @NotNull List<Long> getValue() {
        return tokens;
    }
}
