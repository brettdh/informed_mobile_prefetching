package edu.umich.eac;

import edu.umich.eac.PrefetchStrategy;
import edu.umich.eac.FetchFuture;

class ConservativePrefetchStrategy extends PrefetchStrategy {
    public void onPrefetchEnqueued(FetchFuture<?> prefetch) {
        // For testing, we can also try the 
        //  most conservative strategy: never start prefetches.
    }
}
