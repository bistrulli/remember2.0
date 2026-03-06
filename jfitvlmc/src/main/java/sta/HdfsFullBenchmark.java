package sta;

import fitvlmc.HdfsLogParser;
import fitvlmc.HdfsLogParser.HdfsSession;
import fitvlmc.HdfsRawLogParser;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import vlmc.VlmcRoot;

public class HdfsFullBenchmark {

    public static void main(String[] args) throws Exception {
        String structuredLogPath = null;
        String rawLogPath = null;
        String labelsPath = null;
        double alfa = 0.01;
        String outputPath = null;
        String vlmcModelPath = null;
        String saveVlmcPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--structured-log":
                    structuredLogPath = args[++i];
                    break;
                case "--raw-log":
                    rawLogPath = args[++i];
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
                case "--vlmc-model":
                    vlmcModelPath = args[++i];
                    break;
                case "--save-vlmc":
                    saveVlmcPath = args[++i];
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if ((structuredLogPath == null && rawLogPath == null) || labelsPath == null) {
            printUsage();
            System.exit(1);
        }

        File labelsFile = new File(labelsPath);
        if (!labelsFile.exists()) {
            System.err.println("Labels file not found: " + labelsPath);
            System.exit(1);
        }

        List<HdfsSession> sessions;
        if (rawLogPath != null) {
            File rawLog = new File(rawLogPath);
            if (!rawLog.exists()) {
                System.err.println("Raw log file not found: " + rawLogPath);
                System.exit(1);
            }
            System.out.println("Parsing raw HDFS log (this may take a few minutes)...");
            HdfsRawLogParser rawParser = new HdfsRawLogParser();
            sessions = rawParser.parseRawLog(rawLog);
        } else {
            File structuredLog = new File(structuredLogPath);
            if (!structuredLog.exists()) {
                System.err.println("Structured log file not found: " + structuredLogPath);
                System.exit(1);
            }
            System.out.println("Loading HDFS structured CSV...");
            HdfsLogParser parser = new HdfsLogParser();
            sessions = parser.parseStructuredLog(structuredLog);
        }

        HdfsLogParser labelParser = new HdfsLogParser();
        labelParser.loadLabels(labelsFile, sessions);

        int normals = 0, anomalies = 0;
        for (HdfsSession s : sessions) {
            if (s.isAnomaly) anomalies++;
            else normals++;
        }
        System.out.printf(
                "Loaded %d sessions (%d normal, %d anomalies)%n",
                sessions.size(), normals, anomalies);

        VlmcRoot preloaded = null;
        if (vlmcModelPath != null) {
            File vlmcFile = new File(vlmcModelPath);
            if (!vlmcFile.exists()) {
                System.err.println("VLMC model file not found: " + vlmcModelPath);
                System.exit(1);
            }
            System.out.println("Loading pre-trained VLMC model...");
            preloaded = new VlmcRoot();
            preloaded.setLabel("root");
            preloaded.parseVLMC(vlmcModelPath);
            preloaded.computeOrder(0);
            System.out.printf("Loaded VLMC: %d nodes, order %d%n", VlmcRoot.nNodes, VlmcRoot.order);
        }

        double[] betas = {0.01, 0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 50.0};
        HdfsBenchmark benchmark = new HdfsBenchmark(0.8, alfa, betas);

        File workDir = new File(System.getProperty("java.io.tmpdir"), "hdfs_benchmark");
        workDir.mkdirs();

        System.out.println("Running benchmark...");
        long start = System.currentTimeMillis();
        HdfsBenchmark.BenchmarkResult result = benchmark.run(sessions, workDir, preloaded);
        long elapsed = System.currentTimeMillis() - start;

        if (saveVlmcPath != null && result.vlmc != null) {
            File saveFile = new File(saveVlmcPath);
            saveFile.getParentFile().mkdirs();
            try (FileWriter fw = new FileWriter(saveFile, StandardCharsets.UTF_8)) {
                fw.write(result.vlmc.toString(new String[] {""}));
            }
            System.out.println("VLMC model saved to: " + saveVlmcPath);
        }

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
        List<HdfsSession> testAll =
                new java.util.ArrayList<>(normals.subList(splitIdx, normals.size()));
        testAll.addAll(anomalies);
        return testAll;
    }

    private static void printUsage() {
        System.err.println("Usage: HdfsFullBenchmark");
        System.err.println("  --raw-log <path>         HDFS.log (raw log, parsed automatically)");
        System.err.println("  --structured-log <path>  HDFS.log_structured.csv (alternative)");
        System.err.println("  --labels <path>          anomaly_label.csv");
        System.err.println("  [--alfa <value>]         Pruning alpha (default: 0.01)");
        System.err.println("  [--output <path>]        Output CSV path");
        System.err.println("  [--vlmc-model <path>]    Load pre-trained VLMC (skip training)");
        System.err.println("  [--save-vlmc <path>]     Save trained VLMC model to file");
    }
}
