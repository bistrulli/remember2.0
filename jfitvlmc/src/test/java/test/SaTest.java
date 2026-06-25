package test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import org.apache.commons.lang3.ArrayUtils;
import suffixarray.SuffixArray;

public class SaTest {

    public static void main(String[] args) {

        File inFile =
                new File("/Users/emilio/eclipse-workspace/lightWeightMB/sven_data/svenApel.txt");

        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(inFile), "UTF8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        String str = null;
        String content = "";
        try {
            while ((str = in.readLine()) != null) {
                content += str;
            }
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String query = "!$ f0$ #f1$ #f2$ f0_1$";

        long start = System.currentTimeMillis();
        SuffixArray sa = new SuffixArray(content);

        System.out.println("");

        System.out.println(sa.first(0, sa.length() - 1, query));
        System.out.println(sa.last(0, sa.length() - 1, query));
        System.out.println(ArrayUtils.toString(sa.count(query)));
    }
}
