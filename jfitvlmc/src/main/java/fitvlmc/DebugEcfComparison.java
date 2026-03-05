package fitvlmc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import ECFEntity.Flow;
// import main.Trace2Ecf; // Not directly accessible from jfitVLMC

/**
 * Debug utility to compare ECF generation between new (Trace2EcfIntegrator)
 * and old (original Trace2Ecf) procedures
 */
public class DebugEcfComparison {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java fitvlmc.DebugEcfComparison <training_data_file>");
            System.exit(1);
        }

        String trainingFile = args[0];

        try {
            System.out.println("=== ECF GENERATION COMPARISON DEBUG ===");
            System.out.println("Training data file: " + trainingFile);

            // Read training data
            String content = Files.readString(Paths.get(trainingFile));
            System.out.println("Training data size: " + content.length() + " chars");

            // Show sample of content
            String sample = content.length() > 300 ? content.substring(0, 300) + "..." : content;
            System.out.println("Sample content: " + sample);

            System.out.println("\n" + "=".repeat(60));
            System.out.println("GENERATING ECF WITH NEW PROCEDURE (Trace2EcfIntegrator)");
            System.out.println("=".repeat(60));

            // Generate ECF with new procedure
            Flow newEcfModel = null;
            try {
                long startTime = System.currentTimeMillis();
                newEcfModel = Trace2EcfIntegrator.createEcfFromContentWithValidation(content);
                long newTime = System.currentTimeMillis() - startTime;

                System.out.println("✅ NEW PROCEDURE SUCCESS");
                System.out.println("Generation time: " + newTime + "ms");
                System.out.println("Total edges: " + newEcfModel.getEdges().size());

            } catch (Exception e) {
                System.out.println("❌ NEW PROCEDURE FAILED: " + e.getMessage());
                e.printStackTrace();
            }

            System.out.println("\n" + "=".repeat(60));
            System.out.println("ANALYZING ECF STRUCTURE FOR LOOPS");
            System.out.println("=".repeat(60));

            // For now, we analyze only the new model since the old procedure
            // is in ECF project and doesn't have a direct method API
            System.out.println("📝 NOTE: Old procedure comparison requires ECF project integration");
            System.out.println("         Focus: Analyzing new procedure for infinite loops");

            // Show detailed analysis of the new model
            if (newEcfModel != null) {
                System.out.println("\n" + "=".repeat(60));
                System.out.println("ECF MODEL - DETAILED ANALYSIS");
                System.out.println("=".repeat(60));
                analyzeEcfModel(newEcfModel, "CURRENT");
            }

        } catch (IOException e) {
            System.err.println("Failed to read training file: " + e.getMessage());
            System.exit(1);
        }
    }

    // compareEcfModels method removed - requires ECF project integration

    private static void analyzeEcfModel(Flow ecfModel, String modelType) {
        System.out.println("📋 " + modelType + " MODEL ANALYSIS:");
        System.out.println("  Total edges: " + ecfModel.getEdges().size());

        // Count different types of edges
        int selfLoops = 0;
        int twoCycles = 0;
        int endEdges = 0;

        for (Object edgeLabel : ecfModel.getEdges().keySet()) {
            var edge = ecfModel.getEdges().get(edgeLabel);
            String edgeName = edgeLabel.toString();

            // Count end$ edges
            if (edgeName.equals("end$")) {
                endEdges++;
                System.out.println("  End edge found - incoming: " + edge.getIn().size() +
                                 ", outgoing: " + edge.getOut().size());
                continue;
            }

            // Check for self-loops
            if (edge.getOut().contains(edgeLabel)) {
                selfLoops++;
            }

            // Check for 2-cycles
            for (Object outgoing : edge.getOut()) {
                if (ecfModel.getEdges().containsKey(outgoing)) {
                    var targetEdge = ecfModel.getEdges().get(outgoing);
                    if (targetEdge.getOut().contains(edgeLabel)) {
                        twoCycles++;
                    }
                }
            }
        }

        System.out.println("  Self-loops: " + selfLoops);
        System.out.println("  2-cycles: " + twoCycles / 2);
        System.out.println("  End edges: " + endEdges);

        // Show first 10 edges for structure analysis
        System.out.println("\n📝 EDGE STRUCTURE (first 10):");
        ecfModel.getEdges().keySet().stream()
            .limit(10)
            .forEach(edgeLabel -> {
                var edge = ecfModel.getEdges().get(edgeLabel);
                System.out.println(String.format("  %s: in=%d, out=%d -> %s",
                    edgeLabel, edge.getIn().size(), edge.getOut().size(),
                    edge.getOut().stream().limit(3).toList()));
            });

        // Show potential infinite loops
        System.out.println("\n⚠️ POTENTIAL INFINITE LOOPS:");
        boolean foundLoops = false;
        for (Object edgeLabel : ecfModel.getEdges().keySet()) {
            var edge = ecfModel.getEdges().get(edgeLabel);
            if (edge.getOut().contains(edgeLabel)) {
                System.out.println("  Self-loop: " + edgeLabel + " -> " + edgeLabel);
                foundLoops = true;
            }
        }
        if (!foundLoops) {
            System.out.println("  No obvious infinite loops detected");
        }
    }
}