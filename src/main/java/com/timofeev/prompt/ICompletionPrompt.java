package com.timofeev.prompt;

import org.jetbrains.annotations.NotNull;

public interface ICompletionPrompt<T> {

    @NotNull
    T getValue();
}
