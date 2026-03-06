package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import suffixarray.SuffixArray;

@Tag("e2e")
public class SuffixArrayPerfTest {

    @Test
    void testConstructionScalesLinearly() {
        Random rng = new Random(42);
        int[] sizes = {10_000, 50_000, 100_000, 500_000};
        long[] times = new long[sizes.length];

        for (int s = 0; s < sizes.length; s++) {
            String input = randomString(sizes[s], rng);
            long start = System.nanoTime();
            new SuffixArray(input);
            times[s] = System.nanoTime() - start;
        }

        // t(500K) / t(50K) should be roughly 10x for linear, allow up to 15x
        double ratio = (double) times[3] / times[1];
        assertTrue(
                ratio < 15.0,
                String.format(
                        "Construction should scale ~linearly: t(500K)=%dms, t(50K)=%dms, ratio=%.1f",
                        times[3] / 1_000_000, times[1] / 1_000_000, ratio));
    }

    @Test
    void testConstructionUnder1SecondFor1M() {
        String input = randomString(1_000_000, new Random(42));
        long start = System.nanoTime();
        new SuffixArray(input);
        long elapsed = System.nanoTime() - start;
        long elapsedMs = elapsed / 1_000_000;
        assertTrue(
                elapsedMs < 1000,
                "1M character SA construction should complete in <1s, took " + elapsedMs + "ms");
    }

    @Test
    void testCountPerformance() {
        String input = randomString(100_000, new Random(42));
        SuffixArray sa = new SuffixArray(input);
        Random rng = new Random(123);

        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            int pos = rng.nextInt(input.length() - 5);
            int len = 3 + rng.nextInt(8);
            len = Math.min(len, input.length() - pos);
            sa.count(input.substring(pos, pos + len));
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(
                elapsedMs < 1000,
                "10K count queries should complete in <1s, took " + elapsedMs + "ms");
    }

    private static String randomString(int length, Random rng) {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = (char) ('a' + rng.nextInt(26));
        }
        return new String(chars);
    }
}
