package edu.umich.eac;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;

class CacheStats {
    // cache stats; access must be synchronized(this)
    
    // promotion delay is in milliseconds
    private final long promotionDelayInit = 5000;
    private SummaryStatistics promotionDelay = new SummaryStatistics();
    private int numDemandFetches = 0;
    private int numCacheHits = 0;
    private int numHintedPrefetches = 0;
    private int numCancelledFetches = 0;
    
    public CacheStats() {
        // fake value to start so that prefetches aren't 
        //  immediately considered not promoted 
        promotionDelay.addValue(promotionDelayInit);
    }
    
    synchronized <V> void onPrefetchHint(FetchFuture<V> fetchFuture) {
        numHintedPrefetches++;
    }
    
    synchronized <V> void onDemandFetch(FetchFuture<V> fetchFuture) {
        // prefetch->fetch delay
        long promotion_delay = fetchFuture.millisSinceCreated();
        promotionDelay.addValue(promotion_delay);
        
        // promotion rate
        numDemandFetches++;
        
        if (fetchFuture.isDone()) {
            numCacheHits++;
        }
    }
    
    synchronized <V> void onFetchCancelled(FetchFuture<V> fetchFuture) {
        numCancelledFetches++;
    }
    
    synchronized double getPromotionRate() {
        if (numHintedPrefetches == 0) {
            return 1.0;
        }
        return ((double) numDemandFetches) / ((double) numHintedPrefetches);
    }
    
    /**
     * @return The [avg, stdev] promotion delay of this cache.
     */
    synchronized double[] getPromotionDelay() {
        double avgDelay = promotionDelay.getMean();
        double stddevDelay = promotionDelay.getStandardDeviation(); 
        double ret[] = {avgDelay, stddevDelay};
        return ret;
    }

    public int numHints() {
        return numHintedPrefetches;
    }

    public int numCompletedFetches() {
        // TODO Auto-generated method stub
        return 0;
    }

    public int numDemandRequests() {
        return numDemandFetches;
    }

    public int numHits() {
        return numCacheHits;
    }

    public int numMisses() {
        return numDemandFetches - numCacheHits;
    }

    public double getHitRate() {
        return ((double) numCacheHits) / ((double) numDemandFetches);
    }
}
