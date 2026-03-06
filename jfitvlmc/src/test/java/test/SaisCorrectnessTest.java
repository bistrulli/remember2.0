package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;
import suffixarray.SuffixArray;

public class SaisCorrectnessTest {

    @Test
    void testAbracadabra() {
        SuffixArray sa = new SuffixArray("abracadabra");
        // Known correct suffix array for "abracadabra":
        // Sorted suffixes with their original indices:
        //  0: a           (10)
        //  1: abra         (7)
        //  2: abracadabra  (0)
        //  3: acadabra     (3)
        //  4: adabra       (5)
        //  5: bra          (8)
        //  6: bracadabra   (1)
        //  7: cadabra      (4)
        //  8: dabra        (6)
        //  9: ra           (9)
        // 10: racadabra    (2)
        int[] expected = {10, 7, 0, 3, 5, 8, 1, 4, 6, 9, 2};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], sa.index(i), "SA[" + i + "] should be " + expected[i]);
        }
    }

    @Test
    void testAllSameCharacters() {
        SuffixArray sa = new SuffixArray("aaaaaaa");
        assertEquals(7, sa.length());
        // All suffixes start with 'a', sorted by length (shortest first)
        for (int i = 0; i < sa.length() - 1; i++) {
            assertTrue(
                    sa.index(i) > sa.index(i + 1),
                    "For all-same input, shorter suffixes come first");
        }
        assertSorted(sa);
    }

    @Test
    void testAlreadySorted() {
        SuffixArray sa = new SuffixArray("abcdefg");
        assertEquals(7, sa.length());
        assertSorted(sa);
    }

    @Test
    void testReverseSorted() {
        SuffixArray sa = new SuffixArray("gfedcba");
        assertEquals(7, sa.length());
        assertSorted(sa);
        // "a" should be first suffix
        assertEquals(6, sa.index(0));
    }

    @Test
    void testSingleCharacter() {
        SuffixArray sa = new SuffixArray("a");
        assertEquals(1, sa.length());
        assertEquals(0, sa.index(0));
    }

    @Test
    void testTwoCharacters() {
        SuffixArray sa = new SuffixArray("ba");
        assertEquals(2, sa.length());
        // "a" < "ba"
        assertEquals(1, sa.index(0));
        assertEquals(0, sa.index(1));
    }

    @Test
    void testRepeatingPattern() {
        SuffixArray sa = new SuffixArray("abcabcabc");
        assertEquals(9, sa.length());
        assertSorted(sa);
        // "abc" appears 3 times
        int[] count = sa.count("abc");
        assertEquals(3, count[1]);
    }

    @Test
    void testVlmcTraceFormat() {
        String text = "A B C end$ A B D end$ A C end$";
        SuffixArray sa = new SuffixArray(text);
        // Same assertions as SuffixArrayTest
        assertEquals(2, sa.count("A B")[1]);
        assertEquals(1, sa.count("A B C")[1]);
        assertEquals(1, sa.count("A B D")[1]);
        assertEquals(1, sa.count("A C")[1]);
        assertEquals(3, sa.count("end$")[1]);
        assertEquals(-1, sa.count("X Y")[1]);
    }

    @Test
    void testLargeInput() {
        Random rng = new Random(42);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100_000; i++) {
            sb.append((char) ('a' + rng.nextInt(26)));
        }
        SuffixArray sa = new SuffixArray(sb.toString());
        assertEquals(100_000, sa.length());
        assertIsPermutation(sa);
        assertSorted(sa);
    }

    @Test
    void testSuffixArrayIsSorted() {
        // Test with various inputs
        String[] inputs = {"banana", "mississippi", "the quick brown fox", "zzzzz", "azbzcz"};
        for (String input : inputs) {
            SuffixArray sa = new SuffixArray(input);
            assertSorted(sa);
        }
    }

    private void assertSorted(SuffixArray sa) {
        for (int i = 1; i < sa.length(); i++) {
            String prev = sa.select(i - 1);
            String curr = sa.select(i);
            assertTrue(
                    prev.compareTo(curr) < 0,
                    "Suffix at rank "
                            + (i - 1)
                            + " should be < rank "
                            + i
                            + ": \""
                            + prev.substring(0, Math.min(20, prev.length()))
                            + "\" vs \""
                            + curr.substring(0, Math.min(20, curr.length()))
                            + "\"");
        }
    }

    private void assertIsPermutation(SuffixArray sa) {
        boolean[] seen = new boolean[sa.length()];
        for (int i = 0; i < sa.length(); i++) {
            int idx = sa.index(i);
            assertTrue(idx >= 0 && idx < sa.length(), "Index out of range: " + idx);
            assertFalse(seen[idx], "Duplicate index in SA: " + idx);
            seen[idx] = true;
        }
    }
}
