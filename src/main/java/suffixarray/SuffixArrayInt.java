package suffixarray;
/******************************************************************************
 *  Compilation:  javac SuffixArray.java
 *  Execution:    java SuffixArray < input.txt
 *  Dependencies: StdIn.java StdOut.java
 *  Data files:   https://algs4.cs.princeton.edu/63suffix/abra.txt
 *
 *  A data type that computes the suffix array of a string.
 *
 *   % java SuffixArray < abra.txt
 *    i ind lcp rnk  select
 *   ---------------------------
 *    0  11   -   0  "!"
 *    1  10   0   1  "A!"
 *    2   7   1   2  "ABRA!"
 *    3   0   4   3  "ABRACADABRA!"
 *    4   3   1   4  "ACADABRA!"
 *    5   5   1   5  "ADABRA!"
 *    6   8   0   6  "BRA!"
 *    7   1   3   7  "BRACADABRA!"
 *    8   4   0   8  "CADABRA!"
 *    9   6   0   9  "DABRA!"
 *   10   9   0  10  "RA!"
 *   11   2   2  11  "RACADABRA!"
 *
 *  See SuffixArrayX.java for an optimized version that uses 3-way
 *  radix quicksort and does not use the nested class Suffix.
 *
 ******************************************************************************/

import java.util.ArrayList;
import java.util.Arrays;

import vlmc.NextSymbolsDistribution;

/**
 * The {@code SuffixArray} class represents a suffix array of a string of length
 * <em>n</em>. It supports the <em>selecting</em> the <em>i</em>th smallest
 * suffix, getting the <em>index</em> of the <em>i</em>th smallest suffix,
 * computing the length of the <em>longest common prefix</em> between the
 * <em>i</em>th smallest suffix and the <em>i</em>-1st smallest suffix, and
 * determining the <em>rank</em> of a query string (which is the number of
 * suffixes strictly less than the query string).
 * <p>
 * This implementation uses a nested class {@code Suffix} to represent a suffix
 * of a string (using constant time and space) and {@code Arrays.sort()} to sort
 * the array of suffixes. The <em>index</em> and <em>length</em> operations
 * takes constant time in the worst case. The <em>lcp</em> operation takes time
 * proportional to the length of the longest common prefix. The <em>select</em>
 * operation takes time proportional to the length of the suffix and should be
 * used primarily for debugging.
 * <p>
 * For alternate implementations of the same API, see {@link SuffixArrayX},
 * which is faster in practice (uses 3-way radix quicksort) and uses less memory
 * (does not create {@code Suffix} objects) and <a href =
 * "https://algs4.cs.princeton.edu/63suffix/SuffixArrayJava6.java.html">SuffixArrayJava6.java</a>,
 * which relies on the constant-time substring extraction method that existed in
 * Java 6.
 * <p>
 * For additional documentation, see
 * <a href="https://algs4.cs.princeton.edu/63suffix">Section 6.3</a> of
 * <i>Algorithms, 4th Edition</i> by Robert Sedgewick and Kevin Wayne.
 */
public class SuffixArrayInt {
	private SuffixInt[] suffixes;
	private int[] LCP;
	private ArrayList<Integer> data=null;
	private ArrayList<ArrayList<NextSymbolsDistribution>> sufDist=null;
	private int[] LCP_LR;

	/**
	 * Initializes a suffix array for the given {@code text} string.
	 * 
	 * @param text the input string
	 */
	public SuffixArrayInt(ArrayList<Integer> data) {
		this.data=data;
		int n = data.size();
		this.suffixes = new SuffixInt[n];
		for (int i = 0; i < n; i++) {
			suffixes[i] = new SuffixInt(data, i);
		}
		this.sufDist=new ArrayList<ArrayList<NextSymbolsDistribution>>(n);
		System.out.println("sorting suffixes");
		Arrays.sort(suffixes);
		this.LCP_LR=new int[this.length()];
		
//		this.buildLCPArraykasai();
//		this.buildLCP_LR(0, 0, this.length()-1);
	}

	public static class SuffixInt implements Comparable<SuffixInt> {
		private final ArrayList<Integer> data;
		private final int index;

		public SuffixInt(ArrayList<Integer> data, int index) {
			this.data = data;
			this.index = index;
		}

		private int length() {
			return this.data.size() - index;
		}

		private Integer symbolAt(int i) {
			return data.get(index + i);
		}

		public int compareTo(SuffixInt that) {
			if (this == that)
				return 0; // optimization
			int n = Math.min(this.length(), that.length());
			for (int i = 0; i < n; i++) {
				if (this.symbolAt(i) < that.symbolAt(i))
					return -1;
				if (this.symbolAt(i) > that.symbolAt(i))
					return +1;
			}
			return this.length() - that.length();
		}

		public String toString() {
			StringBuilder suffix=new StringBuilder();
			for(int i=this.index;i<this.data.size(); i++) {
				suffix.append(String.format("%d ",this.data.get(i)));
			}
			return suffix.toString();
		}

		public int getIndex() {
			return index;
		}
	}

	/**
	 * Returns the length of the input string.
	 * 
	 * @return the length of the input string
	 */
	public int length() {
		return suffixes.length;
	}

	/**
	 * Returns the index into the original string of the <em>i</em>th smallest
	 * suffix. That is, {@code text.substring(sa.index(i))} is the <em>i</em>th
	 * smallest suffix.
	 * 
	 * @param i an integer between 0 and <em>n</em>-1
	 * @return the index into the original string of the <em>i</em>th smallest
	 *         suffix
	 * @throws java.lang.IllegalArgumentException unless {@code 0 <= i < n}
	 */
	public int index(int i) {
		if (i < 0 || i >= suffixes.length)
			throw new IllegalArgumentException();
		return suffixes[i].index;
	}

	/**
	 * Returns the length of the longest common prefix of the <em>i</em>th smallest
	 * suffix and the <em>i</em>-1st smallest suffix.
	 * 
	 * @param i an integer between 1 and <em>n</em>-1
	 * @return the length of the longest common prefix of the <em>i</em>th smallest
	 *         suffix and the <em>i</em>-1st smallest suffix.
	 * @throws java.lang.IllegalArgumentException unless {@code 1 <= i < n}
	 */
	public int lcp(int i) {
		if (i < 1 || i >= suffixes.length)
			throw new IllegalArgumentException();
		return lcpSuffix(suffixes[i], suffixes[i - 1]);
	}
	
	public void buildLCPArraykasai() {
	    int n = this.length(); 
	  
	    // To store LCP array 
	    this.LCP=new int[n]; 
	  
	    // An auxiliary array to store inverse of suffix array 
	    // elements. For example if suffixArr[0] is 5, the 
	    // invSuff[5] would store 0.  This is used to get next 
	    // suffix string from suffix array. 
	    int[] invSuff=new int[n];
	    
	  
	    // Fill values in invSuff[] 
	    for (int i=0; i < n; i++) 
	        invSuff[this.suffixes[i].getIndex()] = i; 
	  
	    // Initialize length of previous LCP 
	    int k = 0; 
	  
	    // Process all suffixes one by one starting from 
	    // first suffix in txt[] 
	    for (int i=0; i<n; i++) 
	    { 
	        /* If the current suffix is at n-1, then we don’t 
	           have next substring to consider. So lcp is not 
	           defined for this substring, we put zero. */
	        if (invSuff[i] == n-1) 
	        { 
	            k = 0; 
	            continue; 
	        } 
	  
	        /* j contains index of the next substring to 
	           be considered  to compare with the present 
	           substring, i.e., next string in suffix array */
	        int j = this.suffixes[invSuff[i]+1].getIndex(); 
	  
	        // Directly start matching from k'th index as 
	        // at-least k-1 characters will match 
	        while (i+k<n && j+k<n && this.data.get(i+k)==this.data.get(j+k)) 
	            k++; 
	  
	        this.LCP[invSuff[i]] = k; // lcp for the present suffix. 
	  
	        // Deleting the starting character from the string. 
	        if (k>0) 
	            k--; 
	    } 
	}
	
	public void buildLCP_LR(int index, int low, int high)
	{
	    if(low == high)
	    {
	        this.LCP_LR[index] = this.LCP[low];
	        return;
	    }

	    int mid = (low + high) / 2;

	    buildLCP_LR(2*index, low, mid);
	    buildLCP_LR(2*index+1, mid + 1, high);

	    this.LCP_LR[index] = Math.min(this.LCP_LR[2*index], this.LCP_LR[2*index + 1]);
	}

	// longest common prefix of s and t
	private static int lcpSuffix(SuffixInt s, SuffixInt t) {
		int n = Math.min(s.length(), t.length());
		for (int i = 0; i < n; i++) {
			if (s.symbolAt(i) != t.symbolAt(i))
				return i;
		}
		return n;
	}

	/**
	 * Returns the <em>i</em>th smallest suffix as a string.
	 * 
	 * @param i the index
	 * @return the <em>i</em> smallest suffix as a string
	 * @throws java.lang.IllegalArgumentException unless {@code 0 <= i < n}
	 */
	public String select(int i) {
		if (i < 0 || i >= suffixes.length)
			throw new IllegalArgumentException();
		return suffixes[i].toString();
	}

	/**
	 * Returns the number of suffixes strictly less than the {@code query} string.
	 * We note that {@code rank(select(i))} equals {@code i} for each {@code i}
	 * between 0 and <em>n</em>-1.
	 * 
	 * @param query the query string
	 * @return the number of suffixes strictly less than {@code query}
	 */
	public int rank(ArrayList<Integer> query) {
		int lo = 0, hi = suffixes.length - 1;
		while (lo <= hi) {
			int mid = lo + (hi - lo) / 2;
			int cmp = compare(query, suffixes[mid]);
			if (cmp < 0)
				hi = mid - 1;
			else if (cmp > 0)
				lo = mid + 1;
			else
				return mid;
		}
		return lo;
	}

	// compare query string to suffix
	private static int compare(ArrayList<Integer> query, SuffixInt suffix) {
		int n = Math.min(query.size(), suffix.length());
		for (int i = 0; i < n; i++) {
			if (query.get(i) < suffix.symbolAt(i))
				return -1;
			if (query.get(i) > suffix.symbolAt(i))
				return +1;
		} 
		return query.size() - suffix.length();
	}

	private static int myCompare(ArrayList<Integer> query, SuffixInt suffix) {
		int res=0;
		try {
			for (int i = 0; i < query.size(); i++) {
				if (query.get(i) < suffix.symbolAt(i)) {
					res=-1;
					break;
				}
				if (query.get(i) > suffix.symbolAt(i)) {
					res=1;
					break;
				}
			}
		} catch (IndexOutOfBoundsException e) {
			// significa che query è più lunga di suffix ma i primi caratteri coincidono
			// quindi query > suffix
			return 1;
		}
		return res;
	}

	private static int[] myCompare(ArrayList<Integer> query, SuffixInt suffix, int k) {
		int i = k;
		try {
			for (; i < query.size(); i++) {
				if (query.get(i) < suffix.symbolAt(i))
					return new int[] { -1, i };
				if (query.get(i) > suffix.symbolAt(i))
					return new int[] { 1, i };
			}
		} catch (StringIndexOutOfBoundsException e) {
			// significa che query è più lunga di suffix ma i primi caratteri coincidono
			// quindi query > suffix
			return new int[] { 1, i };
		}
		return new int[] { 0, i };
	}

	public SuffixInt[] getSuffixes() {
		return suffixes;
	}
	
	public int[] getLCP() {
		return this.LCP;
	}
	
	public int[] getLCP_LR() {
		return LCP_LR;
	}
	
	public int search(int low, int high, ArrayList<Integer> pat) {
		if (high >= low) {
			int mid = (low + high) / 2; 
			int res = SuffixArrayInt.myCompare(pat, this.suffixes[mid]);
			if (res == 0)
				return mid;
			else if (res > 0)
				return this.search((mid + 1), high, pat);
			else
				return this.search(low, (mid - 1), pat);
		}
		return -1;
	}

	// todo implement the counting logic
	public int[] count(ArrayList<Integer> pat) {
		/*
		 * if x is present in arr[] then returns the count of occurrences of x,
		 * otherwise returns -1.
		 */
		int i; // index of first occurrence of x in arr[0..n-1]
		int j; // index of last occurrence of x in arr[0..n-1]

		/* get the index of first occurrence of x */
		i = this.first(0, this.length() - 1, pat);
		//i = this.first(pat);

		/* If x doesn't exist in arr[] then return -1 */
		if (i == -1)
			return new int[]{i,i};

		/*
		 * Else get the index of last occurrence of x. Note that we are only looking in
		 * the subarray after first occurrence
		 */
		j = this.last(0, this.length() - 1, pat);
		//j = this.last(pat);

		try {
			if ((j - i + 1) < 0) {
				throw new Exception("negative number of occurence");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		/* return count */
		return new int[]{i,j - i + 1};
	}

	public int first(int low, int high, ArrayList<Integer> pat) {
		if (high >= low) {
			int mid = (low + high) / 2; /* low + (high - low)/2; */
			int res = SuffixArrayInt.myCompare(pat, this.suffixes[mid]);
			if ((mid == 0 || SuffixArrayInt.myCompare(pat, this.suffixes[mid - 1]) > 0) && res == 0)
				return mid;
			else if (res > 0)
				return this.first((mid + 1), high, pat);
			else
				return this.first(low, (mid - 1), pat);
		}
		return -1;
	}

	public int last(int low, int high, ArrayList<Integer> pat) {
		if (high >= low) {
			int mid = (low + high) / 2; /* low + (high - low)/2; */
			int res = SuffixArrayInt.myCompare(pat, this.suffixes[mid]);
			if ((mid == this.length() - 1 || SuffixArrayInt.myCompare(pat, this.suffixes[mid + 1]) < 0) && res == 0) {
				return mid;
			} else if (res < 0) {
				return this.last(low, (mid - 1), pat);
			} else {
				return this.last((mid + 1), high, pat);
			}
		}
		return -1;
	}


	/**
	 * Unit tests the {@code SuffixArray} data type.
	 *
	 * @param args the command-line arguments
	 */
//    public static void main(String[] args) {
//        String s = StdIn.readAll().replaceAll("\\s+", " ").trim();
//        SuffixArray suffix = new SuffixArray(s);
//
//        // StdOut.println("rank(" + args[0] + ") = " + suffix.rank(args[0]));
//
//        StdOut.println("  i ind lcp rnk select");
//        StdOut.println("---------------------------");
//
//        for (int i = 0; i < s.length(); i++) {
//            int index = suffix.index(i);
//            String ith = "\"" + s.substring(index, Math.min(index + 50, s.length())) + "\"";
//            assert s.substring(index).equals(suffix.select(i));
//            int rank = suffix.rank(s.substring(index));
//            if (i == 0) {
//                StdOut.printf("%3d %3d %3s %3d %s\n", i, index, "-", rank, ith);
//            }
//            else {
//                int lcp = suffix.lcp(i);
//                StdOut.printf("%3d %3d %3d %3d %s\n", i, index, lcp, rank, ith);
//            }
//        }
//    }

}