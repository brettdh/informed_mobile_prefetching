package edu.umich.eac;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

class PrefetchAccuracy {
    /**
     * Computes the accuracy of a prefetch cache with one more prefetch in it.
     * @return accuracy in the range [0.0, 1.0].
     */
    public double getNextAccuracy() {
        final int nextDepth = prefetchHashes.size() + 1;
        if (nextDepth > accuracyByDepth.size()) {
            return 1.0;
        }
        
        int hintedPrefetches = emptyCacheHintedPrefetches;
        int utilizedPrefetchHints = 0;
        for (int i = 0; i < nextDepth; ++i) {
            final AccuracyAtDepth accuracy = accuracyByDepth.get(i);
            hintedPrefetches += accuracy.hintedPrefetches;
            utilizedPrefetchHints += accuracy.utilizedPrefetchHints;
        }
        return ((double) utilizedPrefetchHints) / ((double) hintedPrefetches);
    }
    
    /**
     * Call when the application hints a prefetch.
     * @param prefetch
     */
    public <V> void addPrefetchHint(FetchFuture<V> prefetch) {
        int depth = prefetchHashes.size() - 1;
        if (depth == -1) {
            emptyCacheHintedPrefetches++;
        } else {
            accuracyByDepth.get(depth).hintedPrefetches++;
        }
    }
    
    /**
     * Call when the prefetch strategy issues a prefetch.
     * @param prefetch
     */
    public <V> void addIssuedPrefetch(FetchFuture<V> prefetch) {
        // avoid keeping references to the actual object,
        //  as that would prevent it from being garbage-collected.
        prefetchHashes.add(prefetch.hashCode());
    }
    
    /**
     * Call when the application demand-fetches a data item
     * not first hinted as a prefetch.
     * @param fetch
     */
    public <V> void addUnhintedPrefetch(FetchFuture<V> fetch) {
        // cache miss; contributes to inaccuracy of prefetch hints
        // treat as a hint that will never be utilized
        addPrefetchHint(fetch);
    }
    
    /**
     * Call when the application demand-fetches a data item
     * that it has hinted before.
     * @param prefetch
     */
    public <V> void markDemandFetched(FetchFuture<V> prefetch) {
        int depth = prefetchHashes.indexOf(prefetch.hashCode());
        if (depth >= 0 && demandFetchedHints.contains(prefetch.hashCode())) {
            accuracyByDepth.get(depth).utilizedPrefetchHints++;
            demandFetchedHints.add(prefetch.hashCode());
        } else {
            // weird. I shouldn't see the demand fetch if the prefetch doesn't exist
            // or was already removed.
        }
    }
    
    /** 
     * Call when a prefetch is cancelled.
     * This can happen explicitly via the cancel method or 
     * implicitly via garbage collection (which just calls cancel).
     * @param prefetch
     */
    public <V> void removePrefetch(FetchFuture<V> prefetch) {
        prefetchHashes.remove(Integer.valueOf(prefetch.hashCode()));
        demandFetchedHints.remove(Integer.valueOf(prefetch.hashCode()));
    }
    
    public PrefetchAccuracy() {
        accuracyByDepth = new ArrayList<AccuracyAtDepth>();
        prefetchHashes = new ArrayList<Integer>();
        demandFetchedHints = new HashSet<Integer>();
    }
    
    private class AccuracyAtDepth {
        int utilizedPrefetchHints;
        int hintedPrefetches;
    }
    private ArrayList<AccuracyAtDepth> accuracyByDepth;
    private ArrayList<Integer> prefetchHashes;
    private Set<Integer> demandFetchedHints;
    
    private int emptyCacheHintedPrefetches = 0;
}
