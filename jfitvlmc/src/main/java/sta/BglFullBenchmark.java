package sta;

import fitvlmc.BglLogParser;
import fitvlmc.HdfsLogParser.HdfsSession;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import vlmc.VlmcRoot;

public class BglFullBenchmark {

    public static void main(String[] args) throws Exception {
        String bglLogPath = null;
        int windowSize = 20;
        double alfa = 0.01;
        double eta = 0.05;
        String outputPath = null;
        String vlmcModelPath = null;
        String saveVlmcPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--bgl-log":
                    bglLogPath = args[++i];
                    break;
                case "--window-size":
                    windowSize = Integer.parseInt(args[++i]);
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
                case "--eta":
                    eta = Double.parseDouble(args[++i]);
                    break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (bglLogPath == null) {
            printUsage();
            System.exit(1);
        }

        File bglLog = new File(bglLogPath);
        if (!bglLog.exists()) {
            System.err.println("BGL log file not found: " + bglLogPath);
            System.exit(1);
        }

        System.out.printf("Parsing BGL log (window size=%d)...%n", windowSize);
        BglLogParser bglParser = new BglLogParser(windowSize);
        List<HdfsSession> sessions = bglParser.parseLog(bglLog);

        int normals = 0, anomalies = 0;
        for (HdfsSession s : sessions) {
            if (s.isAnomaly) anomalies++;
            else normals++;
        }
        System.out.printf(
                "Loaded %d windows (%d normal, %d anomalies)%n",
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
            VlmcRoot.nNodes = 0;
            preloaded.parseVLMC(vlmcModelPath);
            preloaded.computeOrder(0);
            System.out.printf("Loaded VLMC: %d nodes, order %d%n", VlmcRoot.nNodes, VlmcRoot.order);
        }

        double[] betas = {0.01, 0.1, 0.5, 1.0, 2.0, 5.0, 10.0, 50.0};
        HdfsBenchmark benchmark = new HdfsBenchmark(0.8, alfa, betas, eta, "BGL");

        File workDir = new File(System.getProperty("java.io.tmpdir"), "bgl_benchmark");
        workDir.mkdirs();

        System.out.println("Running benchmark...");
        long start = System.currentTimeMillis();
        HdfsBenchmark.BenchmarkResult result = benchmark.run(sessions, workDir, preloaded);
        long elapsed = System.currentTimeMillis() - start;

        if (saveVlmcPath != null && result.vlmc != null) {
            File saveFile = new File(saveVlmcPath);
            if (saveFile.getParentFile() != null) {
                saveFile.getParentFile().mkdirs();
            }
            try (FileWriter fw = new FileWriter(saveFile, StandardCharsets.UTF_8)) {
                fw.write(result.vlmc.toString(new String[] {""}));
            }
            System.out.println("VLMC model saved to: " + saveVlmcPath);
        }

        System.out.printf(
                "Benchmark completed in %.1f seconds (eta=%.4f)%n%n", elapsed / 1000.0, eta);
        System.out.println(benchmark.formatReport(result));

        AutoBetaSelector selector = new AutoBetaSelector();
        double betaH = selector.heuristicBeta(result.vlmc);
        AutoBetaSelector.TreeStats stats = selector.computeTreeStats(result.vlmc);
        System.out.printf("Auto-beta heuristic: %.4f%n", betaH);
        System.out.println("Tree stats: " + stats);

        List<BenchmarkMetrics.ScoredTrace> autoScores =
                benchmark.scoreWithSta(result.vlmc, filterTest(sessions), betaH);
        BenchmarkMetrics autoBm = new BenchmarkMetrics(autoScores);
        BenchmarkMetrics.MetricsResult autoResult = autoBm.findBestF1();
        System.out.printf(
                "STA auto-beta=%.2f: P=%.4f R=%.4f F1=%.4f%n",
                betaH, autoResult.precision, autoResult.recall, autoResult.f1);

        if (outputPath != null) {
            File outputFile = new File(outputPath);
            if (outputFile.getParentFile() != null) {
                outputFile.getParentFile().mkdirs();
            }
            benchmark.writeCsvReport(result, outputFile);
            System.out.println("\nCSV report written to: " + outputPath);
        }
    }

    private static List<HdfsSession> filterTest(List<HdfsSession> all) {
        java.util.List<HdfsSession> normals = new java.util.ArrayList<>();
        java.util.List<HdfsSession> anomalies = new java.util.ArrayList<>();
        for (HdfsSession s : all) {
            if (s.isAnomaly) anomalies.add(s);
            else normals.add(s);
        }
        java.util.Collections.shuffle(normals, new java.util.Random(42));
        int splitIdx = (int) (normals.size() * 0.8);
        java.util.List<HdfsSession> testAll =
                new java.util.ArrayList<>(normals.subList(splitIdx, normals.size()));
        testAll.addAll(anomalies);
        return testAll;
    }

    private static void printUsage() {
        System.err.println("Usage: BglFullBenchmark");
        System.err.println("  --bgl-log <path>         BGL.log file");
        System.err.println("  [--window-size <N>]      Sliding window size (default: 20)");
        System.err.println("  [--alfa <value>]         Pruning alpha (default: 0.01)");
        System.err.println("  [--output <path>]        Output CSV path");
        System.err.println("  [--vlmc-model <path>]    Load pre-trained VLMC (skip training)");
        System.err.println("  [--save-vlmc <path>]     Save trained VLMC model to file");
        System.err.println("  [--eta <value>]          BMA fixed-share parameter (default: 0.05)");
    }
}
