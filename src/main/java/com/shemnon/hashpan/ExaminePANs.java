package com.shemnon.hashpan;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Created by shemnon on 13 Nov 2013.
 *
 */
public class ExaminePANs {


    public static void main(String... arg) {

        List<String> pans = getPANs();
        List<String> hashesList = getHashes();
        Set<String> hashesSet = new HashSet<>(hashesList);


        List<String> prefixes = pans.stream()
                .map(s -> s.substring(0, 6))
                .distinct()
                .collect(Collectors.toList());

        pans.stream()
                .map(s -> new String[]{sha1(s), s})
                .filter(s -> hashesSet.contains(s[0]))
                .forEach(s -> System.out.println("card# - " + s[1] + " - hash " + s[0]));
        
        int n = 999999999;
        
//        for (int n : new int[] { 1, 10, 100, 1000, 10000, 100000, 1000, 1000, 1000}) {
        
            long start = System.currentTimeMillis();
            prefixes.parallelStream().forEach(prefix ->
                    LongStream.range(0, n)
                            .<String>mapToObj(l -> {
                                String postfix = "000000000" + l;
                                String s = prefix + postfix.substring(postfix.length() - 9);
                                return s + luhn16Expected(s);
                            })
                            .map(s -> new String[]{sha1(s), s})
                            .filter(s -> hashesSet.contains(s[0]))
                            .forEach(s -> System.out.println("card# - " + s[1] + " - hash " + s[0])));
            System.out.println(n + " - " +(System.currentTimeMillis() - start) / (double) n / prefixes.size() * 1000);
//        }
    }

    private static List<String> getPANs() {
        List<String> pans = new ArrayList<>(1024);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(ExaminePANs.class.getResourceAsStream("/pans.txt")))) {
            String s;
            while ((s = br.readLine()) != null) {
                pans.add(s);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return pans;
    }

    private static List<String> getHashes() {
        List<String> pans = new ArrayList<>(1024);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(ExaminePANs.class.getResourceAsStream("/hashes.txt")))) {
            String s;
            while ((s = br.readLine()) != null) {
                pans.add(s);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return pans;
    }

    static int[] sumdouble = {0, 2, 4, 6, 8, 1, 3, 5, 7, 9};

    /**
     * Lunh algorithm on a 16 digit string
     */
    public static int luhn16(String s) {
        char[] c = s.toCharArray();
        int sum = sumdouble[c[0] - '0'] +
                c[1] - '0' +
                sumdouble[c[2] - '0'] +
                c[3] - '0' +
                sumdouble[c[4] - '0'] +
                c[5] - '0' +
                sumdouble[c[6] - '0'] +
                c[7] - '0' +
                sumdouble[c[8] - '0'] +
                c[9] - '0' +
                sumdouble[c[10] - '0'] +
                c[11] - '0' +
                sumdouble[c[12] - '0'] +
                c[13] - '0' +
                sumdouble[c[14] - '0'] +
                c[15] - '0';
        return sum % 10;
    }

    /**
     * Lunh algorithm checksub digit on a 15 digit string
     */
    public static int luhn16Expected(String s) {
        char[] c = s.toCharArray();
        int sum = sumdouble[c[0] - '0'] +
                c[1] - '0' +
                sumdouble[c[2] - '0'] +
                c[3] - '0' +
                sumdouble[c[4] - '0'] +
                c[5] - '0' +
                sumdouble[c[6] - '0'] +
                c[7] - '0' +
                sumdouble[c[8] - '0'] +
                c[9] - '0' +
                sumdouble[c[10] - '0'] +
                c[11] - '0' +
                sumdouble[c[12] - '0'] +
                c[13] - '0' +
                sumdouble[c[14] - '0'];
        return (10 - (sum % 10)) % 10;
    }


    public static String sha1(String s) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(s.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "-";
        }
    }
}
