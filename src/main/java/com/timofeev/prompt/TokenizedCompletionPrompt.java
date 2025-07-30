package com.timofeev.prompt;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TokenizedCompletionPrompt implements ICompletionPrompt<List<Long>> {

    @NotNull
    private final List<Long> tokens;

    public TokenizedCompletionPrompt(@NotNull List<Long> tokens) {
        this.tokens = tokens;
    }

    @Override
    public @NotNull List<Long> getValue() {
        return tokens;
    }
}
