TL;DR
=====

I entered just so I could play with the Java 8 Streams API in a real world 
context.  It was fun, and surprisingly it worked very well.

Execution Speed: 4.405 MegaHashes/s over 73 billion credit cards at just over
4 and a half hours on a Mid 2012 Macbook Air.  99.5% of cards recovered.  If 
the calculations were restricted to a single thread about 18 hours would be 
needed.

Optimizations: Parallel streams, recycled thread local SHA1 digester, modified 
bloom filter, removing unneeded transformations, for a total speed improvement
of 250% (40% of original execution time).

Execution scales simply across up to 73 cores. It will scale well (but not 
simply) across absurdly large numbers of cores with the change of one line of 
code.

Execution Time
==============

On a MacBook Air (Mid 2012, 1.8Ghz Core i5) 1017 of the 1022 cards were found 
in 4 hrs 36 mins 44.701 secs.  For an average of about 227 ns/hash amortized 
over all functional units, or 4.405 MHz.  That's over two hashes per clock 
cycle on a Commodore 64.  

If we are restricted to one core the execution time would be about 908 ns/hash 
per credit card number, so a single core run would take 18 to 19 hours. 

That is based on 73 IINs found in the hacker database, then searching all 
possible legitimate PANs for the IIN, which is one billion pans representing
9 digits of the PAN. At this rate each IIN takes about 3 and three quarter 
minutes  to explore (across all 4 cores).

Methodology
===========

I am doing a brute forces attack, as a preimage attack on SHA-1 is well beyond
the skills and access to computational resources of a typical working software 
engineer. Other than the Java 8 Streams API and a standard SHA-1 encryption 
algorithm no external libraries of note were used.

I briefly considered rainbow table attacks, but some features make this 
inefficient.  First, the search space is only partially covered.  This can be
mitigated with proper table configurations to obtain coverage that is by all 
practical measures complete, but that is as much (black) art as it is science.  
Second, the 16 byte search space is much larger than any of the pre-published 
tables available (which tend to top out at 12), and a numeric table is uncommon
at best.  So the generation of such a set of tables would need to be considered 
as part of the algorithmic complexity of my solution.  While keeping these 
tables around might be useful for a hacker ring their long term value seems 
questionable since this contest seems aimed to show to the PCI Security 
Standards Council that simply hashing a complete PAN is not secure enough 
unless other techniques are also applied such as salting and key lengthening.
([It should be noted](https://plus.google.com/+KevinOConnor7/posts/YdMxYnRRvpQ) 
that the IINs for this exercise are fictional and appear to reflect that some 
of them are more common than others.  Real conclusions should only be drawn 
from real data, however).

The advantage of an exhaustive keyspace search is that you can then be certain
that if a key isn't found that it does not exist in the searched space.  The 
task then is to reduce the key search space to something expressed in billions 
rather than quadrillions.

I am making a few assumptions, first is that the same program that created the 
hacker numbers list is also the same input program that generated the hashes.  
So all the credit cards I am looking for are 16 digits, with 6 digits for IIN 
and a Luhn check digit.  This is based on my 
[extensive research](http://en.wikipedia.org/wiki/Bank_card_number) on the 
subject and I admit I am missing Diners Club cards and American Express, 
although I wouldn't exactly say I'm missing them.  But they are not in the 
hacker list which is presented to be representative of the target data.

I am also assuming that all the IINs I can crack show up in the hacker list,
as looking at all possible numbers takes me into the quadrillion key size.  
Even using some of the standards for numbering PANs (4xxxxx and 
50xxxx-55xxxx card numbers) brings it down to the high trillions, which is 
still inaccessible to the commodity hardware I have access to.  This assumption 
was over 99.5% valid, as only 5 hashes were missed.  If those IINs were present 
in the seed data but rare enough (such as 0.01% frequency or less) then this 
assumption was entirely valid.  But because this is black box data I cannot be 
completely sure on this.

Implementation
==============

I entered this contest for one reason and one reason only: to play with the 
Streams API in Java 8.  The core loop of the calculations looks surprisingly
readable.  Here is an early unoptimized version of the core loop.

        bufferedReader.lines()
                .map(s -> s.substring(0, 6))
                .distinct()
                .forEach(prefix -> LongStream.rangeClosed(0, 999_999_999)
                        .mapToObj(l -> createPAN(prefix, l))
                        .map(s -> new String[]{sha1(s), s})
                        .filter(s -> hashesSet.contains(s[0]))
                        .forEach(s -> System.out.println("card# - " + s[1] + " - hash " + s[0])));

A narrative description matches up to the code quite nicely:

> * for each hacker PAN
>     * extract the IIN prefix
>     * Eliminate duplicates
>     * for all one billion possible accounts
>         * create a card number with a luhn check digit
>         * hash it
>         * check against the list of hashes
>         * print out hits

This is also interesting because it shows the incremental nature of the 
Java Streams API.  A naive implementation (read this as one I would slap
together for testing) would fully calculate each step of the stream.  I
would try to create a list with a billion strings in it, then hash them, 
then try to check against the existing hashes.  Which would work only if 
I had 64GB of RAM (or more!).

But as you run this you will see hits come out in semi-regular intervals
rather than as one big clump at the end. This shows that the Stream API is 
taking values one at a time and walking it down the stream at a sustainable 
pace instead of the whole wall of values hitting the stream all at one time.

And how do I parallelize this implementation?  Add a call to `.parallel()` 
after the distinct() method or the forEach method.  To parallelize across 
processes or multiple machines you can split the list if IINs across the 
separate processes... by hand.  If it was really valuable it could be 
automated via a gradle script.

Because of a quirk in the way LongStream is implemented, I saw better 
performance parallelizing on the IIN numbers than on the account numbers.
The current implementation does not do an even split of the values if the 
size of the stream is over 16 million (2^24).  The stream is split in a 1:3 
ratio instead of a 1:1 configuration resulting in one of the task queues having 
more work that the others which then results in cores becoming idle before 
moving on to the next sequential IIN.  It should also be noted that the default
1:1 split ration has a strong bias towards executing on functional units whose 
quantity is a power of two, and the absence of idle cores would only be seen 
along those boundaries.

One other tweak is that the hacker list of PANs is presumed to be representative
of the frequencies of the IINs found in the rest of the data set.  An after
the crack analysis shows it is close enough with some outliers.  In order to 
"front load" the generation of the hashes the IINs are considered in the 
frequency order of the hacker list of PANs, hence if the execution is aborted
earlier then we will likely have the most possible hashes.  

This presented some challenges with the Java Streams APIs since the parallel 
implementation will partition the ordered list into front and back partitions, 
and have the other worker threads start more or less in the middle.  So I 
stored the IINs in a blocking queue and pulled the "work ticket" just after the
parallel split in the stream.  This resulted in the desired effect of more
frequently used IINs being searched first.  Since the number of IINs is small 
compared to the number of card numbers searched, the overhead of pulling a 
ticket form a concurrent queue is irrelevant.

Optimization
============

The unoptimized code ran at 569 ns/hash.  After sticking VisualVM into the 
execution I discovered that there was a lot of time spent doing things other 
than hashing.  The two biggest culprits were time spent looking up the SHA1
hashing algorithm and translating bytes to and from Strings.

Looking up a digest algorithm via the proper Java APIs every time was very 
slow, so a fast hashing algorithm was instantiated directly (the 
bouncy castle implementation), saving nearly 18% off of the initial 
implementation (this will be revisited later).  

I then did two optimizations based on observations from JVisualVM sampling 
monitoring.  Generating the PANs in byte arrays and delaying the creation of 
the Base64 strings as late as possible also saved about 20%.  Then, adding a 
variation of a bloom filter on the hash bytes to delay the creation of a 
Base64 String and a subsequent HashMap lookup resulted in another 15% gain, 
for a total of 53% reduction at 253 ns/hash.

But one of the morals of optimization is to prove your assumptions.  I had
assumed that the use of ThreadLocal and the built in SHA-1 impl would be 
slower than BouncyCastle.  Apparently [I was 
wrong](http://bouncy-castle.1462172.n4.nabble.com/SHA1-speed-and-correctness-td4656567.html)
and the Sun impl is faster because of reasons internal to the JVM.  Coupling
this with a thread local digester (again, made easy by lambdas) made the 
execution time 227 ns/hash, adding yet another 7% off of the original time 
for a total reduction of 60%.  

At this point I inspected the execution via JVisualVM sampling and it appears 
that 95% of the execution time is spent in the SHA1 hashing code. Yes, 60% of 
the time from the original implementation was in essence wasted effort.  But it 
did keep my workspace warm, so it wasn't entirely wasted.

Results
=======

Not all of the hashes were decoded, only 99.5% of the hashes were found (1017 
of 1022).  The hashes not decoded (and their hex representations) were

    1OQUCrrAT48dL6ELahyaQ9qkN88=    D4E4140ABAC04F8F1D2FA10B6A1C9A43DAA437CF
    J6daTQI88gkWuxpTH4sSB6YUjBQ=    27A75A4D023CF20916BB1A531F8B1207A6148C14
    O7E4G/nYZowrMZnjUFX6TcPGajY=    3BB1381BF9D8668C2B3199E35055FA4DC3C66A36
    i3m8VMVGT6TOwmH4QYZ7d1dDOA0=    8B79BC54C5464FA4CEC261F841867B775743380D
    xGBfhxZwFrDxQp+R6AdhOUh4BC0=    C4605F87167016B0F1429F91E80761394878042D

I believe these hashes fall in to the following categories (most likely to 
least likely)

* Unsearched IINs (not found in hacker sample set)
* PANs with bad LUHN digits
* entirely bogus values, such as the hash of names of Skylanders characters.
 
As a subclass of unsearched IINs are those that are not 16 digits in length.  
And even then only the IINs in the hackers data set were searched.  If the IINs
of the missing hashes were made known, and they were 16 digit credit cards with 
valid LUHNs then they could be resolved in less than a half an hour (less than 
15 minutes if at least one IIN was shared).

It is conceivable I could run this against the whole known set of IINs, but 
that numbers into the tens of thousands, i.e. making the search space too 
large to be practical.  Compounding that problem is that nearly all the IINs 
in the hacker data set are predominately bogus when cross referenced with 
[publicly accessible 
data](http://en.wikipedia.org/wiki/List_of_Bank_Identification_Numbers), so
simply searching against all legitimate IINs would similarly be pointless.

However since over 99% of the hashes were recovered, this is more than enough
data for a hacker to cause problems with the recovered data.
