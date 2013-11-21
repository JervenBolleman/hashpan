TL;DR
=====

I entered just so I could play with the Java 8 Streams API in a real world 
context.  It was fun, and surprisingly it worked very well.

Execution Time
==============

I am doing a brute forces attack, as a preimage attack on SHA-1 is well beyond
the skills and access to computational resources of a typical working software 
engineer.

On a MacBook Air Mid 2012 execution is on the order of 2µs per credit card 
number, so my final run would be 44 to 45 hours on a single core, depending 
on how well my fan holds out, or 11 hours using all 4 functional units.
Running this on a commercial graphics card would be a walk in the park.

That is based on 73 IINs found in the hacker database, then searching all 
possible legitimate PANs for the IIN, which is one billion or 9 digits.

Methodology
===========

I am making a few assumptions, first is that the same program that created the 
hacker known numbers is also the same input program that generated the hashes.  
So all the credit cards I am looking for are 16 digits, with 6 digits for IIN 
and a Luhn check digit.  This is based on my 
[extensive research](http://en.wikipedia.org/wiki/Bank_card_number) on the 
subject and I admit I am missing Diners Club cards and American Express, 
although I wouldn't exactly say I'm missing them.

I am also assuming that all the IINs I can crack show up in the hacker list,
as looking at all possible numbers takes me into the 
[pentillion](http://www.unc.edu/~rowlett/units/large.html) size.  Even using 
some of the standards for numbering PANs brings it down to the tetrillions,
i.e. inaccessible to the commodity hardware I have access to.

Implementation
==============

I entered this contest for one reason and one reason only: to play with the 
Streams API in Java 8.  The core loop of the calculations looks surprisingly
readable:

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


Results
=======

1017 of the 1022 cards were found in 11 hrs 32 min 56.293 sec.  For an average 
of about 0.569 µs/hash amortized over all functional units, or 1.755 megahertz.  
At this rate each IIN takes about nine and a half minutes to explore.

The hashes not found are

    1OQUCrrAT48dL6ELahyaQ9qkN88=
    J6daTQI88gkWuxpTH4sSB6YUjBQ=
    O7E4G/nYZowrMZnjUFX6TcPGajY=
    i3m8VMVGT6TOwmH4QYZ7d1dDOA0=
    xGBfhxZwFrDxQp+R6AdhOUh4BC0=

I believe these hashes fall in to the following categories (most likely to least likely)

* Unsearched IINs
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
