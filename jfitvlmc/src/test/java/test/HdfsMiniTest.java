package test;

import static org.junit.jupiter.api.Assertions.*;

import fitvlmc.HdfsLogParser;
import fitvlmc.HdfsLogParser.HdfsSession;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sta.AutoBetaSelector;
import sta.BenchmarkMetrics;
import sta.HdfsBenchmark;

public class HdfsMiniTest {

    @TempDir File tempDir;

    private List<HdfsSession> allSessions;

    private static final String[][] NORMAL_PATTERNS = {
        {"E5", "E22", "E5", "E11", "E9", "E11", "E9", "E11", "E9", "E26", "E26", "E26"},
        {"E5", "E22", "E5", "E11", "E9", "E11", "E9", "E26", "E26"},
        {"E5", "E5", "E22", "E11", "E9", "E26", "E26", "E26"},
        {"E5", "E22", "E11", "E9", "E11", "E9", "E11", "E9", "E26"},
        {"E5", "E5", "E22", "E11", "E9", "E11", "E9", "E26", "E26"},
    };

    private static final String[][] ANOMALY_PATTERNS = {
        {"E5", "E22", "E1", "E1", "E1", "E15", "E26"},
        {"E5", "E1", "E1", "E1", "E1", "E26"},
        {"E5", "E22", "E15", "E15", "E15", "E26"},
        {"E1", "E1", "E1", "E1", "E1", "E1"},
    };

    @BeforeEach
    public void generateMiniDataset() {
        Random rng = new Random(42);
        allSessions = new ArrayList<>();

        for (int i = 0; i < 500; i++) {
            String[] pattern = NORMAL_PATTERNS[rng.nextInt(NORMAL_PATTERNS.length)];
            HdfsSession s =
                    new HdfsSession("blk_normal_" + i, new ArrayList<>(Arrays.asList(pattern)));
            s.isAnomaly = false;
            allSessions.add(s);
        }

        for (int i = 0; i < 50; i++) {
            String[] pattern = ANOMALY_PATTERNS[rng.nextInt(ANOMALY_PATTERNS.length)];
            HdfsSession s =
                    new HdfsSession("blk_anomaly_" + i, new ArrayList<>(Arrays.asList(pattern)));
            s.isAnomaly = true;
            allSessions.add(s);
        }
    }

    @Test
    public void testPipelineEndToEnd() throws Exception {
        HdfsBenchmark benchmark =
                new HdfsBenchmark(0.8, 0.01, new double[] {0.1, 1.0, 5.0, 10.0});
        HdfsBenchmark.BenchmarkResult result = benchmark.run(allSessions, tempDir);

        assertNotNull(result.vlmc, "VLMC should be trained");
        assertFalse(result.results.isEmpty(), "Should have results");

        System.out.println("\n=== HDFS Mini Benchmark ===");
        System.out.println(benchmark.formatReport(result));
    }

    @Test
    public void testSeparationAboveRandom() throws Exception {
        HdfsBenchmark benchmark = new HdfsBenchmark(0.8, 0.01, new double[] {1.0, 5.0});
        HdfsBenchmark.BenchmarkResult result = benchmark.run(allSessions, tempDir);

        boolean anyAboveRandom = false;
        for (BenchmarkMetrics.MetricsResult m : result.results.values()) {
            if (m.f1 > 0.3) {
                anyAboveRandom = true;
                break;
            }
        }
        assertTrue(anyAboveRandom, "At least one method should separate better than random");
    }

    @Test
    public void testAutoBetaHeuristicProducesReasonableValue() throws Exception {
        HdfsBenchmark benchmark = new HdfsBenchmark(0.8, 0.01, new double[] {1.0});
        HdfsBenchmark.BenchmarkResult result = benchmark.run(allSessions, tempDir);

        AutoBetaSelector selector = new AutoBetaSelector();
        double betaH = selector.heuristicBeta(result.vlmc);

        assertTrue(betaH > 0.01, "Beta should be > 0.01, got: " + betaH);
        assertTrue(betaH < 100, "Beta should be < 100, got: " + betaH);

        AutoBetaSelector.TreeStats stats = selector.computeTreeStats(result.vlmc);
        System.out.println("\n=== Auto-Beta on Mini HDFS ===");
        System.out.printf("Heuristic beta: %.4f%n", betaH);
        System.out.println("Tree stats: " + stats);
    }

    @Test
    public void testCsvReport() throws Exception {
        HdfsBenchmark benchmark = new HdfsBenchmark(0.8, 0.01, new double[] {1.0});
        HdfsBenchmark.BenchmarkResult result = benchmark.run(allSessions, tempDir);

        File csvFile = new File(tempDir, "report.csv");
        benchmark.writeCsvReport(result, csvFile);

        assertTrue(csvFile.exists(), "CSV report should be written");
        String content =
                new String(
                        java.nio.file.Files.readAllBytes(csvFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("method,precision"), "CSV should have header");
        assertTrue(content.contains("VLMC classic"), "CSV should have VLMC results");
    }

    @Test
    public void testTrainingSizeCorrect() throws Exception {
        HdfsBenchmark benchmark = new HdfsBenchmark(0.8, 0.01, new double[] {1.0});
        HdfsBenchmark.BenchmarkResult result = benchmark.run(allSessions, tempDir);

        assertEquals(400, result.nTraining, "Should train on 80% of normals (500*0.8=400)");
        assertEquals(
                150,
                result.nTest,
                "Should test on 20% normals (100) + all anomalies (50) = 150");
        assertEquals(50, result.nAnomalies, "Should have 50 anomalies");
    }
}
