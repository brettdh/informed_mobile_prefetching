# Informed Mobile Prefetching

Informed Mobile Prefetching is an Android library for making
prefetching decisions. It helps applications decide when to 
prefetch and how much data to prefetch, seeking to balance 
reduction in user-visible delays with the energy and cellular
data costs that prefetching incurs.

Here is a simple example of prefetching using IMP:

```java
CacheFetcher<String> fetcher = new CacheFetcher<String>() {
    public String call(int labels) throws Exception {
        // Do the fetching of this particular item;
        // e.g. execute an HTTP request
        // *labels* indicates whether we're doing a prefetch
        // or a demand fetch, allowing the fetcher to 
        // treat the request accordingly (or allow a lower 
        // layer, such as Intentional Networking, to do so).
        return item;
    }
}

// assume cache exists for the lifetime of the application
Future<String> future = cache.prefetch(fetcher);
```

This schedules a prefetch of a given data item (URL or other identifier 
and other details omitted above for brevity). As you can see, the application
provides an implementation of the *fetching* action, in whatever way it
sees fit. Notice, however, that the application need not make any effort
deciding *when* to prefetch or *which data* to prefetch (assuming there are
many items available to prefetch, such as in a newsreader application); 
IMP takes care of these decisions on the application's behalf.

Some time later, when the user selects the above item for viewing, the 
application can execute `future.get()` to retrieve the content. If IMP has 
already prefetched the data, it is simply returned. If not, a demand fetch
is immediately issued for the data item. Notice also that the application 
need not treat the prefetch and demand fetch cases differently; the same
fetch code is used for both.

This simple API allows IMP to gain insight into the effectiveness of its
prefetching. For example, if the user decides to delete or ignore a particular
data item without consuming it, the application can call `future.cancel()` to
communicate this occurrence to IMP. Over time, this allows IMP to build up 
a measure of the *accuracy* of the application's prefetch hints, which
allows it to compute a more accurate expectation of the benefit of prefetching.

Prefetching comes at a cost of potentially increased energy and cellular data
usage. IMP tracks the device's energy and data usage over time, estimates the
resource cost of each prefetch, and uses a cost/benefit analysis to decide
whether to prefetch. Additionally, since cellular network usage incurs a
*tail period* in which the radio remains in a high-energy state until idle
timeout, IMP considers whether *batching* prefetches can save energy.

For a *much* more detailed description, please see this 
[paper][imp-mobisys], presented at [MobiSys 2012][mobisys].

## Getting started

Prerequisites:
* [Intentional Networking][intnw] and its prerequisites
* [libpowertutor][libpowertutor]

Build

    # Get the sources
    $ git clone https://github.com/brettdh/libcmm
    $ git clone https://github.com/brettdh/configumerator
    $ git clone https://github.com/brettdh/mocktime
    $ git clone https://github.com/brettdh/libpowertutor
    $ git clone https://github.com/brettdh/informed_mobile_prefetching

    # Make all modules available for importing
    $ git clone https://gist.github.com/ab71855542fb42cc7dc0.git ./setup_ndk
    $ python ./setup_ndk/setup_ndk_modules.py .  # note trailing '.'; will
                                                 # search all subfolders
    # This will make more symlinks than is strictly necessary.
    # If you wish, you can remove the extras (those not in the above list).

    # Build NDK support lib
    $ cd informed_mobile_prefetching
    $ ndk-build 

You should then be able to import `informed_mobile_prefetching` in
your Android project in Eclipse. There's also a test project under `tests`
that you can "Run as Android Test Project" to see if everything works.

## Obvious caveat

This is all research-quality code at best, and the terms of the [CRAPL][crapl]
apply (though the actual license is BSD). If you find this useful or promising, 
please open issues and (even better) pull requests for ways to make it better.
It is currently optimized for running experiments, as opposed to production
use, as will become apparent with a cursory glance at the code.

That said, if you find this interesting, I hope you also find the code useful!

[imp-mobisys]: http://bretthiggins.me/papers/mobisys12.pdf
[mobisys]: http://www.sigmobile.org/mobisys/2012/
[intnw]: http://github.com/brettdh/libcmm
[libpowertutor]: http://github.com/brettdh/libpowertutor
[crapl]: http://matt.might.net/articles/crapl/CRAPL-LICENSE.txt
