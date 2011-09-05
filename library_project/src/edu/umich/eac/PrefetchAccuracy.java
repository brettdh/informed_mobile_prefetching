package edu.umich.eac;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

class PrefetchAccuracy {
    /**
     * Computes the accuracy of a prefetch cache with one more prefetch in it.
     * @return accuracy in the range [0.0, 1.0].
     */
    public synchronized double getNextAccuracy() {
        final int nextDepth = issuedPrefetchHashes.size();
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
    public synchronized <V> void addPrefetchHint(FetchFuture<V> prefetch) {
        hintedPrefetches.add(prefetch.hashCode());
        
        int depth = issuedPrefetchHashes.size();
        
        // valid assertion because addPrefetchHint MUST precede addIssuedPrefetch
        //  and accuracyByDepth never shrinks; it only grows.
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
    public synchronized <V> void addIssuedPrefetch(FetchFuture<V> prefetch) {
        assert(hintedPrefetches.contains(prefetch.hashCode()));
        
        // avoid keeping references to the actual object,
        //  as that would prevent it from being garbage-collected.
        issuedPrefetchHashes.add(prefetch.hashCode());
        
        // valid assertion because addPrefetchHint MUST precede addIssuedPrefetch
        assert(issuedPrefetchHashes.size() <= accuracyByDepth.size());
        if (issuedPrefetchHashes.size() == accuracyByDepth.size()) {
            accuracyByDepth.add(new AccuracyAtDepth());
        }
    }
    
    /**
     * Call when the application demand-fetches a data item
     * not first hinted as a prefetch.
     * @param fetch
     */
    public synchronized <V> void addUnhintedPrefetch(FetchFuture<V> fetch) {
        // cache miss; contributes to inaccuracy of prefetch hints
        // treat as a hint that was already issued (but not recorded) and will never be utilized
        if (issuedPrefetchHashes.size() == accuracyByDepth.size()) {
            accuracyByDepth.add(new AccuracyAtDepth());
        }
        hintedPrefetches.add(fetch.hashCode());
        addIssuedPrefetch(fetch);
    }
    
    /**
     * Call when the application demand-fetches a data item
     * that it has hinted before.
     * @param prefetch
     */
    public synchronized <V> void markDemandFetched(FetchFuture<V> prefetch) {
        final int prefetchId = prefetch.hashCode();
        int depth = issuedPrefetchHashes.indexOf(prefetchId);
        if (depth >= 0 && !demandFetchedHints.contains(prefetchId)) {
            assert(depth < accuracyByDepth.size());
            accuracyByDepth.get(depth).utilizedPrefetchHints++; //TODO: fix ArrayIndexOutOfBoundsException
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
    public synchronized <V> void removePrefetch(FetchFuture<V> prefetch) {
        issuedPrefetchHashes.remove(Integer.valueOf(prefetch.hashCode()));
        demandFetchedHints.remove(Integer.valueOf(prefetch.hashCode()));
    }
    
    public PrefetchAccuracy() {
        accuracyByDepth = new ArrayList<AccuracyAtDepth>();
        issuedPrefetchHashes = new ArrayList<Integer>();
        hintedPrefetches = new HashSet<Integer>();
        demandFetchedHints = new HashSet<Integer>();
    }
    
    private class AccuracyAtDepth {
        int utilizedPrefetchHints;
        int hintedPrefetches;
    }
    private ArrayList<AccuracyAtDepth> accuracyByDepth;
    private ArrayList<Integer> issuedPrefetchHashes;
    private Set<Integer> hintedPrefetches;
    private Set<Integer> demandFetchedHints;
}
