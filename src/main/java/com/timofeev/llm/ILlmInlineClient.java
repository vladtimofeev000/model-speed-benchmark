package com.timofeev.llm;

import com.timofeev.prompt.ICompletionPrompt;
import org.jetbrains.annotations.NotNull;

public interface ILlmInlineClient {

    @NotNull
    String generate(@NotNull ICompletionPrompt<?> prompt);
}
