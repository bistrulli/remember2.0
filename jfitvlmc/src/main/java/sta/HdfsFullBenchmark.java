package sta;

import fitvlmc.HdfsLogParser;
import fitvlmc.HdfsLogParser.HdfsSession;
import java.io.File;
import java.util.List;
import java.util.Map;

public class HdfsFullBenchmark {

    public static void main(String[] args) throws Exception {
        String structuredLogPath = null;
        String labelsPath = null;
        double alfa = 0.01;
        String outputPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--structured-log":
                    structuredLogPath = args[++i];
                    break;
                case "--labels":
                    labelsPath = args[++i];
                    break;
                case "--alfa":
                    alfa = Double.parseDouble(args[++i]);
                    break;
                case "--output":
                    outputPath = args[++i];
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (structuredLogPath == null || labelsPath == null) {
            printUsage();
            System.exit(1);
        }

        File structuredLog = new File(structuredLogPath);
        File labelsFile = new File(labelsPath);

        if (!structuredLog.exists()) {
            System.err.println("Structured log file not found: " + structuredLogPath);
            System.exit(1);
        }
        if (!labelsFile.exists()) {
            System.err.println("Labels file not found: " + labelsPath);
            System.exit(1);
        }

        System.out.println("Loading HDFS dataset...");
        HdfsLogParser parser = new HdfsLogParser();
        List<HdfsSession> sessions = parser.parseStructuredLog(structuredLog);
        parser.loadLabels(labelsFile, sessions);

        int normals = 0, anomalies = 0;
        for (HdfsSession s : sessions) {
            if (s.isAnomaly) anomalies++;
            else normals++;
        }
        System.out.printf(
                "Loaded %d sessions (%d normal, %d anomalies)%n",
                sessions.size(), normals, anomalies);

        double[] betas = {0.01, 0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 50.0};
        HdfsBenchmark benchmark = new HdfsBenchmark(0.8, alfa, betas);

        File workDir = new File(System.getProperty("java.io.tmpdir"), "hdfs_benchmark");
        workDir.mkdirs();

        System.out.println("Running benchmark...");
        long start = System.currentTimeMillis();
        HdfsBenchmark.BenchmarkResult result = benchmark.run(sessions, workDir);
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("Benchmark completed in %.1f seconds%n%n", elapsed / 1000.0);
        System.out.println(benchmark.formatReport(result));

        AutoBetaSelector selector = new AutoBetaSelector();
        double betaH = selector.heuristicBeta(result.vlmc);
        AutoBetaSelector.TreeStats stats = selector.computeTreeStats(result.vlmc);
        System.out.printf("Auto-beta heuristic: %.4f%n", betaH);
        System.out.println("Tree stats: " + stats);

        List<BenchmarkMetrics.ScoredTrace> autoScores =
                benchmark.scoreWithSta(result.vlmc, filterTest(sessions, result), betaH);
        BenchmarkMetrics autoBm = new BenchmarkMetrics(autoScores);
        BenchmarkMetrics.MetricsResult autoResult = autoBm.findBestF1();
        System.out.printf(
                "STA auto-beta=%.2f: P=%.4f R=%.4f F1=%.4f%n",
                betaH, autoResult.precision, autoResult.recall, autoResult.f1);

        if (outputPath != null) {
            File outputFile = new File(outputPath);
            outputFile.getParentFile().mkdirs();
            benchmark.writeCsvReport(result, outputFile);
            System.out.println("\nCSV report written to: " + outputPath);
        }
    }

    private static List<HdfsSession> filterTest(
            List<HdfsSession> all, HdfsBenchmark.BenchmarkResult result) {
        List<HdfsSession> normals = new java.util.ArrayList<>();
        List<HdfsSession> anomalies = new java.util.ArrayList<>();
        for (HdfsSession s : all) {
            if (s.isAnomaly) anomalies.add(s);
            else normals.add(s);
        }
        java.util.Collections.shuffle(normals, new java.util.Random(42));
        int splitIdx = (int) (normals.size() * 0.8);
        List<HdfsSession> testAll = new java.util.ArrayList<>(normals.subList(splitIdx, normals.size()));
        testAll.addAll(anomalies);
        return testAll;
    }

    private static void printUsage() {
        System.err.println("Usage: HdfsFullBenchmark");
        System.err.println("  --structured-log <path>  HDFS.log_structured.csv");
        System.err.println("  --labels <path>          anomaly_label.csv");
        System.err.println("  [--alfa <value>]         Pruning alpha (default: 0.01)");
        System.err.println("  [--output <path>]        Output CSV path");
    }
}
