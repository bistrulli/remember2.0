package fitvlmc;

import java.io.IOException;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import ECFEntity.Flow;

/**
 * Utility to generate ECF using new procedure and save to file for comparison
 */
public class GenerateEcfToFile {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java fitvlmc.GenerateEcfToFile <input_file> <output_ecf_file>");
            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = args[1];

        try {
            System.out.println("=== GENERATING ECF WITH NEW PROCEDURE ===");
            System.out.println("Input: " + inputFile);
            System.out.println("Output: " + outputFile);

            // Read training data
            String content = Files.readString(Paths.get(inputFile));
            System.out.println("Input size: " + content.length() + " chars");

            // Generate ECF using new procedure
            long startTime = System.currentTimeMillis();
            Flow ecfModel = Trace2EcfIntegrator.createEcfFromContentWithValidation(content);
            long generationTime = System.currentTimeMillis() - startTime;

            System.out.println("ECF generation time: " + generationTime + "ms");
            System.out.println("Total edges: " + ecfModel.getEdges().size());

            // Save ECF to file
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(ecfModel.toString());
            }

            System.out.println("✅ ECF saved to: " + outputFile);

            // Show basic stats
            System.out.println("\n=== ECF STATISTICS ===");
            System.out.println("Total edges: " + ecfModel.getEdges().size());

            // Count edge types
            int endEdges = 0;
            int regularEdges = 0;

            for (Object edgeLabel : ecfModel.getEdges().keySet()) {
                if (edgeLabel.toString().equals("end$")) {
                    endEdges++;
                } else {
                    regularEdges++;
                }
            }

            System.out.println("Regular edges: " + regularEdges);
            System.out.println("End edges: " + endEdges);

        } catch (IOException e) {
            System.err.println("Failed to read input file: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("ECF generation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}