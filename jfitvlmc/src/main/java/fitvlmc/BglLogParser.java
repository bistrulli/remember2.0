package fitvlmc;

import fitvlmc.HdfsLogParser.HdfsSession;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BglLogParser {

    private final int windowSize;

    public BglLogParser(int windowSize) {
        this.windowSize = windowSize;
    }

    public BglLogParser() {
        this(20);
    }

    public List<HdfsSession> parseLog(File logFile) throws IOException {
        List<String> allEvents = new ArrayList<>();
        List<Boolean> allAnomalies = new ArrayList<>();

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new FileInputStream(logFile), StandardCharsets.UTF_8));

        String line;
        long lineCount = 0;
        long skipped = 0;
        while ((line = reader.readLine()) != null) {
            lineCount++;
            if (lineCount % 1000000 == 0) {
                System.out.printf("  Parsed %dM lines...%n", lineCount / 1000000);
            }

            ParsedLine parsed = parseLine(line);
            if (parsed == null) {
                skipped++;
                continue;
            }

            allEvents.add(parsed.eventType);
            allAnomalies.add(parsed.isAnomaly);
        }
        reader.close();

        System.out.printf(
                "  Total: %d lines, %d valid, %d skipped%n", lineCount, allEvents.size(), skipped);

        return buildWindows(allEvents, allAnomalies);
    }

    public ParsedLine parseLine(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }

        String[] fields = line.split("\\s+");
        if (fields.length < 9) {
            return null;
        }

        String label = fields[0];
        boolean isAnomaly = !"-".equals(label);

        String component = fields[7];
        String level = fields[8];

        if (!component.matches("[A-Z_]+") || !level.matches("[A-Z]+")) {
            return null;
        }

        String eventType = component + "_" + level;
        return new ParsedLine(eventType, isAnomaly);
    }

    public List<HdfsSession> buildWindows(List<String> events, List<Boolean> anomalies) {
        List<HdfsSession> sessions = new ArrayList<>();
        int windowIdx = 0;

        for (int i = 0; i + windowSize <= events.size(); i += windowSize) {
            List<String> windowEvents = new ArrayList<>(events.subList(i, i + windowSize));
            boolean hasAnomaly = false;
            for (int j = i; j < i + windowSize; j++) {
                if (anomalies.get(j)) {
                    hasAnomaly = true;
                    break;
                }
            }

            HdfsSession session = new HdfsSession("W_" + windowIdx, windowEvents);
            session.isAnomaly = hasAnomaly;
            sessions.add(session);
            windowIdx++;
        }

        return sessions;
    }

    public static class ParsedLine {
        public final String eventType;
        public final boolean isAnomaly;

        ParsedLine(String eventType, boolean isAnomaly) {
            this.eventType = eventType;
            this.isAnomaly = isAnomaly;
        }
    }
}
