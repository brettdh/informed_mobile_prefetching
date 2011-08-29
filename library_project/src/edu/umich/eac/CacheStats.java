package edu.umich.eac;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;

import android.util.Log;

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
    
    synchronized <V> void onUnhintedDemandFetch(FetchFuture<V> fetchFuture) {
        // an unhinted demand fetch decreases the overall accuracy of prefetch hints.
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
    
    synchronized double getPrefetchAccuracy() {
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

    synchronized int numHints() {
        return numHintedPrefetches;
    }

    synchronized int numCompletedFetches() {
        // TODO Auto-generated method stub
        return 0;
    }

    synchronized int numDemandRequests() {
        return numDemandFetches;
    }

    synchronized int numHits() {
        return numCacheHits;
    }

    synchronized int numMisses() {
        return numDemandFetches - numCacheHits;
    }

    synchronized double getHitRate() {
        return ((double) numCacheHits) / ((double) numDemandFetches);
    }
    
    private class LogOutputStream extends OutputStream {
        // Implementation borrowed from:
        //   http://tech.chitgoks.com/2008/03/17/android-showing-systemout-messages-to-console/
        private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private String name;
        
        public LogOutputStream(String name)   {
          this.name = name;
        }
        
        @Override
        public void write(int b) throws IOException {
            if (((byte) b) == '\n') {
                String s = new String(buffer.toByteArray());
                Log.v(name, s);
                buffer.reset();
            } else {
                buffer.write(b);
            }
        }
    }
    private LogOutputStream logStream = new LogOutputStream("CacheStats");
    
    synchronized void printCacheStats() {
        printCacheStatsToFile(logStream);
    }
    
    synchronized void printCacheStatsToFile(OutputStream out) {
        PrintWriter writer = new PrintWriter(out, true);
        writer.println("Cache stats:");
        writer.format("  Items hinted: %d\n", numHints());
        writer.format("  Items fetched: %d\n", numCompletedFetches());
        writer.format("  Demand requests: %d\n", numDemandRequests());
        writer.format("  Cache hits: %d\n", numHits());
        writer.format("  Cache misses: %d\n", numMisses());
        writer.format("  Hit rate: %.02f %%\n", getHitRate() * 100.0);
    }
}
