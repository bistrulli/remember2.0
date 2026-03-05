package fitvlmc;

import ECFEntity.Edge;
import ECFEntity.Flow;

/**
 * Helper class to integrate Trace2Ecf functionality into fitVlmc
 * Extracts ECF model generation logic from Trace2Ecf.java
 */
public class Trace2EcfIntegrator {

    /**
     * Creates an ECF Flow model from trace content string - EXACT replica of Trace2Ecf.java algorithm
     * @param content The trace content with states separated by spaces
     * @return Flow object representing the ECF model
     */
    public static Flow createEcfFromContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Trace content cannot be null or empty");
        }

        Flow ecfModel = new Flow();

        // EXACT replica of Trace2Ecf.java lines 55-92
        String[] states = content.split(" ");
        Edge lastEdge = null;

        for (String s : states) {
            Edge e = null;
            if (lastEdge == null) {
                // First state in sequence or after end$ reset
                if ((e = ecfModel.getEdges().get(s)) == null) {
                    e = new Edge(String.valueOf(s));
                    if (s.split("_").length > 1) {
                        String[] p = s.split("_");
                        e.setCost(Integer.valueOf(p[p.length - 1]));
                    }
                    ecfModel.addEdge(e);
                }
                lastEdge = e;
            } else {
                // Subsequent states - create connections
                if ((e = ecfModel.getEdges().get(s)) == null) {
                    e = new Edge(String.valueOf(s));
                    if (s.split("_").length > 1) {
                        String[] p = null;
                        try {
                            p = s.split("_");
                            e.setCost(Integer.valueOf(p[p.length - 1].replace("$", "")));
                        } catch (java.lang.NumberFormatException excep) {
                            System.err.println(s);
                            excep.printStackTrace();
                        }
                    }
                    ecfModel.addEdge(e);
                }
                // Set connection between edges (with duplicate prevention)
                if (!lastEdge.getOut().contains(e)) {
                    lastEdge.addOutEdge(e);
                }
                if (!e.getIn().contains(lastEdge)) {
                    e.addInEdge(lastEdge);
                }
                lastEdge = e;
            }

            // CRITICAL: Reset after end$ but continue processing (don't break!)
            if (s.equals("end$")) {
                lastEdge = null;
            }
        }

        return ecfModel;
    }


    /**
     * Creates ECF model from trace content with additional validation
     * @param content The trace content
     * @return Flow object with validation checks
     */
    public static Flow createEcfFromContentWithValidation(String content) {
        System.out.println("Generating ECF model using exact Trace2Ecf.java algorithm...");
        Flow ecfModel = createEcfFromContent(content);

        // Validation checks
        if (ecfModel.getEdges().isEmpty()) {
            throw new RuntimeException("Generated ECF model is empty");
        }

        // Check for disconnected components
        int edgesWithNoIn = 0;
        int edgesWithNoOut = 0;

        for (Edge edge : ecfModel.getEdges().values()) {
            if (edge.getIn().isEmpty()) {
                edgesWithNoIn++;
            }
            if (edge.getOut().isEmpty()) {
                edgesWithNoOut++;
            }
        }

        System.out.println("ECF Model Statistics:");
        System.out.println("  - Total states: " + ecfModel.getEdges().size());
        System.out.println("  - Start states (no incoming): " + edgesWithNoIn);
        System.out.println("  - End states (no outgoing): " + edgesWithNoOut);

        // Check for end$ state specifically
        if (ecfModel.getEdges().containsKey("end$")) {
            Edge endState = ecfModel.getEdges().get("end$");
            System.out.println("  - Terminal symbol 'end$' detected with " + endState.getIn().size() + " incoming connections");
        }

        return ecfModel;
    }
}