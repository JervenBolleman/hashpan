package com.shemnon.hashpan;

import org.bouncycastle.crypto.digests.SHA1Digest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Created by shemnon on 13 Nov 2013.
 *
 */
public class SearchHashesForPANs {


    public static void main(String... arg) {

        Set<String> hashesSet =
                new BufferedReader(new InputStreamReader(SearchHashesForPANs.class.getResourceAsStream("/hashes.txt")))
                        .lines()
                        .collect(Collectors.toSet());
        
        // for each hacker PAN
        // * extract the IIN prefix
        // * Eliminate duplicates
        // * walk through on billion possible accounts
        //   * create a card number with a luhn check digit
        //   * hash it
        //   * check against the list of hashes
        //   * print out hits
        
        new BufferedReader(new BufferedReader(new InputStreamReader(SearchHashesForPANs.class.getResourceAsStream("/pans.txt")))).lines()
                .map(s -> s.substring(0, 6))
                .parallel() // to scale across all cores uncomment
                .distinct()
                .forEach(prefix -> LongStream.rangeClosed(0, 999999999)
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
        SHA1Digest sha1Digester = new SHA1Digest();
        byte[] card = s.getBytes();
        sha1Digester.update(card, 0, card.length);
        byte[] hash = new byte[20];  
        sha1Digester.doFinal(hash, 0);
        
        return Base64.getEncoder().encodeToString(hash);
    }
}
