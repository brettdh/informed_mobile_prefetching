package edu.umich.eac;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;

import android.util.Log;

import edu.umich.eac.FetchFuture;
import edu.umich.eac.CacheFetcher;

public class EnergyAdaptiveCache {
    private static String TAG = EnergyAdaptiveCache.class.getName();
    
    private ExecutorService bg_executor;
    private ExecutorService fg_executor;
    
    /** Hints a future access and gets a Future for that access.
     *
     *  TODO: figure out how app could pass ordering constraints, 
     *        relative priority of prefetches
     *
     *  @param fetcher Application-specific logic to fetch a data item
     *                 and return an object representing it.
     *  @return A Future that can be used later to actually carry out
     *          the hinted access, via get().
     */
    public <V> Future<V> prefetch(CacheFetcher<V> fetcher) {
        try {
            FetchFuture<V> fetchFuture = new FetchFuture<V>(fetcher, this);
            strategy.onPrefetchEnqueued(fetchFuture);
            
            return fetchFuture;
        } catch (CancellationException e) {
            // shouldn't happen; the app can't cancel the prefetch 
            //  yet because it doesn't have a Future.
            Log.e(TAG, "Prefetch cancelled before it was sent");
            assert false;
            return null;
        }
    }
    
    /** Hint a future access and start prefetching it immediately,
     *  bypassing any deferral decision.
     *
     *  This is useful for testing.
     */
    public <V> Future<V> prefetchNow(CacheFetcher<V> fetcher) {
        return fetchNow(fetcher, false);
    }
    
    /** "Hint" an immediate access and start fetching it immediately.
     *
     *  This is useful for demand fetches that weren't hinted in advance.
     */
    public <V> Future<V> fetch(CacheFetcher<V> fetcher) {
        return fetchNow(fetcher, true);
    }
    
    private <V> Future<V> fetchNow(CacheFetcher<V> fetcher, boolean demand) {
        FetchFuture<V> fetchFuture = new FetchFuture<V>(fetcher, this);
        try {
            fetchFuture.startAsync(demand);
        } catch (CancellationException e) {
            Log.e(TAG, "No-defer prefetch cancelled before it was sent");
            fetchFuture = null;
        }
        return fetchFuture;
    }
    
    <V> Future<V> submit(Callable<V> fetcher, int demand_labels) {
        // TODO: record whatever necessary information
        // TODO: make a method to do that, and call it from FetchFuture
        if ((demand_labels & IntNWLabels.BACKGROUND) != 0) {
            return bg_executor.submit(fetcher);
        } else {
            return fg_executor.submit(fetcher);
        }
    }
    
    // cache stats; access must be synchronized(this)
    
    // promotion delay is in milliseconds
    private final long mPromotionDelayInit = 5000;
    private SummaryStatistics mPromotionDelay = new SummaryStatistics();
    private int mNumPromotions = 0;
    private int mNumPrefetches = 0;
    private double mPromotionRateEWMA = 1.0;
    private final double alpha = 0.9;
    
    <V> void updateStats(FetchFuture<V> fetchFuture,
                         boolean wasCancelled) {
        synchronized(this) {
            if (!wasCancelled) {
                // prefetch->fetch delay
                long promotion_delay = fetchFuture.millisSinceCreated();
                mPromotionDelay.addValue(promotion_delay);

                // promotion rate
                mNumPromotions++;
            }
            mNumPrefetches++;
            updatePromotionRate(mNumPromotions / mNumPrefetches);
        }
    }
    
    private synchronized void updatePromotionRate(double sample) {
        mPromotionRateEWMA = 
            (alpha * mPromotionRateEWMA) + ((1 - alpha) * sample);
    }
    
    synchronized double getPromotionRate() {
        return mPromotionRateEWMA;
    }
    
    /**
     * @return The [avg, stdev] promotion delay of this cache, or null if 
     *         there haven't been any promotions yet.
     */
    synchronized double[] getPromotionDelay() {
        if (mPromotionDelay.getN() == 0) {
            return null;
        }
        double avgDelay = mPromotionDelay.getMean();
        double stddevDelay = mPromotionDelay.getStandardDeviation(); 
        double ret[] = {avgDelay, stddevDelay};
        return ret;
    }
    
    // Bound on the number of in-flight prefetches, similar to before.
    // XXX: does this need to be configurable?
    public static final int NUM_THREADS = 10;
    
    /* Should only call this one if the strategy ignores the params. */
    public EnergyAdaptiveCache(PrefetchStrategyType strategyType) {
        this(strategyType, new Date(), 0, 0);
    }
    public EnergyAdaptiveCache(PrefetchStrategyType strategyType,
                               Date goalTime,
                               int energyBudget,
                               int dataBudget) {
        Log.d(TAG, "Created a new EnergyAdaptiveCache");
        bg_executor = Executors.newFixedThreadPool(NUM_THREADS);
        fg_executor = Executors.newCachedThreadPool();

        // fake value to start so that prefetches aren't 
        //  immediately considered not promoted 
        mPromotionDelay.addValue(mPromotionDelayInit);
        
        strategy = PrefetchStrategy.create(strategyType, goalTime, 
                                           energyBudget, dataBudget);
    }
    
    PrefetchStrategy strategy;
}
