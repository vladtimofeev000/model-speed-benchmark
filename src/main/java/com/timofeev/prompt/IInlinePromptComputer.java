package com.timofeev.prompt;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IInlinePromptComputer {

    @Nullable
    String computeInlinePrompt(@NotNull String promptJsonData);

}
