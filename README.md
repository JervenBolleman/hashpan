TL;DR
=====

I entered just so I could play with the Java 8 Streams API in a real world 
context.  It was fun, and surprisingly it worked very well.

Execution Time
==============

On a MacBook Air (Mid 2012, 1.8Ghz Core i5) 1017 of the 1022 cards were found 
in 5 hrs 7 mins 55.396.  For an average of about 253 ns/hash amortized 
over all functional units, or 3.953 MHz.  That's two hashes per clock cycle 
on a Commodore 64.  

If we are restricted to one core the execution time would be about 1.012 µs 
per credit card number, so a single core run would take 21 to 22 hours. 

That is based on 73 IINs found in the hacker database, then searching all 
possible legitimate PANs for the IIN, which is one billion pans representing
9 digits of the PAN. At this rate each IIN takes about 4 and a half minutes 
to explore (across all 4 cores).

Methodology
===========

I am doing a brute forces attack, as a preimage attack on SHA-1 is well beyond
the skills and access to computational resources of a typical working software 
engineer.

I am making a few assumptions, first is that the same program that created the 
hacker known numbers is also the same input program that generated the hashes.  
So all the credit cards I am looking for are 16 digits, with 6 digits for IIN 
and a Luhn check digit.  This is based on my 
[extensive research](http://en.wikipedia.org/wiki/Bank_card_number) on the 
subject and I admit I am missing Diners Club cards and American Express, 
although I wouldn't exactly say I'm missing them.  But they are not in the 
hacker list which is presented to be representative of the target data.


I am also assuming that all the IINs I can crack show up in the hacker list,
as looking at all possible numbers takes me into the 
[pentillion](http://www.unc.edu/~rowlett/units/large.html) size.  Even using 
some of the standards for numbering PANs brings it down to the tetrillions,
i.e. inaccessible to the commodity hardware I have access to.  This was true
for all but 5 of the hashes (more on that later).

Implementation
==============

I entered this contest for one reason and one reason only: to play with the 
Streams API in Java 8.  The core loop of the calculations looks surprisingly
readable.  Here is an early unoptimized version of the core loop.

        bufferedReader.lines()
                .map(s -> s.substring(0, 6))
                .distinct()
                .forEach(prefix -> LongStream.rangeClosed(0, 999999999)
                        .mapToObj(l -> createPAN(prefix, l))
                        .map(s -> new String[]{sha1(s), s})
                        .filter(s -> hashesSet.contains(s[0]))
                        .forEach(s -> System.out.println("card# - " + s[1] + " - hash " + s[0])));

A narrative description matches up to the code quite nicely:

> * for each hacker PAN
>     * extract the IIN prefix
>     * Eliminate duplicates
>     * walk through on billion possible accounts
>         * create a card number with a luhn check digit
>         * hash it
>         * check against the list of hashes
>         * print out hits


Another interesting fact is that this shows the incremental nature of the 
Java Streams API.  A naive implementation (read this as one I would slap
together for testing) would fully calculate each step of the stream.  I
would try to create a list with a billion strings in it, then hash them, 
then try to check against the existing hashes.  Which would work only if 
I had 64GB of RAM (or more!).

However, as you run this you will see hits come out in semi-regular intervals.
This shows that the Stream API is taking a value and walking it down the stream
at a sustainable pace instead of the whole wall of values hitting the stream
at one time.

And how do I parallelize this implementation?  Add a call to `.parallel()` 
right after the `forEach` line at the beginning of the long stream.  Java will
automatically create an appropriate amount of threads needed to stream across
the available cores.  Note however, that it would stop splitting the stream
after one billion cores since that is the total count of the elements. ;)
To parallelize across processes or multiple machines you can split the 
list if IINs across the separate processes... by hand.  If it was really
valuable it could be automated via a gradle script.

Because of a quirk in the way LongStream is implemented, I saw better 
performance parallelizing on the IIN numbers than on the account numbers.
if the size of the stream is over 16 million (2^24) then it is split in a 1:3
configuration instead of a 1:1 configuration, resulting in one of the task 
queues having more work that the others, resulting in idle cores before moving
on to the next sequential IIN.

One other tweak is that the hacker list of PANs is presumed to be representative
of the frequencies of the IINs found in the rest of the data set.  An after
the crack analysis shows it is close enough with some outliers.  In order to 
"front load" the generation of the hashes the IINs are considered in the 
frequency order of the hacker list of PANs, hence if the execution is aborted
earlier then we will likely have the most possible hashes.  

This presented some  challenges with the Java Streams APIs since the parallel 
implementation will partition the ordered list into front and back partitions, 
and have the other worker threads start more or less in the middle.  So I 
stored the IINs in a blocking queue and pulled the "work ticket" just after the
parallel split in the stream.  This resulted in the desired effect of more
frequently used IINs being searched first.

Optimization
============
The unoptimized code ran at 569 ns/hash, however sticking VisualVM into the 
execution and discovered that there was a lot of time not spent hashing.  The 
two biggest culprits were time spent looking up the SHA1 hashing object and
translating bytes to and from Strings.

To fix the lookup a fast hashing algorithm was instantiated directly, saving
nearly 18% off of the initial implementation.  Generating the PANs in byte 
arrays and delaying the creation of the Base64 strings as late as possible also
saved about 20%.  Based on a quick visual inspection of the VisualVM stack 
traces we are out of big hits for optimization.  Also, doing a pre-check on the 
hash via verifying that paired bytes show up in the hash in the proper order
resulted in another 15% gain, for a total of 57% reduction at 253 ns/hash.


Results
=======

The hashes not found are

    1OQUCrrAT48dL6ELahyaQ9qkN88=
    J6daTQI88gkWuxpTH4sSB6YUjBQ=
    O7E4G/nYZowrMZnjUFX6TcPGajY=
    i3m8VMVGT6TOwmH4QYZ7d1dDOA0=
    xGBfhxZwFrDxQp+R6AdhOUh4BC0=

I believe these hashes fall in to the following categories (most likely to 
least likely)

* Unsearched IINs (not found in hacker sample set)
* PANs with bad LUHN digits
* entirely bogus values, such as the hash of the script of Star Wars.
 
As a subclass of unsearched IINs are those that are not 16 digits in length.  
And even then only the IINs in the hackers data set were searched.  

It is conceivable I could run this against the whole known set of IINs, but 
that numbers into the tens of thousands, i.e. making the search space too 
large to be practical.  Compounding that problem is that nearly all the IINs 
in the hacker data set are predominately bogus when cross referenced with 
[publicly accessible 
data](http://en.wikipedia.org/wiki/List_of_Bank_Identification_Numbers}, so
simply searching against all legitimate IINs would similarly be pointless.
