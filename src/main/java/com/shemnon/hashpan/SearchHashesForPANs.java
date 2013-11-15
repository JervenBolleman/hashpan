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
public class SearchHashesForPANs {


    public static void main(String... arg) {

        // get the data
        List<String> pans = getPANs();
        List<String> hashesList = getHashes();
        Set<String> hashesSet = new HashSet<>(hashesList);


        // extract all of the 6 digit prefixes from the known PANs
        List<String> prefixes = pans.stream()
                .map(s -> s.substring(0, 6))
                .distinct()
                .collect(Collectors.toList());
        
        // For each prefix, 
        // * walk through all billion accounts
        //   * create a card number with a luhn check digit
        //   * hash it
        //   * check against the list of hashes
        //   * print out hits
        
        prefixes.parallelStream().forEach(prefix ->
                LongStream.range(0, 999999999)
                        .mapToObj(l -> createPAN(prefix, l))
                        .map(s -> new String[]{sha1(s), s})
                        .filter(s -> hashesSet.contains(s[0]))
                        .forEach(s -> System.out.println("card# - " + s[1] + " - hash " + s[0])));
    }

    private static String createPAN(String prefix, long l) {
        String postfix = "000000000" + l;
        String s = prefix + postfix.substring(postfix.length() - 9);
        return s + luhn16CheckDigit(s);
    }

    private static List<String> getPANs() {
        List<String> pans = new ArrayList<>(1024);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(SearchHashesForPANs.class.getResourceAsStream("/pans.txt")))) {
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

        try (BufferedReader br = new BufferedReader(new InputStreamReader(SearchHashesForPANs.class.getResourceAsStream("/hashes.txt")))) {
            String s;
            while ((s = br.readLine()) != null) {
                pans.add(s);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return pans;
    }

    static int[] doubles = {0, 2, 4, 6, 8, 1, 3, 5, 7, 9};

    /**
     * Lunh algorithm checksub digit on a 15 digit string
     */
    public static int luhn16CheckDigit(String s) {
        char[] c = s.toCharArray();
        int sum = doubles[c[0] - '0'] +
                c[1] - '0' +
                doubles[c[2] - '0'] +
                c[3] - '0' +
                doubles[c[4] - '0'] +
                c[5] - '0' +
                doubles[c[6] - '0'] +
                c[7] - '0' +
                doubles[c[8] - '0'] +
                c[9] - '0' +
                doubles[c[10] - '0'] +
                c[11] - '0' +
                doubles[c[12] - '0'] +
                c[13] - '0' +
                doubles[c[14] - '0'];
        return (10 - (sum % 10)) % 10;
    }


    public static String sha1(String s) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(s.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            return "-";
        }
    }
}
