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

    static Set<String> hashesSet;
    // an array of booleans indexed by byte position and word
    static boolean[][] quickCheck = new boolean[19][65536];

    public static void main(String... arg) {

        prepareHashes();

        // for each hacker PAN
        // * extract the IIN prefix
        // * Eliminate duplicates
        // * convert the IIN to bytes for performance
        // * walk through on billion possible accounts
        //   * create a card number with a luhn check digit
        //   * hash it
        //   * check against the list of hashes
        //   * print out hits

        new BufferedReader(new BufferedReader(new InputStreamReader(System.in))).lines()
                .map(s -> s.substring(0, 6))
                .distinct()
                .map(s -> s.getBytes())
                .parallel() // to scale across up to 73 cores cores uncomment
                .forEach(prefix -> LongStream.rangeClosed(0, 999_999_999)
                        //.parallel() // to scale across all cores cores uncomment
                        .mapToObj(l -> createPAN(prefix, l))
                        .map(s -> new byte[][]{sha1(s), s})
                        .filter(SearchHashesForPANs::matchesHash)
                        .forEach(r -> System.out.println(Base64.getEncoder().encodeToString(r[0]) + " - " + new String(r[1]))));
        
        System.out.println("Undecyphered hashes:");
        hashesSet.stream().forEach(System.out::println);
    }

    private static void prepareHashes() {
        hashesSet = new BufferedReader(new InputStreamReader(SearchHashesForPANs.class.getResourceAsStream("/hashes.txt")))
                .lines()
                .collect(Collectors.toSet());
        // create the paird bytes array
        for (String s : hashesSet) {
            byte[] hash = Base64.getDecoder().decode(s);
            for (int i = 0; i < 19; i++) {
                quickCheck[i][(hash[i] << 8 | hash[i + 1]) & 0xffff] = true;
            }
        }
    }


    private static boolean matchesHash(byte[][] s) {
        byte[] hash = s[0];
        // first make sure it has paired bytes in the correct place
        for (int i = 0; i < 19; i++) {
            if (!quickCheck[i][(hash[i] << 8 | hash[i + 1]) & 0xffff]) {
                return false;
            }
        }
        // now we can do the string lookup
        return hashesSet.remove(Base64.getEncoder().encodeToString(hash));
    }

    private static byte[] createPAN(byte[] prefix, long l) {
        byte[] result = new byte[]{'0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0'};
        
        // copy IIN
        System.arraycopy(prefix, 0, result, 0, 6);
        
        // fill in account # from right to left
        int pos = 14;
        while (l > 0) {
            result[pos--] = (byte) ((byte) (l % 10) + ((byte) '0'));
            l /= 10;
        }

        // now add the luhn digit for this card
        result[15] = (byte) (luhn16CheckDigit(result) + '0');
        
        return result;
    }

    static int[] doubles = {0, 2, 4, 6, 8, 1, 3, 5, 7, 9};

    /**
     * Lunh algorithm.  Returns the checksum digit on a 15 digit string
     */
    public static int luhn16CheckDigit(byte[] c) {
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


    public static byte[] sha1(byte[] card) {
        SHA1Digest sha1Digester = new SHA1Digest();
        sha1Digester.update(card, 0, card.length);
        byte[] hash = new byte[20];
        sha1Digester.doFinal(hash, 0);

        return hash;
    }
}
