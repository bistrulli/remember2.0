package suffixarray;

import java.util.logging.Logger;

/**
 * Suffix array built using the SA-IS algorithm (Nong, Zhang, Chan 2009) in O(n) time and space.
 *
 * <p>Provides the same public API as the original comparison-sort implementation: {@code count},
 * {@code first}, {@code last}, {@code index}, {@code lcp}, {@code select}, {@code rank}, {@code
 * length}.
 */
public class SuffixArray {
    private static final Logger LOGGER = Logger.getLogger(SuffixArray.class.getName());

    private final char[] text;
    private final int n;
    private final int[] sa;

    public SuffixArray(String input) {
        this.n = input.length();
        if (n == 0) {
            this.text = new char[0];
            this.sa = new int[0];
            return;
        }
        // Build int array with sentinel 0 (smallest value)
        int[] t = new int[n + 1];
        for (int i = 0; i < n; i++) {
            t[i] = input.charAt(i) + 1; // shift by 1 so sentinel 0 is smallest
        }
        t[n] = 0; // sentinel
        this.text = input.toCharArray();

        int[] fullSa = sais(t, t.length, 65536 + 1);

        // Remove sentinel position from SA (it's always at index 0)
        this.sa = new int[n];
        int j = 0;
        for (int idx : fullSa) {
            if (idx != n) { // skip the sentinel suffix
                sa[j++] = idx;
            }
        }
    }

    // ==================== SA-IS Algorithm ====================

    private static int[] sais(int[] t, int n, int alphabetSize) {
        // Classify each position as S-type or L-type
        boolean[] sType = new boolean[n];
        sType[n - 1] = true; // sentinel is S-type
        for (int i = n - 2; i >= 0; i--) {
            if (t[i] < t[i + 1]) {
                sType[i] = true;
            } else if (t[i] > t[i + 1]) {
                sType[i] = false;
            } else {
                sType[i] = sType[i + 1];
            }
        }

        // Compute bucket boundaries
        int[] bucketSizes = new int[alphabetSize];
        for (int i = 0; i < n; i++) {
            bucketSizes[t[i]]++;
        }
        int[] bucketStarts = new int[alphabetSize];
        int[] bucketEnds = new int[alphabetSize];
        int sum = 0;
        for (int i = 0; i < alphabetSize; i++) {
            bucketStarts[i] = sum;
            sum += bucketSizes[i];
            bucketEnds[i] = sum - 1;
        }

        // Find LMS positions
        int[] lmsPositions = findLmsPositions(sType, n);

        // Step 1: Induced sort with LMS positions
        int[] sa = new int[n];
        inducedSort(t, sa, sType, lmsPositions, bucketSizes, bucketStarts, alphabetSize, n);

        // Step 2: Name the LMS substrings
        int[] names = new int[n];
        java.util.Arrays.fill(names, -1);
        int currentName = 0;
        int lastLms = -1;
        for (int i = 0; i < n; i++) {
            if (isLms(sType, sa[i])) {
                if (lastLms >= 0 && !lmsSubstringsEqual(t, sType, lastLms, sa[i], n)) {
                    currentName++;
                }
                names[sa[i]] = currentName;
                lastLms = sa[i];
            }
        }
        int nameCount = currentName + 1;

        // Step 3: Determine sorted order of LMS suffixes
        // Rebuild lmsPositions in text order
        int[] lmsInTextOrder = new int[lmsPositions.length];
        int li = 0;
        for (int i = 0; i < n; i++) {
            if (isLms(sType, i)) {
                lmsInTextOrder[li++] = i;
            }
        }

        int[] sortedLms;
        if (nameCount < lmsPositions.length) {
            // Not all names unique — recurse on reduced string
            int[] reducedString = new int[lmsPositions.length];
            int ri = 0;
            for (int i = 0; i < n; i++) {
                if (names[i] >= 0) {
                    reducedString[ri++] = names[i];
                }
            }

            int[] reducedSa = sais(reducedString, reducedString.length, nameCount);

            sortedLms = new int[lmsPositions.length];
            for (int i = 0; i < reducedSa.length; i++) {
                sortedLms[i] = lmsInTextOrder[reducedSa[i]];
            }
        } else {
            // All names unique — sort LMS directly by name
            sortedLms = new int[lmsPositions.length];
            for (int i = 0; i < n; i++) {
                if (names[i] >= 0) {
                    sortedLms[names[i]] = i;
                }
            }
        }

        // Final induced sort with correctly ordered LMS (always needed)
        inducedSort(t, sa, sType, sortedLms, bucketSizes, bucketStarts, alphabetSize, n);

        return sa;
    }

    private static boolean isLms(boolean[] sType, int i) {
        return i > 0 && sType[i] && !sType[i - 1];
    }

    private static int[] findLmsPositions(boolean[] sType, int n) {
        int count = 0;
        for (int i = 1; i < n; i++) {
            if (sType[i] && !sType[i - 1]) count++;
        }
        int[] lms = new int[count];
        int j = 0;
        for (int i = 1; i < n; i++) {
            if (sType[i] && !sType[i - 1]) lms[j++] = i;
        }
        return lms;
    }

    private static boolean lmsSubstringsEqual(int[] t, boolean[] sType, int pos1, int pos2, int n) {
        for (int i = 0; ; i++) {
            boolean end1 = (i > 0 && isLms(sType, pos1 + i));
            boolean end2 = (i > 0 && isLms(sType, pos2 + i));
            if (pos1 + i >= n || pos2 + i >= n) return end1 && end2;
            if (t[pos1 + i] != t[pos2 + i] || end1 != end2) return false;
            if (end1 && end2) return true;
        }
    }

    private static void inducedSort(
            int[] t,
            int[] sa,
            boolean[] sType,
            int[] lms,
            int[] bucketSizes,
            int[] bucketStartsBase,
            int alphabetSize,
            int n) {
        java.util.Arrays.fill(sa, -1);

        // Place LMS suffixes at the ends of their buckets
        int[] bucketEnds = new int[alphabetSize];
        computeBucketEnds(bucketSizes, bucketEnds, alphabetSize);
        for (int i = lms.length - 1; i >= 0; i--) {
            int c = t[lms[i]];
            sa[bucketEnds[c]] = lms[i];
            bucketEnds[c]--;
        }

        // Induce L-type suffixes (left-to-right scan)
        int[] bucketStarts = new int[alphabetSize];
        computeBucketStarts(bucketSizes, bucketStarts, alphabetSize);
        for (int i = 0; i < n; i++) {
            if (sa[i] > 0 && !sType[sa[i] - 1]) {
                int c = t[sa[i] - 1];
                sa[bucketStarts[c]] = sa[i] - 1;
                bucketStarts[c]++;
            } else if (sa[i] == 0 && !sType[n - 1]) {
                // handle position 0 wrapping — shouldn't happen with sentinel but be safe
            }
        }

        // Induce S-type suffixes (right-to-left scan)
        computeBucketEnds(bucketSizes, bucketEnds, alphabetSize);
        for (int i = n - 1; i >= 0; i--) {
            if (sa[i] > 0 && sType[sa[i] - 1]) {
                int c = t[sa[i] - 1];
                sa[bucketEnds[c]] = sa[i] - 1;
                bucketEnds[c]--;
            }
        }
    }

    private static void computeBucketStarts(int[] sizes, int[] starts, int alphabetSize) {
        int s = 0;
        for (int i = 0; i < alphabetSize; i++) {
            starts[i] = s;
            s += sizes[i];
        }
    }

    private static void computeBucketEnds(int[] sizes, int[] ends, int alphabetSize) {
        int s = 0;
        for (int i = 0; i < alphabetSize; i++) {
            s += sizes[i];
            ends[i] = s - 1;
        }
    }

    // ==================== Public API (unchanged signatures) ====================

    public int length() {
        return n;
    }

    public int index(int i) {
        if (i < 0 || i >= n) throw new IllegalArgumentException();
        return sa[i];
    }

    public int lcp(int i) {
        if (i < 1 || i >= n) throw new IllegalArgumentException();
        int s1 = sa[i];
        int s2 = sa[i - 1];
        int maxLen = Math.min(n - s1, n - s2);
        for (int k = 0; k < maxLen; k++) {
            if (text[s1 + k] != text[s2 + k]) return k;
        }
        return maxLen;
    }

    public String select(int i) {
        if (i < 0 || i >= n) throw new IllegalArgumentException();
        return new String(text, sa[i], n - sa[i]);
    }

    public int rank(String query) {
        int lo = 0, hi = n - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            int cmp = compareExact(query, sa[mid]);
            if (cmp < 0) hi = mid - 1;
            else if (cmp > 0) lo = mid + 1;
            else return mid;
        }
        return lo;
    }

    /**
     * Compares query as an exact string against the suffix starting at suffixStart. Used by rank()
     * where query.length() matters for ordering.
     */
    private int compareExact(String query, int suffixStart) {
        int qLen = query.length();
        int sLen = n - suffixStart;
        int limit = Math.min(qLen, sLen);
        for (int i = 0; i < limit; i++) {
            char qc = query.charAt(i);
            char sc = text[suffixStart + i];
            if (qc < sc) return -1;
            if (qc > sc) return +1;
        }
        return qLen - sLen;
    }

    /**
     * Compares pattern as a prefix against the suffix starting at suffixStart. Returns 0 if pattern
     * is a prefix of the suffix (used by count/first/last).
     */
    private int comparePrefix(String pat, int suffixStart) {
        int pLen = pat.length();
        int sLen = n - suffixStart;
        int limit = Math.min(pLen, sLen);
        for (int i = 0; i < limit; i++) {
            char pc = pat.charAt(i);
            char sc = text[suffixStart + i];
            if (pc < sc) return -1;
            if (pc > sc) return +1;
        }
        if (pLen <= sLen) return 0; // pattern is prefix of suffix — match
        return 1; // pattern longer than suffix
    }

    public int[] count(String pat) {
        int i = first(0, n - 1, pat);
        if (i == -1) return new int[] {-1, -1};
        int j = last(0, n - 1, pat);
        return new int[] {i, j - i + 1};
    }

    public int first(int low, int high, String pat) {
        if (high >= low) {
            int mid = (low + high) / 2;
            int res = comparePrefix(pat, sa[mid]);
            if (res == 0 && (mid == 0 || comparePrefix(pat, sa[mid - 1]) > 0)) {
                return mid;
            } else if (res > 0) {
                return first(mid + 1, high, pat);
            } else {
                return first(low, mid - 1, pat);
            }
        }
        return -1;
    }

    public int last(int low, int high, String pat) {
        if (high >= low) {
            int mid = (low + high) / 2;
            int res = comparePrefix(pat, sa[mid]);
            if (res == 0 && (mid == n - 1 || comparePrefix(pat, sa[mid + 1]) < 0)) {
                return mid;
            } else if (res < 0) {
                return last(low, mid - 1, pat);
            } else {
                return last(mid + 1, high, pat);
            }
        }
        return -1;
    }
}
