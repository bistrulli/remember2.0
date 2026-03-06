package fitvlmc;

import fitvlmc.HdfsLogParser.HdfsSession;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HdfsRawLogParser {

    private static final Pattern BLOCK_PATTERN = Pattern.compile("(blk_-?\\d+)");

    private static final String[][] TEMPLATES = {
        {"E1", "Receiving block"},
        {"E2", "NameSystem.allocateBlock"},
        {"E3", "PacketResponder"},
        {"E4", "Received block"},
        {"E5", "NameSystem.addStoredBlock: blockMap updated"},
        {"E6", "Deleting block"},
        {"E7", "NameSystem.delete"},
        {"E8", "Verification succeeded"},
        {"E9", "Transmitted block"},
        {"E10", "Starting thread to transfer block"},
        {"E11", "ask"},
        {"E12", "Unexpected error"},
        {"E13", "writeBlock"},
        {"E14", "Receiving empty packet"},
        {"E15", "addStoredBlock request"},
        {"E16", "Redundant addStoredBlock"},
        {"E17", "Exception in receiveBlock"},
        {"E18", "Changing block file offset"},
        {"E19", "PendingReplicationMonitor timed out"},
        {"E20", "Reopen Block"},
        {"E21", "Removing block"},
        {"E22", "Adding an already existing"},
        {"E23", "Received block"}, // DataXceiver variant (with "src:")
    };

    public List<HdfsSession> parseRawLog(File logFile) throws IOException {
        LinkedHashMap<String, List<String>> blockEvents = new LinkedHashMap<>();

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new FileInputStream(logFile), StandardCharsets.UTF_8));

        String line;
        long lineCount = 0;
        while ((line = reader.readLine()) != null) {
            lineCount++;
            if (lineCount % 1000000 == 0) {
                System.out.printf("  Parsed %dM lines...%n", lineCount / 1000000);
            }

            Matcher blockMatcher = BLOCK_PATTERN.matcher(line);
            if (!blockMatcher.find()) continue;
            String blockId = blockMatcher.group(1);

            String eventId = classifyEvent(line);
            blockEvents.computeIfAbsent(blockId, k -> new ArrayList<>()).add(eventId);
        }
        reader.close();

        System.out.printf("  Total: %d lines, %d blocks%n", lineCount, blockEvents.size());

        List<HdfsSession> sessions = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : blockEvents.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                sessions.add(new HdfsSession(entry.getKey(), entry.getValue()));
            }
        }
        return sessions;
    }

    private String classifyEvent(String line) {
        // DataXceiver "Received block" vs PacketResponder "Received block"
        if (line.contains("DataXceiver") && line.contains("Received block")) return "E23";

        // Check templates in order
        if (line.contains("Receiving block")) return "E1";
        if (line.contains("NameSystem.allocateBlock")) return "E2";
        if (line.contains("PacketResponder")) return "E3";
        if (line.contains("Received block")) return "E4";
        if (line.contains("blockMap updated")) return "E5";
        if (line.contains("Deleting block")) return "E6";
        if (line.contains("NameSystem.delete")) return "E7";
        if (line.contains("Verification succeeded")) return "E8";
        if (line.contains("Transmitted block")) return "E9";
        if (line.contains("Starting thread to transfer")) return "E10";
        if (line.contains("BLOCK* ask")) return "E11";
        if (line.contains("Unexpected error")) return "E12";
        if (line.contains("writeBlock")) return "E13";
        if (line.contains("Receiving empty packet")) return "E14";
        if (line.contains("addStoredBlock request")) return "E15";
        if (line.contains("Redundant addStoredBlock")) return "E16";
        if (line.contains("Exception in receiveBlock")) return "E17";
        if (line.contains("Changing block file offset")) return "E18";
        if (line.contains("PendingReplicationMonitor timed out")) return "E19";
        if (line.contains("Reopen Block")) return "E20";
        if (line.contains("Removing block")) return "E21";
        if (line.contains("Adding an already existing")) return "E22";

        return "E0"; // Unknown
    }
}
