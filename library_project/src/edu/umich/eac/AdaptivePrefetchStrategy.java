package edu.umich.eac;

import edu.umich.eac.PrefetchStrategy;
import edu.umich.eac.FetchFuture;

class AdaptivePrefetchStrategy extends PrefetchStrategy {
    public void handlePrefetch(FetchFuture<?> prefetch) {
        /* TODO: IMPL
         * 
         * Possible actions to do with a prefetch:
         * a) Issue it now
         * b) Re-schedule this decision X seconds from now
         * 
         * How to pick between these:
         * 
         * if conditions are good now:
         *   
         * else:
         * 
         * 1) Conditions are good: 
         *      issue it now
         * 2) Conditions are bad but might improve soon:
         *      schedule it for a specific time in the future
         * 3) Conditions are bad and probably won't improve soon:
         *      
         * 
         */
    }
}
