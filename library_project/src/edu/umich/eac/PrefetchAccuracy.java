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
        final int nextDepth = prefetchHashes.size();
        if (nextDepth >= accuracyByDepth.size()) {
            // XXX: adjust this.
            return 1.0;
        }
        
        int hintedPrefetches = 0;
        int utilizedPrefetchHints = 0;
        for (int i = 0; i <= nextDepth; ++i) {
            final AccuracyAtDepth accuracy = accuracyByDepth.get(i);
            hintedPrefetches += accuracy.hintedPrefetches;
            utilizedPrefetchHints += accuracy.utilizedPrefetchHints;
        }
        if (utilizedPrefetchHints == 0) {
            // TODO: better 'smoothing.'
            return 0.25;
        }
        return ((double) utilizedPrefetchHints) / ((double) hintedPrefetches);
    }
    
    /**
     * Call when the application hints a prefetch.
     * @param prefetch
     */
    public <V> void addPrefetchHint(FetchFuture<V> prefetch) {
        int depth = prefetchHashes.size();
        
        // valid assertion because addPrefetchHint MUST precede addIssuedPrefetch
        //  and accuracyByDepth never shrinks; it only grows.
        // TODO: check for this ordering explicitly?
        assert(depth <= accuracyByDepth.size());
        
        if (depth == accuracyByDepth.size()) {
            accuracyByDepth.add(new AccuracyAtDepth());
        }
        accuracyByDepth.get(depth).hintedPrefetches++;
    }
    
    /**
     * Call when the prefetch strategy issues a prefetch.
     * @param prefetch
     */
    public <V> void addIssuedPrefetch(FetchFuture<V> prefetch) {
        // avoid keeping references to the actual object,
        //  as that would prevent it from being garbage-collected.
        prefetchHashes.add(prefetch.hashCode());
        
        // valid assertion because addPrefetchHint MUST precede addIssuedPrefetch
        assert(prefetchHashes.size() <= accuracyByDepth.size());
    }
    
    /**
     * Call when the application demand-fetches a data item
     * not first hinted as a prefetch.
     * @param fetch
     */
    public <V> void addUnhintedPrefetch(FetchFuture<V> fetch) {
        // cache miss; contributes to inaccuracy of prefetch hints
        // treat as a hint that was already issued (but not recorded) and will never be utilized
        if (prefetchHashes.size() == accuracyByDepth.size()) {
            accuracyByDepth.add(new AccuracyAtDepth());
        }
        addIssuedPrefetch(fetch);
    }
    
    /**
     * Call when the application demand-fetches a data item
     * that it has hinted before.
     * @param prefetch
     */
    public <V> void markDemandFetched(FetchFuture<V> prefetch) {
        final int prefetchId = prefetch.hashCode();
        int depth = prefetchHashes.indexOf(prefetchId);
        if (depth >= 0 && !demandFetchedHints.contains(prefetchId)) {
            accuracyByDepth.get(depth).utilizedPrefetchHints++;
            demandFetchedHints.add(prefetchId);
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
}
