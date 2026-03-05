package test;

import java.util.ArrayList;
import java.util.Arrays;

import suffixarray.SuffixArrayInt;
import suffixarray.SuffixArrayInt.SuffixInt;

public class SaTestInt {

	public static void main(String[] args) {
		
		ArrayList<Integer> data=new ArrayList<Integer>(Arrays.asList(1,2,3,4,1,2,1,2,3,4,1));
		
		SuffixArrayInt sa=new SuffixArrayInt(data); 
		for(int i=0; i<data.size(); i++) {
			System.out.println(String.format("%s %d", sa.getSuffixes()[i].toString(),sa.getLCP()[i]));
		}
		System.out.println();
		
		for(int i=0; i<sa.getLCP_LR().length;i++) {
			System.out.println(sa.getLCP_LR()[i]);
		}
	}

}
