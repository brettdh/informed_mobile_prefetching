package edu.umich.eac;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.CancellationException;
import java.util.Date;

import android.content.Context;
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
            stats.onPrefetchHint(fetchFuture);
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
    <V> Future<V> prefetchNow(CacheFetcher<V> fetcher) {
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
        if (demand) {
            stats.onUnhintedDemandFetch(fetchFuture);
        } else {
            // only to preserve the stats invariant of hint-before-fetch
            //  also, only called from prefetchNow, so only happens in tests.
            stats.onPrefetchHint(fetchFuture);
        }
        try {
            fetchFuture.startAsync(demand);
        } catch (CancellationException e) {
            Log.e(TAG, "No-defer prefetch cancelled before it was sent");
            fetchFuture = null;
        }
        return fetchFuture;
    }
    
    <V> Future<V> submit(Callable<V> fetcher, int demand_labels) {
        if ((demand_labels & IntNWLabels.BACKGROUND) != 0) {
            return bg_executor.submit(fetcher);
        } else {
            return fg_executor.submit(fetcher);
        }
    }
    
    CacheStats stats = new CacheStats();

    private long relGoalTimeEpochMillis;
    
    // Bound on the number of in-flight prefetches, similar to before.
    // Bounding this to only one thread for now, because unless I'm
    //  prefetching a lot of tiny things, the RTT that I save by
    //  pipelining prefetches won't really matter much.
    //  This also simplifies application code by not requiring
    //  it to do the pipelining and maintaining the order of
    //  prefetches.
    public static final int NUM_THREADS = 1;
    
    private static final String LOG_FILENAME = "/sdcard/intnw/prefetching.log";
    
    /* Should only call this one if the strategy ignores the params. */
    public EnergyAdaptiveCache(Context context, PrefetchStrategyType strategyType) {
        this(context, strategyType, System.currentTimeMillis(), 0, 0);
    }
    public EnergyAdaptiveCache(Context context,
                               PrefetchStrategyType strategyType,
                               long goalTimeEpochMillis,
                               int energyBudget,
                               int dataBudget) {
        Log.d(TAG, "Created a new EnergyAdaptiveCache");
        bg_executor = Executors.newFixedThreadPool(NUM_THREADS);
        fg_executor = Executors.newCachedThreadPool();

        logEvent("new-run", 0);
        
        long nowMillis = System.currentTimeMillis();
        if (goalTimeEpochMillis > nowMillis) {
            relGoalTimeEpochMillis = goalTimeEpochMillis - nowMillis;
        } else {
            relGoalTimeEpochMillis = 0;
        }
        Date goalTime = new Date(goalTimeEpochMillis);
        strategy = PrefetchStrategy.create(context, strategyType, goalTime, 
                                           energyBudget, dataBudget);
    }
    
    /** update the goal time of this cache to be
     *  as if the experiment had started startDelayedMillis ago.
     */
    public void updateGoalTime(int startDelayedMillis) {
        strategy.updateGoalTime(new Date(System.currentTimeMillis() + 
                                         relGoalTimeEpochMillis - 
                                         startDelayedMillis));
    }

    PrefetchStrategy strategy;
    
    private static PrintWriter logFileWriter;
    static {
        try {
            logFileWriter = new PrintWriter(new FileWriter(LOG_FILENAME, true), true);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create log file: " + e.getMessage());
        }
    }
    
    static void logEvent(String type) {
        logEvent(type, 0);
    }
    
    static void logEvent(String type, int fetchId) {
        if (logFileWriter != null) {
            long now = System.currentTimeMillis();
            logFileWriter.println(String.format("%d %s 0x%08x", now, type, fetchId));
        }
    }
}
