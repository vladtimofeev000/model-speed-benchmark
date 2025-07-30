package com.timofeev.benchmark;

import com.timofeev.llm.OpenAiLlmClient;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class LlmTimingHolder {

    @NotNull
    private final List<TimingInfo> timingInfos;

    public LlmTimingHolder() {
        timingInfos = new ArrayList<>();
    }

    public synchronized void addTimingInfo(
            @NotNull TimingInfo timingInfo
    ) {
        timingInfos.add(timingInfo);
    }

    @NotNull
    public synchronized String getTimingReport() {
        final StringBuilder report = new StringBuilder();
        report.append(
                "timeMs, contextTokensSize, responseCharsSize\n"
        );
        for (TimingInfo timingInfo : timingInfos) {
            report.append(
                    String.format(
                            "%s, %s, %s\n",
                            timingInfo.timeMs,
                            timingInfo.contextTokensSize,
                            timingInfo.responseCharsSize
                    )
            );
        }
        return report.toString();
    }


    public record TimingInfo(long timeMs, int contextTokensSize, int responseCharsSize) {
    }
}
