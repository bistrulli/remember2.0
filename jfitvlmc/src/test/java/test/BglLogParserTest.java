package test;

import static org.junit.jupiter.api.Assertions.*;

import fitvlmc.BglLogParser;
import fitvlmc.HdfsLogParser.HdfsSession;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BglLogParserTest {

    @TempDir File tempDir;

    private static final String NORMAL_LINE =
            "- 1117838570 2005.06.03 R02-M1-N0-C:J12-U11 2005-06-03-15.42.50.363779"
                    + " R02-M1-N0-C:J12-U11 RAS KERNEL INFO instruction cache parity error"
                    + " corrected";

    private static final String ANOMALY_LINE =
            "KERNDTLB 1117869872 2005.06.04 R23-M1-N8-I:J18-U11"
                    + " 2005-06-04-00.24.32.398284 R23-M1-N8-I:J18-U11 RAS APP FATAL ciod:"
                    + " failed to read message prefix on control stream";

    @Test
    public void testParseNormalLine() {
        BglLogParser parser = new BglLogParser();
        BglLogParser.ParsedLine parsed = parser.parseLine(NORMAL_LINE);
        assertNotNull(parsed);
        assertEquals("KERNEL_INFO", parsed.eventType);
        assertFalse(parsed.isAnomaly);
    }

    @Test
    public void testParseAnomalyLine() {
        BglLogParser parser = new BglLogParser();
        BglLogParser.ParsedLine parsed = parser.parseLine(ANOMALY_LINE);
        assertNotNull(parsed);
        assertEquals("APP_FATAL", parsed.eventType);
        assertTrue(parsed.isAnomaly);
    }

    @Test
    public void testParseMalformedLine() {
        BglLogParser parser = new BglLogParser();
        assertNull(parser.parseLine(""));
        assertNull(parser.parseLine(null));
        assertNull(parser.parseLine("short line"));
    }

    @Test
    public void testParseMalformedComponent() {
        BglLogParser parser = new BglLogParser();
        // Simulate a line where fields[7] is not uppercase (e.g. a number)
        String badLine =
                "- 1133447685 2005.12.01 R17-M0-NF-C:J09-U11 2005-12-01-06.34.45.094935"
                        + " R17-M0-NF-C:J09-U11 RAS 0 microseconds";
        // fields[7]="0" -> doesn't match [A-Z_]+
        BglLogParser.ParsedLine parsed = parser.parseLine(badLine);
        assertNull(parsed);
    }

    @Test
    public void testSlidingWindowSize3() throws IOException {
        // 9 normal lines -> 3 windows of size 3
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            sb.append(NORMAL_LINE).append("\n");
        }

        File logFile = writeFile(sb.toString());
        BglLogParser parser = new BglLogParser(3);
        List<HdfsSession> sessions = parser.parseLog(logFile);

        assertEquals(3, sessions.size());
        for (HdfsSession s : sessions) {
            assertEquals(3, s.events.size());
            assertFalse(s.isAnomaly);
            assertEquals("KERNEL_INFO", s.events.get(0));
        }
        assertEquals("W_0", sessions.get(0).blockId);
        assertEquals("W_1", sessions.get(1).blockId);
        assertEquals("W_2", sessions.get(2).blockId);
    }

    @Test
    public void testWindowWithAnomaly() throws IOException {
        // 3 normal + 1 anomaly + 2 normal = 6 lines, window size 3
        // Window 0: [N, N, N] -> normal
        // Window 1: [A, N, N] -> anomaly
        StringBuilder sb = new StringBuilder();
        sb.append(NORMAL_LINE).append("\n");
        sb.append(NORMAL_LINE).append("\n");
        sb.append(NORMAL_LINE).append("\n");
        sb.append(ANOMALY_LINE).append("\n");
        sb.append(NORMAL_LINE).append("\n");
        sb.append(NORMAL_LINE).append("\n");

        File logFile = writeFile(sb.toString());
        BglLogParser parser = new BglLogParser(3);
        List<HdfsSession> sessions = parser.parseLog(logFile);

        assertEquals(2, sessions.size());
        assertFalse(sessions.get(0).isAnomaly);
        assertTrue(sessions.get(1).isAnomaly);
    }

    @Test
    public void testIncompleteWindowDiscarded() throws IOException {
        // 5 lines, window size 3 -> 1 complete window, last 2 discarded
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(NORMAL_LINE).append("\n");
        }

        File logFile = writeFile(sb.toString());
        BglLogParser parser = new BglLogParser(3);
        List<HdfsSession> sessions = parser.parseLog(logFile);

        assertEquals(1, sessions.size());
    }

    @Test
    public void testDefaultWindowSize() {
        BglLogParser parser = new BglLogParser();
        // Build windows with 40 events -> 2 windows of size 20
        java.util.List<String> events = new java.util.ArrayList<>();
        java.util.List<Boolean> anomalies = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            events.add("KERNEL_INFO");
            anomalies.add(false);
        }
        List<HdfsSession> sessions = parser.buildWindows(events, anomalies);
        assertEquals(2, sessions.size());
        assertEquals(20, sessions.get(0).events.size());
    }

    private File writeFile(String content) throws IOException {
        File file = new File(tempDir, "test_bgl.log");
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(content);
        }
        return file;
    }
}
