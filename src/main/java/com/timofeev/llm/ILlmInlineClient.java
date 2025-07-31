package com.timofeev.llm;

import com.timofeev.prompt.ICompletionPrompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ILlmInlineClient {

    @Nullable
    String generate(@NotNull ICompletionPrompt<?> prompt);
}
