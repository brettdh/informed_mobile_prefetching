package edu.umich.eac;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

class PrefetchAccuracy {
    /**
     * Computes the accuracy of the prefetch hints so far.
     * @return accuracy in the range [0.0, 1.0].
     */
    public synchronized double getAccuracy() {
        /* The prefetch hint accuracy is the number of consumed hints
         * divided by the total number of hints.  We only consider
         * the number of total hints up until the last consumed hint;
         * e.g. if the hint consumption pattern is 0010010100,
         * where 1 is consumed and 0 is not, the accuracy is 3/8
         * rather than 3/10.  This avoids penalizing applications
         * that hint far on into the future.
         * 
         * Note that the accuracy is zero to start.  This means that
         * we won't prefetch until after the first consumption.
         */
        int utilizedPrefetchHints = 0;
        int hintedPrefetches = 0;
        for (int i = 0; i < prefetchHintsConsumed.size(); ++i) {
            boolean consumed = prefetchHintsConsumed.get(i);
            if (consumed) {
                hintedPrefetches = i + 1;
                utilizedPrefetchHints++;
            }
        }
        // To disable Brian's patch to the simple calculation, uncomment this line.  
        hintedPrefetches = prefetchHintsConsumed.size();
        
        if (hintedPrefetches == 0) {
            return 0.0;
        }
        
        return ((double) utilizedPrefetchHints) / ((double) hintedPrefetches);
    }
    
    /**
     * Call when the application hints a prefetch.
     * @param prefetch
     */
    public synchronized <V> void addPrefetchHint(FetchFuture<V> prefetch) {
        prefetchHintHashes.add(prefetch.hashCode());
        prefetchHintsConsumed.add(false);
    }
    
    /**
     * Call when the prefetch strategy issues a prefetch.
     * @param prefetch
     */
    public synchronized <V> void addIssuedPrefetch(FetchFuture<V> prefetch) {
        // ignore
    }
    
    /**
     * Call when the application demand-fetches a data item
     * not first hinted as a prefetch.
     * @param fetch
     */
    public synchronized <V> void addUnhintedPrefetch(FetchFuture<V> fetch) {
        // ignore
    }
    
    /**
     * Call when the application demand-fetches a data item
     * that it has hinted before.
     * @param prefetch
     */
    public synchronized <V> void markDemandFetched(FetchFuture<V> prefetch) {
        final int prefetchId = prefetch.hashCode();
        int depth = prefetchHintHashes.indexOf(prefetchId);
        if (depth >= 0) {
            assert(depth < prefetchHintsConsumed.size());
            prefetchHintsConsumed.set(depth, true);
        }
    }
    
    /** 
     * Call when a prefetch is cancelled.
     * This can happen explicitly via the cancel method or 
     * implicitly via garbage collection (which just calls cancel).
     * @param prefetch
     */
    public synchronized <V> void removePrefetch(FetchFuture<V> prefetch) {
        int index = prefetchHintHashes.indexOf(prefetch.hashCode());
        assert(index != -1);
        prefetchHintHashes.remove(index);
        prefetchHintsConsumed.remove(index);
    }
    
    public PrefetchAccuracy() {
        prefetchHintHashes = new ArrayList<Integer>();
        prefetchHintsConsumed = new ArrayList<Boolean>();
    }
    
    private ArrayList<Integer> prefetchHintHashes;
    private ArrayList<Boolean> prefetchHintsConsumed;
}
