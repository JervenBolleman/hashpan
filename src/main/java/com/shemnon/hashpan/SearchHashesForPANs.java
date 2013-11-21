package com.shemnon.hashpan;

import org.bouncycastle.crypto.digests.SHA1Digest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
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

        for (int n : new int[] {10_000, 10_000, 10_000, 10_000, 100_000, 1_000_000, 100_000, 10_000, 10_000, 10_000, 10_000}) {

            long start = System.currentTimeMillis();
            new BufferedReader(new BufferedReader(new InputStreamReader(SearchHashesForPANs.class.getResourceAsStream("/pans.txt")))).lines()
                    .map(s -> s.substring(0, 6))
                    .distinct()
                    .map(s -> s.getBytes())
                    .parallel() // to scale across all cores uncomment
                    .forEach(prefix -> LongStream.rangeClosed(0, n)
                            .mapToObj(l -> createPAN(prefix, l))
                            .map(s -> new Object[]{sha1(s), s})
                            .filter(s -> hashesSet.contains(s[0]))
                            .forEach(r -> System.out.println("card# - " + new String((byte[])r[1]) + " - hash " + r[0])));
            long stop = System.currentTimeMillis();
            Duration d = Duration.ofMillis((stop - start) * 1_000_000_000 / n);
            double perHashMicroSec = ((stop - start) * 1_000_000.0 / n / 73);
            System.out.println(n + " @ " + perHashMicroSec +  "µs/hash - estimated run " + d.toString());
        }
    }
        

    private static byte[] createPAN(byte[] prefix, long l) {
        byte[] result = new byte[] {'0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0'};
        System.arraycopy(prefix, 0, result, 0, 6);
        int pos = 14;
        while (l > 0) {
            result[pos--] = (byte) ((byte) (l % 10) + ((byte)'0'));
            l /= 10;
        }
        
        result[15] = (byte) (luhn16CheckDigit(result) + '0');
        return result;
    }

    static int[] doubles = {0, 2, 4, 6, 8, 1, 3, 5, 7, 9};

    /**
     * Lunh algorithm checksub digit on a 15 digit string
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


    public static String sha1(byte[] card) {
        SHA1Digest sha1Digester = new SHA1Digest();
        sha1Digester.update(card, 0, card.length);
        byte[] hash = new byte[20];  
        sha1Digester.doFinal(hash, 0);
        
        return Base64.getEncoder().encodeToString(hash);
    }
}
