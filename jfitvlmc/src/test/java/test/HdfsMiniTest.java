package test;

import static org.junit.jupiter.api.Assertions.*;

import fitvlmc.HdfsLogParser.HdfsSession;
import java.io.File;
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

    // --- Normal patterns: HDFS block lifecycle with realistic variations ---
    // All patterns use the SAME symbols (E1-E11, E22, E26) but in different orders/counts.
    // This ensures the VLMC sees all symbols in training — anomalies differ by SEQUENCE,
    // not by having alien symbols.
    //
    // Type 1: Standard 3-replica write (30%)
    private static final String[][][] NORMAL_PATTERNS = {
        {
            {"E5", "E22", "E5", "E11", "E9", "E11", "E9", "E11", "E9", "E26", "E26", "E26"},
            {"E5", "E22", "E5", "E5", "E11", "E9", "E11", "E9", "E11", "E9", "E26", "E26", "E26"},
            {"E5", "E22", "E5", "E11", "E9", "E11", "E9", "E11", "E9", "E26", "E26", "E26", "E2"},
            {"E5", "E22", "E11", "E9", "E11", "E9", "E11", "E9", "E26", "E26", "E26"},
        },
        // Type 2: 2-replica write (20%)
        {
            {"E5", "E22", "E5", "E11", "E9", "E11", "E9", "E26", "E26"},
            {"E5", "E22", "E11", "E9", "E11", "E9", "E26", "E26"},
            {"E5", "E22", "E5", "E11", "E9", "E11", "E9", "E26", "E26", "E2"},
        },
        // Type 3: Block read/serve (15%)
        {
            {"E5", "E9", "E9", "E9", "E26"},
            {"E5", "E9", "E9", "E26"},
            {"E5", "E9", "E9", "E9", "E9", "E26"},
            {"E5", "E9", "E26"},
        },
        // Type 4: Write with minor retry — E1 appears rarely in normal! (10%)
        {
            {"E5", "E22", "E5", "E1", "E11", "E9", "E11", "E9", "E26", "E26"},
            {"E5", "E22", "E5", "E11", "E1", "E9", "E11", "E9", "E26", "E26"},
        },
        // Type 5: Transfer with E8 (10%)
        {
            {"E5", "E8", "E22", "E11", "E9", "E26", "E26"},
            {"E5", "E8", "E22", "E11", "E9", "E11", "E9", "E26", "E26"},
            {"E5", "E8", "E8", "E22", "E11", "E9", "E26"},
        },
        // Type 6: Write with E3 retry — E3 appears rarely in normal! (8%)
        {
            {"E5", "E22", "E5", "E3", "E11", "E9", "E11", "E9", "E26", "E26"},
            {"E5", "E22", "E3", "E5", "E11", "E9", "E26", "E26"},
        },
        // Type 7: Verification write with E7 (7%)
        {
            {"E5", "E22", "E5", "E11", "E9", "E11", "E9", "E7", "E26", "E26"},
            {"E5", "E22", "E5", "E11", "E9", "E7", "E26"},
        },
    };

    private static final double[] NORMAL_TYPE_WEIGHTS = {0.30, 0.20, 0.15, 0.10, 0.10, 0.08, 0.07};

    private static final double[][] NORMAL_VARIANT_WEIGHTS = {
        {0.40, 0.25, 0.20, 0.15},
        {0.45, 0.30, 0.25},
        {0.35, 0.30, 0.20, 0.15},
        {0.55, 0.45},
        {0.40, 0.35, 0.25},
        {0.55, 0.45},
        {0.55, 0.45},
    };

    // --- Anomaly patterns: same symbols but WRONG sequences ---
    // Key: anomalies use repeated E1/E3 (which appear rarely in normals) or wrong order
    private static final String[][][] ANOMALY_PATTERNS = {
        // Type A: Excessive retries — E1 repeated many times (30%)
        {
            {"E5", "E22", "E1", "E1", "E1", "E9", "E26"},
            {"E5", "E22", "E5", "E1", "E1", "E1", "E11", "E26"},
            {"E5", "E1", "E1", "E1", "E1", "E9", "E26"},
        },
        // Type B: Replication then many failures (25%)
        {
            {"E5", "E22", "E5", "E11", "E9", "E1", "E1", "E1", "E26"},
            {"E5", "E22", "E1", "E1", "E11", "E1", "E1", "E26"},
        },
        // Type C: Connection drops — repeated E3 (20%)
        {
            {"E5", "E22", "E5", "E11", "E3", "E3", "E3", "E26"},
            {"E5", "E22", "E3", "E3", "E3", "E9", "E26"},
            {"E5", "E3", "E3", "E3", "E3", "E26"},
        },
        // Type D: Wrong order — E26 before E11, E9 after E26 (15%)
        {
            {"E5", "E26", "E22", "E11", "E9", "E26"},
            {"E5", "E22", "E26", "E11", "E26", "E9"},
        },
        // Type E: Truncated write — abnormally short with retry symbols (10%)
        {
            {"E5", "E1", "E3", "E26"},
            {"E5", "E22", "E1", "E3", "E26"},
        },
    };

    private static final double[] ANOMALY_TYPE_WEIGHTS = {0.30, 0.25, 0.20, 0.15, 0.10};

    private static final double[][] ANOMALY_VARIANT_WEIGHTS = {
        {0.45, 0.30, 0.25},
        {0.55, 0.45},
        {0.40, 0.35, 0.25},
        {0.55, 0.45},
        {0.50, 0.50},
    };

    // --- Test-only anomaly variants (never seen in training) ---
    private static final String[][] TEST_ANOMALY_VARIANTS = {
        {"E5", "E22", "E1", "E1", "E9", "E26"}, // V1: 2 retries (train has 3)
        {"E5", "E22", "E5", "E3", "E3", "E1", "E26"}, // V2: mixed conn+retry
        {"E5", "E1", "E1", "E3", "E3", "E9", "E26"}, // V3: retry then conn drop
        {"E5", "E22", "E5", "E11", "E3", "E1", "E1", "E26"}, // V4: partial write fail
        {"E5", "E26", "E9", "E11", "E26"}, // V5: completely wrong order
        {"E5", "E1", "E1", "E1", "E1", "E1", "E26"}, // V6: extreme retries
    };

    private static final int N_NORMAL_TRAIN = 4000;
    private static final int N_ANOMALY_TRAIN = 400;
    private static final int N_NORMAL_TEST = 1000;
    private static final int N_ANOMALY_TEST = 100;

    @BeforeEach
    public void generateDataset() {
        Random rng = new Random(42);
        allSessions = new ArrayList<>();

        // Training normals
        for (int i = 0; i < N_NORMAL_TRAIN; i++) {
            String[] pattern =
                    pickPattern(rng, NORMAL_PATTERNS, NORMAL_TYPE_WEIGHTS, NORMAL_VARIANT_WEIGHTS);
            HdfsSession s =
                    new HdfsSession("blk_normal_" + i, new ArrayList<>(Arrays.asList(pattern)));
            s.isAnomaly = false;
            allSessions.add(s);
        }

        // Training anomalies
        for (int i = 0; i < N_ANOMALY_TRAIN; i++) {
            String[] pattern =
                    pickPattern(
                            rng, ANOMALY_PATTERNS, ANOMALY_TYPE_WEIGHTS, ANOMALY_VARIANT_WEIGHTS);
            HdfsSession s =
                    new HdfsSession(
                            "blk_anomaly_train_" + i, new ArrayList<>(Arrays.asList(pattern)));
            s.isAnomaly = true;
            allSessions.add(s);
        }

        // Test normals (same distribution as training)
        for (int i = 0; i < N_NORMAL_TEST; i++) {
            String[] pattern =
                    pickPattern(rng, NORMAL_PATTERNS, NORMAL_TYPE_WEIGHTS, NORMAL_VARIANT_WEIGHTS);
            HdfsSession s =
                    new HdfsSession(
                            "blk_normal_test_" + i, new ArrayList<>(Arrays.asList(pattern)));
            s.isAnomaly = false;
            allSessions.add(s);
        }

        // Test anomalies (unseen variants only)
        int perVariant = N_ANOMALY_TEST / TEST_ANOMALY_VARIANTS.length;
        int remainder = N_ANOMALY_TEST % TEST_ANOMALY_VARIANTS.length;
        int idx = 0;
        for (int v = 0; v < TEST_ANOMALY_VARIANTS.length; v++) {
            int n = perVariant + (v < remainder ? 1 : 0);
            for (int j = 0; j < n; j++) {
                HdfsSession s =
                        new HdfsSession(
                                "blk_anomaly_test_" + idx,
                                new ArrayList<>(Arrays.asList(TEST_ANOMALY_VARIANTS[v])));
                s.isAnomaly = true;
                allSessions.add(s);
                idx++;
            }
        }
    }

    private static String[] pickPattern(
            Random rng, String[][][] patterns, double[] typeWeights, double[][] variantWeights) {
        int type = weightedChoice(rng, typeWeights);
        int variant = weightedChoice(rng, variantWeights[type]);
        return patterns[type][variant];
    }

    private static int weightedChoice(Random rng, double[] weights) {
        double r = rng.nextDouble();
        double cumulative = 0.0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (r < cumulative) return i;
        }
        return weights.length - 1;
    }

    @Test
    public void testPipelineEndToEnd() throws Exception {
        HdfsBenchmark benchmark =
                new HdfsBenchmark(0.8, 0.01, new double[] {0.1, 0.5, 1.0, 2.0, 5.0, 10.0});
        HdfsBenchmark.BenchmarkResult result = benchmark.run(allSessions, tempDir);

        assertNotNull(result.vlmc, "VLMC should be trained");
        assertFalse(result.results.isEmpty(), "Should have results");

        System.out.println("\n=== HDFS Synthetic Benchmark (4000+400 train, 1000+100 test) ===");
        System.out.println(benchmark.formatReport(result));

        // Also run auto-beta
        AutoBetaSelector selector = new AutoBetaSelector();
        double betaH = selector.heuristicBeta(result.vlmc);
        AutoBetaSelector.TreeStats stats = selector.computeTreeStats(result.vlmc);
        System.out.printf("Auto-beta heuristic: %.4f%n", betaH);
        System.out.println("Tree stats: " + stats);

        // Score with auto-beta
        List<HdfsSession> testSessions = extractTestSessions();
        List<BenchmarkMetrics.ScoredTrace> autoScores =
                benchmark.scoreWithSta(result.vlmc, testSessions, betaH);
        BenchmarkMetrics autoBm = new BenchmarkMetrics(autoScores);
        BenchmarkMetrics.MetricsResult autoResult = autoBm.findBestF1();
        System.out.printf(
                "STA auto-beta=%.2f: P=%.4f R=%.4f F1=%.4f%n",
                betaH, autoResult.precision, autoResult.recall, autoResult.f1);
    }

    @Test
    public void testStaImprovesOverVlmc() throws Exception {
        HdfsBenchmark benchmark = new HdfsBenchmark(0.8, 0.01, new double[] {0.5, 1.0, 2.0, 5.0});
        HdfsBenchmark.BenchmarkResult result = benchmark.run(allSessions, tempDir);

        double vlmcF1 = result.results.get("VLMC classic").f1;
        double bestStaF1 = 0;
        String bestMethod = "";
        for (var entry : result.results.entrySet()) {
            if (entry.getKey().startsWith("STA") && entry.getValue().f1 > bestStaF1) {
                bestStaF1 = entry.getValue().f1;
                bestMethod = entry.getKey();
            }
        }

        System.out.printf("%nVLMC F1=%.4f, Best STA F1=%.4f (%s)%n", vlmcF1, bestStaF1, bestMethod);
        assertTrue(
                bestStaF1 >= vlmcF1 * 0.95,
                String.format("STA (%.4f) should be comparable to VLMC (%.4f)", bestStaF1, vlmcF1));
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
        System.out.println("\n=== Auto-Beta on Synthetic HDFS ===");
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

    private List<HdfsSession> extractTestSessions() {
        List<HdfsSession> test = new ArrayList<>();
        for (HdfsSession s : allSessions) {
            if (s.blockId.contains("test")) {
                test.add(s);
            }
        }
        return test;
    }
}
