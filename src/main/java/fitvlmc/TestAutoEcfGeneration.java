package fitvlmc;

import ECFEntity.Flow;

/**
 * Test class for automatic ECF generation functionality
 */
public class TestAutoEcfGeneration {

    public static void main(String[] args) {
        // Test data with simple trace (using numeric states)
        String testTrace = "1 2 3 end$ 1 4 end$";

        System.out.println("Testing automatic ECF generation...");
        System.out.println("Input trace: " + testTrace);

        try {
            // Test the Trace2EcfIntegrator
            Flow ecfModel = Trace2EcfIntegrator.createEcfFromContentWithValidation(testTrace);

            System.out.println("\n=== Generated ECF Model ===");
            System.out.println(ecfModel.toString());

            // Verify edges exist
            System.out.println("=== Verification ===");
            System.out.println("Total edges: " + ecfModel.getEdges().size());

            for (Object edgeLabel : ecfModel.getEdges().keySet()) {
                System.out.println("Edge: " + edgeLabel +
                    " | Cost: " + ecfModel.getEdges().get(edgeLabel).getCost() +
                    " | In: " + ecfModel.getEdges().get(edgeLabel).getIn().size() +
                    " | Out: " + ecfModel.getEdges().get(edgeLabel).getOut().size());
            }

            System.out.println("\nTest completed successfully!");

        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}