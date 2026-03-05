package test;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import suffixarray.SuffixArray;

public class SuffixArrayTest {

	private SuffixArray sa;
	// Text: 3 traces separated by end$
	// Trace 1: A B C end$
	// Trace 2: A B D end$
	// Trace 3: A C end$
	private static final String TEXT = "A B C end$ A B D end$ A C end$";

	@BeforeEach
	public void setup() {
		sa = new SuffixArray(TEXT);
	}

	@Test
	public void testCountPatternPresent() {
		// "A B" appears in trace 1 and trace 2
		int[] result = sa.count("A B");
		assertEquals(2, result[1]);
	}

	@Test
	public void testCountPatternAbsent() {
		// "X Y" does not appear
		int[] result = sa.count("X Y");
		assertEquals(-1, result[1]);
	}

	@Test
	public void testCountSingleToken() {
		// "A" appears 3 times (once per trace)
		int[] result = sa.count("A ");
		assertEquals(3, result[1]);
	}

	@Test
	public void testCountLongerPatternFewerOccurrences() {
		// A longer pattern cannot have more occurrences than a shorter prefix
		int countAB = sa.count("A B")[1];
		int countABC = sa.count("A B C")[1];
		int countABD = sa.count("A B D")[1];
		assertTrue(countABC <= countAB, "A B C count should be <= A B count");
		assertTrue(countABD <= countAB, "A B D count should be <= A B count");
		assertEquals(1, countABC);
		assertEquals(1, countABD);
	}

	@Test
	public void testCountSpecificPatterns() {
		// "A C" appears in trace 3 (and as substring "A B C" does NOT match "A C")
		// Actually in the suffix array, "A C" as a prefix matches:
		// - "A C end$" from trace 3
		// - "A C end$ A B D end$ A C end$" is NOT a match because after first trace
		//   we need to check carefully
		// Let's verify: the text is "A B C end$ A B D end$ A C end$"
		// "A C" appears at position 21 (start of "A C end$" at the end)
		int[] result = sa.count("A C");
		assertEquals(1, result[1]);
	}

	@Test
	public void testCountEnd$() {
		// "end$" appears 3 times
		int[] result = sa.count("end$");
		assertEquals(3, result[1]);
	}

	@Test
	public void testFirstAndLastConsistency() {
		// For a pattern with count N, last - first + 1 == N
		String pattern = "A B";
		int[] countResult = sa.count(pattern);
		int first = sa.first(0, sa.length() - 1, pattern);
		int last = sa.last(0, sa.length() - 1, pattern);
		assertTrue(first >= 0);
		assertTrue(last >= first);
		assertEquals(countResult[1], last - first + 1);
	}

	@Test
	public void testContextCounting() {
		// Verify context-based counting used by VLMC learning
		// "B C" should appear once (in trace 1: "A B C end$")
		assertEquals(1, sa.count("B C")[1]);
		// "B D" should appear once (in trace 2: "A B D end$")
		assertEquals(1, sa.count("B D")[1]);
		// "C end$" appears twice (trace 1 and trace 3)
		assertEquals(2, sa.count("C end$")[1]);
	}

	@Test
	public void testEmptyResult() {
		// Very specific pattern that doesn't exist
		int[] result = sa.count("Z Z Z");
		assertEquals(-1, result[1]);
	}
}
