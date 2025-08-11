package com.timofeev.benchmark;

import org.knowm.xchart.*;
import org.knowm.xchart.style.markers.SeriesMarkers;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BenchmarkLineCharts {

    public static void main(String[] args) throws IOException {
        // Read data from CSV (timeMs, contextTokensSize, responseCharsSize)
        List<Double> timeMs = new ArrayList<>();
        List<Double> contextTokens = new ArrayList<>();
        List<Double> responseChars = new ArrayList<>();
        String meta = "";
        String title = "";
        int total = 0;
        double absoluteSum = 0;
        double relativeSum = 0;

        try (Scanner scanner = new Scanner(BenchmarkLineCharts.class.getResourceAsStream("/reports/report_9.csv"))) {
            meta = scanner.nextLine(); // read meta
            System.out.println(meta);
            Map<String, String> params = parseBenchmarkParams(meta);
            title = String.format("Config: %s model: %s ctx size: %s", params.get("gpuConfig"), params.get("modelName"), params.get("contextSize"));
            scanner.nextLine(); // skip empty line
            scanner.nextLine(); // skip header

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.equals("END")) {
                    break;
                }
                String[] parts = line.split(",");
                double time = Double.parseDouble(parts[0]);
                timeMs.add(time);
                double tokens = Double.parseDouble(parts[1]);
                contextTokens.add(tokens);
                responseChars.add(Double.parseDouble(parts[2]));
                total++;
                absoluteSum += time;
                relativeSum += time / tokens;

            }

            String timings = String.format("Avg. time per request %8.2f, per ctx token %8.2f", absoluteSum / total, relativeSum / total);
            title += " " + timings;

/*            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (!line.startsWith("vllm:e2e_request_latency_seconds_bucket")) {
                    continue;
                }
                String[] parts = line.split(",");
                double time = Double.parseDouble(parts[0]);
                timeMs.add(time);
                double tokens = Double.parseDouble(parts[1]);
                contextTokens.add(tokens);
                responseChars.add(Double.parseDouble(parts[2]));
                total++;
                absoluteSum += time;
                relativeSum += time / tokens;

            }*/
        }

        System.out.println("Total count: " + timeMs.size());

        // Calculate combined parameter: contextSize + 4*responseSize
        List<Double> combinedParam = new ArrayList<>();
        for (int i = 0; i < contextTokens.size(); i++) {
            combinedParam.add(contextTokens.get(i) + 4 * responseChars.get(i));
        }

        // Create bins for histogram
        int bins = 20;
        double min = combinedParam.stream().min(Double::compare).get();
        double max = combinedParam.stream().max(Double::compare).get();
        double binSize = (max - min) / bins;

        // Histogram 1: Time vs Context Size
        CategoryChart chart0 = new CategoryChartBuilder()
                .width(1200).height(600)
                .title("Response Time Distribution by Context Size")
                .xAxisTitle("ContextSize")
                .yAxisTitle("Time (ms)")
                .build();

        // Customize chart
        chart0.getStyler().setXAxisLabelRotation(45);
        chart0.getStyler().setAvailableSpaceFill(0.8);
        chart0.getStyler().setOverlapped(true);
        chart0.getStyler().setPlotGridVerticalLinesVisible(false);

        List<Double> xData = new ArrayList<>();
        List<Double> yData = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        List<String> xLabels = new ArrayList<>();

        for (int i = 0; i < bins; i++) {
            double lowerBound = min + i * binSize;
            double upperBound = lowerBound + binSize;
            double avgTime = 0;
            int count = 0;

            for (int j = 0; j < contextTokens.size(); j++) {
                if (contextTokens.get(j) >= lowerBound && contextTokens.get(j) < upperBound) {
                    avgTime += timeMs.get(j);
                    count++;
                }
            }

            if (count > 0) {
                xData.add((lowerBound + upperBound)/2);
                yData.add(avgTime/count);
                counts.add(count);
                xLabels.add(String.format("%.0f-%.0f\n(n=%d)", lowerBound, upperBound, count));
            }
        }

        // Add count labels on bars
        for (int i = 0; i < yData.size(); i++) {
            double yPos = 200; // Offset above bar

            AnnotationText annotation = new AnnotationText(
                    String.valueOf(counts.get(i)), // Text to display (count)
                    i * 72 + 135,                          // X position (category index)
                    yPos,                          // Y position (value coordinate)
                    true                          // Not in screen space
            );
            chart0.addAnnotation(annotation);
        }

        chart0.addSeries("Avg Time", xData, yData);
        chart0.getStyler().setXAxisLabelRotation(45);
        new SwingWrapper<>(chart0).setTitle(title).displayChart();


        // Chart 1: Time vs. Context Size (Line)
        XYChart chart1 = new XYChartBuilder()
                .width(1000).height(600)
                .title("Response Time vs. Context Size")
                .xAxisTitle("Context (tokens)").yAxisTitle("Time (ms)")
                .build();
        chart1.getStyler().setMarkerSize(6);
        chart1.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        chart1.addSeries("Time (ms)", contextTokens, timeMs)
                .setMarker(SeriesMarkers.CIRCLE);
        new SwingWrapper<>(chart1).setTitle(title).displayChart();

        // Chart 2: Time vs. Response Size (Line)
        XYChart chart2 = new XYChartBuilder()
                .width(1000).height(600)
                .title("Response Time vs. Response Size")
                .xAxisTitle("Response (chars)").yAxisTitle("Time (ms)")
                .build();

        chart2.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        chart2.addSeries("Time (ms)", responseChars, timeMs)
                .setMarker(SeriesMarkers.CIRCLE);
        new SwingWrapper<>(chart2).setTitle(title).displayChart();

        // Chart 3: Time vs. Response Size (Line)
        XYChart chart3 = new XYChartBuilder()
                .width(1000).height(600)
                .title("Response Time vs. (Context Size + Response Size)")
                .xAxisTitle("Response (chars)").yAxisTitle("Time (ms)")
                .build();

        chart3.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        chart3.addSeries("Time (ms)", combinedParam, timeMs)
                .setMarker(SeriesMarkers.CIRCLE);
        new SwingWrapper<>(chart3).setTitle(title).displayChart();



        // Save to file
        BitmapEncoder.saveBitmap(chart0, "time_vs_contextSize.png", BitmapEncoder.BitmapFormat.PNG);
        BitmapEncoder.saveBitmap(chart1, "time_vs_context_line.png", BitmapEncoder.BitmapFormat.PNG);
        BitmapEncoder.saveBitmap(chart2, "time_vs_response_line.png", BitmapEncoder.BitmapFormat.PNG);
        BitmapEncoder.saveBitmap(chart3, "combined_line.png", BitmapEncoder.BitmapFormat.PNG);
    }

    public static Map<String, String> parseBenchmarkParams(String input) {
        Map<String, String> params = new HashMap<>();

        // Find the content between curly braces
        int startIndex = input.indexOf('{');
        if (startIndex == -1) {
            return params;
        }

        String content = input.substring(startIndex + 1, input.length() - 1).trim();

        // Regex to match key-value pairs
        Pattern pattern = Pattern.compile("([a-zA-Z]+)=([^,]+)");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = matcher.group(2).trim();

            // Remove surrounding quotes if present
            if (value.startsWith("'") && value.endsWith("'") ||
                    value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }

            // Handle "null" string
            if ("null".equalsIgnoreCase(value)) {
                value = null;
            }

            params.put(key, value);
        }

        return params;
    }

}