package test;

import static org.junit.jupiter.api.Assertions.*;

import fitvlmc.HdfsLogParser;
import fitvlmc.HdfsLogParser.HdfsSession;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class HdfsLogParserTest {

    @TempDir File tempDir;

    private File writeFile(String name, String content) throws IOException {
        File f = new File(tempDir, name);
        try (FileWriter fw = new FileWriter(f, StandardCharsets.UTF_8)) {
            fw.write(content);
        }
        return f;
    }

    @Test
    public void testParseGroupsByBlockId() throws IOException {
        String csv =
                "LineId,Date,Time,Pid,Level,Component,Content,EventId,EventTemplate,BlockId\n"
                        + "1,081109,203615,148,INFO,dfs.DataNode,content,E5,template,blk_1\n"
                        + "2,081109,203616,148,INFO,dfs.DataNode,content,E22,template,blk_1\n"
                        + "3,081109,203617,148,INFO,dfs.DataNode,content,E5,template,blk_2\n"
                        + "4,081109,203618,148,INFO,dfs.DataNode,content,E11,template,blk_2\n"
                        + "5,081109,203619,148,INFO,dfs.DataNode,content,E9,template,blk_1\n";

        File csvFile = writeFile("structured.csv", csv);
        HdfsLogParser parser = new HdfsLogParser();
        List<HdfsSession> sessions = parser.parseStructuredLog(csvFile);

        assertEquals(2, sessions.size());
        assertEquals("blk_1", sessions.get(0).blockId);
        assertEquals(List.of("E5", "E22", "E9"), sessions.get(0).events);
        assertEquals("blk_2", sessions.get(1).blockId);
        assertEquals(List.of("E5", "E11"), sessions.get(1).events);
    }

    @Test
    public void testPreservesInsertionOrder() throws IOException {
        String csv =
                "LineId,Date,Time,Pid,Level,Component,Content,EventId,EventTemplate,BlockId\n"
                        + "1,081109,203615,148,INFO,dfs.DataNode,c,E1,t,blk_3\n"
                        + "2,081109,203616,148,INFO,dfs.DataNode,c,E2,t,blk_1\n"
                        + "3,081109,203617,148,INFO,dfs.DataNode,c,E3,t,blk_3\n";

        File csvFile = writeFile("order.csv", csv);
        HdfsLogParser parser = new HdfsLogParser();
        List<HdfsSession> sessions = parser.parseStructuredLog(csvFile);

        assertEquals("blk_3", sessions.get(0).blockId);
        assertEquals("blk_1", sessions.get(1).blockId);
        assertEquals(List.of("E1", "E3"), sessions.get(0).events);
    }

    @Test
    public void testLoadLabels() throws IOException {
        String csv =
                "LineId,Date,Time,Pid,Level,Component,Content,EventId,EventTemplate,BlockId\n"
                        + "1,081109,203615,148,INFO,dfs.DataNode,c,E5,t,blk_1\n"
                        + "2,081109,203616,148,INFO,dfs.DataNode,c,E5,t,blk_2\n"
                        + "3,081109,203617,148,INFO,dfs.DataNode,c,E5,t,blk_3\n";

        String labels = "BlockId,Label\nblk_1,Normal\nblk_2,Anomaly\n";

        File csvFile = writeFile("data.csv", csv);
        File labelFile = writeFile("labels.csv", labels);

        HdfsLogParser parser = new HdfsLogParser();
        List<HdfsSession> sessions = parser.parseStructuredLog(csvFile);
        parser.loadLabels(labelFile, sessions);

        assertFalse(sessions.get(0).isAnomaly);
        assertTrue(sessions.get(1).isAnomaly);
        assertFalse(sessions.get(2).isAnomaly);
    }

    @Test
    public void testToTraceFormat() throws IOException {
        String csv =
                "LineId,Date,Time,Pid,Level,Component,Content,EventId,EventTemplate,BlockId\n"
                        + "1,081109,203615,148,INFO,dfs.DataNode,c,E5,t,blk_1\n"
                        + "2,081109,203616,148,INFO,dfs.DataNode,c,E22,t,blk_1\n"
                        + "3,081109,203617,148,INFO,dfs.DataNode,c,E9,t,blk_2\n";

        File csvFile = writeFile("trace.csv", csv);
        HdfsLogParser parser = new HdfsLogParser();
        List<HdfsSession> sessions = parser.parseStructuredLog(csvFile);
        String traceFormat = parser.toTraceFormat(sessions);

        assertEquals("E5 E22 end$E9 end$", traceFormat);
    }

    @Test
    public void testWriteTraceFile() throws IOException {
        String csv =
                "LineId,Date,Time,Pid,Level,Component,Content,EventId,EventTemplate,BlockId\n"
                        + "1,081109,203615,148,INFO,dfs.DataNode,c,E5,t,blk_1\n"
                        + "2,081109,203616,148,INFO,dfs.DataNode,c,E22,t,blk_1\n";

        File csvFile = writeFile("write.csv", csv);
        File outFile = new File(tempDir, "output.txt");

        HdfsLogParser parser = new HdfsLogParser();
        List<HdfsSession> sessions = parser.parseStructuredLog(csvFile);
        parser.writeTraceFile(sessions, outFile);

        assertTrue(outFile.exists());
        String content =
                new String(
                        java.nio.file.Files.readAllBytes(outFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("E5 E22 end$"));
    }

    @Test
    public void testSkipsEmptyLines() throws IOException {
        String csv =
                "LineId,Date,Time,Pid,Level,Component,Content,EventId,EventTemplate,BlockId\n"
                        + "1,081109,203615,148,INFO,dfs.DataNode,c,E5,t,blk_1\n"
                        + "\n"
                        + "2,081109,203616,148,INFO,dfs.DataNode,c,E22,t,blk_1\n";

        File csvFile = writeFile("empty_lines.csv", csv);
        HdfsLogParser parser = new HdfsLogParser();
        List<HdfsSession> sessions = parser.parseStructuredLog(csvFile);

        assertEquals(1, sessions.size());
        assertEquals(2, sessions.get(0).events.size());
    }
}
