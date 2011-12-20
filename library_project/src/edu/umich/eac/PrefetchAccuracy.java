package edu.umich.eac;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class PrefetchAccuracy {
    private int prefetchClass;
    /**
     * Computes the accuracy of the prefetch hints so far.
     * @return accuracy in the range [0.0, 1.0].
     */
    public synchronized double getAccuracy() {
        /* The prefetch hint accuracy is the number of consumed hints
         * divided by the total number of hints.
         * e.g. if the hint consumption pattern is 0010010100,
         * where 1 is consumed and 0 is not, the accuracy is 3/10.
         * 
         * Note that the accuracy is zero to start.  This means that
         * we won't prefetch until after the first consumption.
         */
        
        // TODO: decay impact of older hints.
        
        int utilizedPrefetchHints = 0;
        for (int i = 0; i < prefetchHintsConsumed.size(); ++i) {
            boolean consumed = prefetchHintsConsumed.get(i);
            if (consumed) {
                utilizedPrefetchHints++;
            }
        }
        int hintedPrefetches = prefetchHintsConsumed.size();
        
        if (hintedPrefetches == 0) {
            return 0.0;
        }
        
        return ((double) utilizedPrefetchHints) / ((double) hintedPrefetches);
    }
    
    public enum Application {
        EMAIL, NEWS
    }
    
    public enum NewsreaderPrefetchClass {
        FEED0, FEED1, FEED2, FEED3, FEED4
    }
    
    public double getHardcodedAccuracy(Application app) {
        switch (app) {
        case EMAIL:
            return 0.8;  // reported accuracy of GMail Priority Inbox
        case NEWS:
            return newsreaderPrefetchAccuracyByFeed.get(prefetchClass);
            //return 0.64; // 16 out of 25 articles read
        }
        
        // NOTREACHED
        return 1.0;
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
        prefetchClass = 0;
    }
    
    public PrefetchAccuracy(int prefetchClass) {
        this();
        this.prefetchClass = prefetchClass;
    }
    
    private static Map<Integer, Double> newsreaderPrefetchAccuracyByFeed;
    static {
        newsreaderPrefetchAccuracyByFeed = new HashMap<Integer, Double>();
        newsreaderPrefetchAccuracyByFeed.put(NewsreaderPrefetchClass.FEED0.ordinal(), 8.0/9.0);
        newsreaderPrefetchAccuracyByFeed.put(NewsreaderPrefetchClass.FEED1.ordinal(), 2.0/8.0);
        newsreaderPrefetchAccuracyByFeed.put(NewsreaderPrefetchClass.FEED2.ordinal(), 3.0/3.0);
        newsreaderPrefetchAccuracyByFeed.put(NewsreaderPrefetchClass.FEED3.ordinal(), 1.0/2.0);
        newsreaderPrefetchAccuracyByFeed.put(NewsreaderPrefetchClass.FEED4.ordinal(), 2.0/3.0);
        newsreaderPrefetchAccuracyByFeed.put(CacheFetcher.DEFAULT_PREFETCH_CLASS, 16.0/25.0);
    }
    
    private ArrayList<Integer> prefetchHintHashes;
    private ArrayList<Boolean> prefetchHintsConsumed;
}
