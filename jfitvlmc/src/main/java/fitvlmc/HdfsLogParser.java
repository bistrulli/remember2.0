package fitvlmc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HdfsLogParser {

    public static class HdfsSession {
        public final String blockId;
        public final List<String> events;
        public boolean isAnomaly;

        public HdfsSession(String blockId, List<String> events) {
            this.blockId = blockId;
            this.events = events;
            this.isAnomaly = false;
        }
    }

    public List<HdfsSession> parseStructuredLog(File csvFile) throws IOException {
        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8));

        String headerLine = reader.readLine();
        if (headerLine == null || headerLine.trim().isEmpty()) {
            reader.close();
            throw new IOException("CSV file is empty or has no header");
        }

        String[] headers = headerLine.split(",", -1);
        for (int i = 0; i < headers.length; i++) {
            headers[i] = headers[i].trim().replace("\"", "");
        }

        int blockIdx = findColumnIndex(headers, "BlockId");
        int eventIdx = findColumnIndex(headers, "EventId");

        if (blockIdx == -1) {
            reader.close();
            throw new IOException("Column 'BlockId' not found in header: " + headerLine);
        }
        if (eventIdx == -1) {
            reader.close();
            throw new IOException("Column 'EventId' not found in header: " + headerLine);
        }

        LinkedHashMap<String, List<String>> blockEvents = new LinkedHashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] fields = line.split(",", -1);
            if (fields.length <= Math.max(blockIdx, eventIdx)) {
                continue;
            }

            String blockId = fields[blockIdx].trim().replace("\"", "");
            String eventId = fields[eventIdx].trim().replace("\"", "");

            blockEvents.computeIfAbsent(blockId, k -> new ArrayList<>()).add(eventId);
        }
        reader.close();

        List<HdfsSession> sessions = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : blockEvents.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                sessions.add(new HdfsSession(entry.getKey(), entry.getValue()));
            }
        }
        return sessions;
    }

    public void loadLabels(File labelFile, List<HdfsSession> sessions) throws IOException {
        Map<String, Boolean> labels = new LinkedHashMap<>();

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(new FileInputStream(labelFile), StandardCharsets.UTF_8));

        String headerLine = reader.readLine();
        if (headerLine == null) {
            reader.close();
            throw new IOException("Label file is empty");
        }

        String[] headers = headerLine.split(",", -1);
        for (int i = 0; i < headers.length; i++) {
            headers[i] = headers[i].trim().replace("\"", "");
        }

        int blockIdx = findColumnIndex(headers, "BlockId");
        int labelIdx = findColumnIndex(headers, "Label");

        if (blockIdx == -1 || labelIdx == -1) {
            reader.close();
            throw new IOException(
                    "Label file must have 'BlockId' and 'Label' columns. Header: " + headerLine);
        }

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            String[] fields = line.split(",", -1);
            if (fields.length <= Math.max(blockIdx, labelIdx)) continue;

            String blockId = fields[blockIdx].trim().replace("\"", "");
            String label = fields[labelIdx].trim().replace("\"", "");
            labels.put(blockId, "Anomaly".equalsIgnoreCase(label));
        }
        reader.close();

        for (HdfsSession session : sessions) {
            Boolean isAnomaly = labels.get(session.blockId);
            if (isAnomaly != null) {
                session.isAnomaly = isAnomaly;
            }
        }
    }

    public String toTraceFormat(List<HdfsSession> sessions) {
        StringBuilder sb = new StringBuilder();
        for (HdfsSession session : sessions) {
            if (!session.events.isEmpty()) {
                sb.append(String.join(" ", session.events));
                sb.append(" end$");
            }
        }
        return sb.toString();
    }

    public void writeTraceFile(List<HdfsSession> sessions, File output) throws IOException {
        try (FileWriter fw = new FileWriter(output, StandardCharsets.UTF_8)) {
            for (HdfsSession session : sessions) {
                if (!session.events.isEmpty()) {
                    fw.write(String.join(" ", session.events));
                    fw.write(" end$\n");
                }
            }
        }
    }

    private int findColumnIndex(String[] headers, String columnName) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        return -1;
    }
}
