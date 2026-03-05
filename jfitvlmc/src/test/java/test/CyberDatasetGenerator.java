package test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class CyberDatasetGenerator {

    private final Random rng;

    // Normal pattern bases + variants with relative weights
    private static final String[][][] NORMAL_PATTERNS = {
        // Type 1: Admin routine (25%)
        {
            {"SS", "SU", "UP", "BK", "LO"},
            {"SS", "SU", "UP", "UP", "BK", "LO"},
            {"SS", "NR", "SU", "UP", "BK", "LO"},
            {"SS", "SU", "UP", "BK", "BK", "LO"}
        },
        // Type 2: Admin files (25%)
        {
            {"SS", "SU", "FR", "FW", "LO"},
            {"SS", "SU", "FR", "FW", "FW", "LO"},
            {"SS", "SU", "FR", "FR", "FW", "LO"},
            {"SS", "NR", "SU", "FR", "FW", "LO"}
        },
        // Type 3: User normal (30%)
        {
            {"SS", "NR", "NR", "NR", "LO"},
            {"SS", "NR", "NR", "NR", "NR", "LO"},
            {"SS", "NR", "NR", "LO"},
            {"SS", "NR", "NR", "NR", "NR", "NR", "LO"}
        },
        // Type 4: Cron job (10%)
        {
            {"CR", "BK", "LO"},
            {"CR", "BK", "BK", "LO"},
            {"CR", "UP", "LO"}
        },
        // Type 5: Dev deployment (10%)
        {
            {"SS", "SU", "PS", "FW", "FW", "LO"},
            {"SS", "SU", "PS", "FW", "FW", "FW", "LO"},
            {"SS", "SU", "PS", "PS", "FW", "FW", "LO"}
        }
    };

    // Weights per variant within each type
    private static final double[][] NORMAL_VARIANT_WEIGHTS = {
        {0.40, 0.25, 0.20, 0.15},
        {0.40, 0.25, 0.20, 0.15},
        {0.35, 0.25, 0.25, 0.15},
        {0.50, 0.30, 0.20},
        {0.40, 0.30, 0.30}
    };

    // Type proportions for normal traces
    private static final double[] NORMAL_TYPE_WEIGHTS = {0.25, 0.25, 0.30, 0.10, 0.10};

    // Attack pattern bases + variants
    private static final String[][][] ATTACK_PATTERNS = {
        // Type A: Brute force + exfil (40%)
        {
            {"SF", "SF", "SF", "SF", "SS", "SU", "FR", "NC", "LO"},
            {"SF", "SF", "SF", "SF", "SF", "SS", "SU", "FR", "NC", "LO"},
            {"SF", "SF", "SF", "SF", "SS", "SU", "FR", "FR", "NC", "LO"}
        },
        // Type B: Brute force slow (20%)
        {{"SF", "SS", "SF", "SS", "SF", "SF", "SS", "SU", "FR", "NC", "LO"}},
        // Type C: Insider threat (25%)
        {
            {"SS", "SU", "FR", "FR", "FR", "NC", "LO"},
            {"SS", "SU", "FR", "FR", "FR", "FR", "NC", "LO"}
        },
        // Type D: Lateral movement (15%)
        {
            {"SS", "PS", "NC", "PS", "NC", "LO"},
            {"SS", "PS", "NC", "PS", "NC", "PS", "NC", "LO"}
        }
    };

    private static final double[][] ATTACK_VARIANT_WEIGHTS = {
        {0.50, 0.25, 0.25},
        {1.0},
        {0.60, 0.40},
        {0.50, 0.50}
    };

    private static final double[] ATTACK_TYPE_WEIGHTS = {0.40, 0.20, 0.25, 0.15};

    // Test variants: NEVER seen in training
    private static final String[][] TEST_ATTACK_VARIANTS = {
        {"SF", "SF", "SS", "SU", "FR", "NC", "LO"}, // V1: brute 2-fail
        {"SF", "SF", "SF", "SF", "SF", "SF", "SS", "SU", "FR", "NC", "LO"}, // V2: brute 6-fail
        {"SS", "SU", "FR", "NC", "LO"}, // V3: insider simplified
        {"SF", "SF", "SS", "SU", "PS", "NC", "LO"}, // V4: mixed brute+lateral
        {"SS", "PS", "NC", "PS", "NC", "PS", "NC", "PS", "NC", "LO"}, // V5: lateral 4-hop
        {"SF", "SF", "SF", "SF", "SS", "SU", "FR", "FW", "NC", "LO"} // V6: brute+write+exfil
    };

    private static final String[] TEST_VARIANT_LABELS = {
        "brute_2fail",
        "brute_6fail",
        "insider_simple",
        "mixed_brute_lateral",
        "lateral_4hop",
        "brute_write_exfil"
    };

    public CyberDatasetGenerator(long seed) {
        this.rng = new Random(seed);
    }

    public CyberDatasetGenerator() {
        this(42L);
    }

    public List<List<String>> generateTrainingNormal(int count) {
        return generateFromPatterns(
                count, NORMAL_PATTERNS, NORMAL_VARIANT_WEIGHTS, NORMAL_TYPE_WEIGHTS);
    }

    public List<List<String>> generateTrainingAttack(int count) {
        return generateFromPatterns(
                count, ATTACK_PATTERNS, ATTACK_VARIANT_WEIGHTS, ATTACK_TYPE_WEIGHTS);
    }

    public List<List<String>> generateTestNormal(int count) {
        // Same distribution as training normals
        return generateFromPatterns(
                count, NORMAL_PATTERNS, NORMAL_VARIANT_WEIGHTS, NORMAL_TYPE_WEIGHTS);
    }

    public List<List<String>> generateTestAttackVariants(int totalCount) {
        List<List<String>> traces = new ArrayList<>();
        int perVariant = totalCount / TEST_ATTACK_VARIANTS.length;
        int remainder = totalCount % TEST_ATTACK_VARIANTS.length;

        for (int v = 0; v < TEST_ATTACK_VARIANTS.length; v++) {
            int n = perVariant + (v < remainder ? 1 : 0);
            for (int i = 0; i < n; i++) {
                traces.add(new ArrayList<>(Arrays.asList(TEST_ATTACK_VARIANTS[v])));
            }
        }
        Collections.shuffle(traces, rng);
        return traces;
    }

    public String getTestVariantLabel(List<String> trace) {
        for (int v = 0; v < TEST_ATTACK_VARIANTS.length; v++) {
            if (trace.equals(Arrays.asList(TEST_ATTACK_VARIANTS[v]))) {
                return TEST_VARIANT_LABELS[v];
            }
        }
        return "unknown";
    }

    public void writeTracesFile(List<List<String>> traces, File output) throws IOException {
        try (FileWriter fw = new FileWriter(output, StandardCharsets.UTF_8)) {
            for (List<String> trace : traces) {
                fw.write(String.join(" ", trace));
                fw.write(" end$\n");
            }
        }
    }

    private List<List<String>> generateFromPatterns(
            int count, String[][][] patterns, double[][] variantWeights, double[] typeWeights) {

        List<List<String>> traces = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int type = weightedChoice(typeWeights);
            int variant = weightedChoice(variantWeights[type]);
            traces.add(new ArrayList<>(Arrays.asList(patterns[type][variant])));
        }
        Collections.shuffle(traces, rng);
        return traces;
    }

    private int weightedChoice(double[] weights) {
        double r = rng.nextDouble();
        double cumulative = 0.0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (r < cumulative) return i;
        }
        return weights.length - 1;
    }
}
