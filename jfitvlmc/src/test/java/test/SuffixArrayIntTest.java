package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import suffixarray.SuffixArrayInt;

public class SuffixArrayIntTest {

    private ArrayList<Integer> toList(Integer... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    @Test
    void testConstruction() {
        ArrayList<Integer> data = toList(1, 2, 3, 1, 2, 3);
        SuffixArrayInt sa = new SuffixArrayInt(data);
        assertEquals(6, sa.length());
    }

    @Test
    void testCount_existingPattern() {
        // data: 1 2 3 1 2 3
        // pattern "1 2" appears at positions 0 and 3
        ArrayList<Integer> data = toList(1, 2, 3, 1, 2, 3);
        SuffixArrayInt sa = new SuffixArrayInt(data);
        int[] result = sa.count(toList(1, 2));
        assertTrue(
                result[1] >= 2, "Pattern [1,2] should appear at least 2 times, got " + result[1]);
    }

    @Test
    void testCount_missingPattern() {
        ArrayList<Integer> data = toList(1, 2, 3, 1, 2, 3);
        SuffixArrayInt sa = new SuffixArrayInt(data);
        int[] result = sa.count(toList(9, 9));
        assertEquals(-1, result[0], "Pattern [9,9] should not be found");
    }

    @Test
    void testCount_singleElement() {
        ArrayList<Integer> data = toList(1, 2, 3, 1, 2, 3);
        SuffixArrayInt sa = new SuffixArrayInt(data);
        int[] result = sa.count(toList(3));
        assertTrue(result[1] >= 2, "Pattern [3] should appear at least 2 times");
    }

    @Test
    void testSearch_existingPattern() {
        ArrayList<Integer> data = toList(1, 2, 3, 4, 5);
        SuffixArrayInt sa = new SuffixArrayInt(data);
        int result = sa.search(0, sa.length() - 1, toList(3, 4));
        assertTrue(result >= 0, "Pattern [3,4] should be found");
    }

    @Test
    void testSearch_missingPattern() {
        ArrayList<Integer> data = toList(1, 2, 3, 4, 5);
        SuffixArrayInt sa = new SuffixArrayInt(data);
        int result = sa.search(0, sa.length() - 1, toList(9));
        assertEquals(-1, result, "Pattern [9] should not be found");
    }

    @Test
    void testLcp() {
        ArrayList<Integer> data = toList(1, 2, 1, 2, 3);
        SuffixArrayInt sa = new SuffixArrayInt(data);
        sa.buildLCPArraykasai();
        int[] lcp = sa.getLCP();
        assertNotNull(lcp);
        assertEquals(data.size(), lcp.length);
    }

    @Test
    void testPatternLongerThanSuffix() {
        // Pattern longer than any suffix should still work (bounds check)
        ArrayList<Integer> data = toList(1, 2);
        SuffixArrayInt sa = new SuffixArrayInt(data);
        int[] result = sa.count(toList(1, 2, 3, 4, 5));
        // Should not find it (pattern longer than data)
        assertTrue(result[1] <= 0, "Longer pattern should not match");
    }
}
