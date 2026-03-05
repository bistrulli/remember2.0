package fitvlmc;

import java.io.IOException;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import ECFEntity.Flow;
import ECFEntity.Edge;
import java.util.ArrayList;

/**
 * Utility to remove ONLY self-loops (A → A) from generated ECF files
 * Keeps all other connections intact including direct cycles (A → B → A)
 */
public class SelfLoopRemover {

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java fitvlmc.SelfLoopRemover <input_training_file> <original_ecf> <cleaned_ecf>");
            System.exit(1);
        }

        String inputFile = args[0];
        String originalEcfFile = args[1]; // Not used, we generate fresh
        String cleanedEcfFile = args[2];

        try {
            System.out.println("=== SELF-LOOP REMOVAL UTILITY ===");
            System.out.println("Input training file: " + inputFile);
            System.out.println("Cleaned ECF output: " + cleanedEcfFile);

            // Generate fresh ECF from training data
            String content = Files.readString(Paths.get(inputFile));
            Flow originalModel = Trace2EcfIntegrator.createEcfFromContentWithValidation(content);

            System.out.println("\n=== ORIGINAL ECF ANALYSIS ===");
            analyzeSelfLoops(originalModel);

            // Remove ONLY self-loops
            Flow cleanedModel = removeSelfLoops(originalModel);

            System.out.println("\n=== CLEANED ECF ANALYSIS ===");
            analyzeSelfLoops(cleanedModel);

            // Save cleaned ECF
            try (FileWriter writer = new FileWriter(cleanedEcfFile)) {
                writer.write(cleanedModel.toString());
            }

            System.out.println("\n✅ Cleaned ECF saved to: " + cleanedEcfFile);

            // Show comparison stats
            System.out.println("\n=== COMPARISON SUMMARY ===");
            int originalConnections = countTotalConnections(originalModel);
            int cleanedConnections = countTotalConnections(cleanedModel);

            System.out.println("Original edges: " + originalModel.getEdges().size());
            System.out.println("Cleaned edges: " + cleanedModel.getEdges().size());
            System.out.println("Original connections: " + originalConnections);
            System.out.println("Cleaned connections: " + cleanedConnections);
            System.out.println("Connections removed: " + (originalConnections - cleanedConnections));

        } catch (IOException e) {
            System.err.println("Failed to read input file: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Self-loop removal failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Remove ONLY self-loops (A → A) from ECF model
     * Preserves all other connections including A → B → A cycles
     */
    private static Flow removeSelfLoops(Flow originalModel) {
        System.out.println("\n🔄 Removing self-loops...");

        Flow cleanedModel = new Flow();
        int selfLoopsRemoved = 0;
        int connectionsKept = 0;

        // First pass: Add all edges to cleaned model
        for (Object edgeLabel : originalModel.getEdges().keySet()) {
            Edge originalEdge = originalModel.getEdges().get(edgeLabel);

            // Create new edge with same properties
            Edge cleanedEdge = new Edge(edgeLabel.toString());
            cleanedEdge.setCost(originalEdge.getCost());
            cleanedModel.addEdge(cleanedEdge);
        }

        // Second pass: Add connections, skipping self-loops
        for (Object edgeLabel : originalModel.getEdges().keySet()) {
            Edge originalEdge = originalModel.getEdges().get(edgeLabel);
            Edge cleanedEdge = cleanedModel.getEdges().get(edgeLabel);

            // Process OUT connections
            for (Object outEdgeLabel : originalEdge.getOut()) {
                if (outEdgeLabel.equals(edgeLabel)) {
                    // This is a self-loop (A → A) - SKIP it
                    selfLoopsRemoved++;
                    System.out.println("  ❌ Removed self-loop: " + edgeLabel + " → " + outEdgeLabel);
                } else {
                    // This is any other connection - ADD it
                    if (cleanedModel.getEdges().containsKey(outEdgeLabel)) {
                        Edge targetEdge = cleanedModel.getEdges().get(outEdgeLabel);
                        cleanedEdge.addOutEdge(targetEdge);
                        targetEdge.addInEdge(cleanedEdge);
                        connectionsKept++;
                    } else if (outEdgeLabel.equals("end$")) {
                        // Special handling for end$ if needed
                        connectionsKept++;
                    }
                }
            }
        }

        System.out.println("✅ Self-loops removed: " + selfLoopsRemoved);
        System.out.println("✅ Other connections kept: " + connectionsKept);

        return cleanedModel;
    }

    /**
     * Analyze and report self-loops in ECF model
     */
    private static void analyzeSelfLoops(Flow ecfModel) {
        int totalEdges = ecfModel.getEdges().size();
        int selfLoopCount = 0;
        int totalConnections = countTotalConnections(ecfModel);

        System.out.println("Total edges: " + totalEdges);

        ArrayList<String> selfLoopEdges = new ArrayList<>();

        for (Object edgeLabel : ecfModel.getEdges().keySet()) {
            Edge edge = ecfModel.getEdges().get(edgeLabel);

            // Check for self-loops
            if (edge.getOut().contains(edgeLabel)) {
                selfLoopCount++;
                selfLoopEdges.add(edgeLabel.toString());
            }
        }

        System.out.println("Total connections: " + totalConnections);
        System.out.println("Edges with self-loops: " + selfLoopCount);

        if (selfLoopCount > 0) {
            System.out.println("Self-loop edges (first 10):");
            for (int i = 0; i < Math.min(10, selfLoopEdges.size()); i++) {
                String edgeLabel = selfLoopEdges.get(i);
                System.out.println("  🔄 " + edgeLabel + " → " + edgeLabel);
            }
            if (selfLoopEdges.size() > 10) {
                System.out.println("  ... and " + (selfLoopEdges.size() - 10) + " more self-loops");
            }
        } else {
            System.out.println("✅ No self-loops found!");
        }

        // Also check for some direct cycles (A → B → A) for info
        checkDirectCycles(ecfModel);
    }

    /**
     * Count total connections in the model
     */
    private static int countTotalConnections(Flow ecfModel) {
        int total = 0;
        for (Object edgeLabel : ecfModel.getEdges().keySet()) {
            Edge edge = ecfModel.getEdges().get(edgeLabel);
            total += edge.getOut().size();
        }
        return total;
    }

    /**
     * Check for direct cycles (A → B → A) for reporting
     */
    private static void checkDirectCycles(Flow ecfModel) {
        int directCycles = 0;

        for (Object edgeLabel : ecfModel.getEdges().keySet()) {
            Edge edge = ecfModel.getEdges().get(edgeLabel);

            // Check each outgoing connection
            for (Object outLabel : edge.getOut()) {
                if (!outLabel.equals("end$") && ecfModel.getEdges().containsKey(outLabel)) {
                    Edge targetEdge = ecfModel.getEdges().get(outLabel);
                    if (targetEdge.getOut().contains(edgeLabel)) {
                        directCycles++;
                        if (directCycles <= 5) { // Show first 5
                            System.out.println("  🔄 Direct cycle: " + edgeLabel + " → " + outLabel + " → " + edgeLabel);
                        }
                    }
                }
            }
        }

        if (directCycles > 0) {
            System.out.println("Direct cycles (A → B → A) found: " + directCycles / 2); // Divide by 2 since we count each twice
            if (directCycles > 10) {
                System.out.println("  (Direct cycles are PRESERVED in cleaned model)");
            }
        }
    }
}