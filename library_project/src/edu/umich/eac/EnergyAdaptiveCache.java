package edu.umich.eac;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import android.util.Log;

import edu.umich.eac.FetchFuture;
import edu.umich.eac.CacheFetcher;

public class EnergyAdaptiveCache {
    private String TAG = EnergyAdaptiveCache.class.getName();
    
    private ExecutorService executor;
    
    // contains prefetches yet to be sent.
    private BlockingQueue<FetchFuture<?> > prefetchQueue;
    
    // contains prefetches that have been sent, potentially having results.
    // XXX: might be unnecessary.  Only the application needs references
    //      to these objects
    private Set<FetchFuture<?> > prefetchCache;
    
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
            prefetchQueue.put(fetchFuture);
        
            return fetchFuture;
        } catch (InterruptedException e) {
            // the queue has no max size, so this won't happen.
            assert false;
            return null;
        }
    }
    
    /** Hint a future access and start prefetching immediately,
     *  bypassing any deferral decision.
     *
     *  This is useful for testing.
     */
    public <V> Future<V> prefetchNow(CacheFetcher<V> fetcher) {
        FetchFuture<V> fetchFuture = new FetchFuture<V>(fetcher, this);
        prefetchCache.add(fetchFuture);
        try {   
            fetchFuture.startAsync();
        } catch (CancellationException e) {
            Log.e(TAG, "No-defer prefetch cancelled before it was sent");
            fetchFuture = null;
        }
        return fetchFuture;
    }
    
    <V> Future<V> submit(Callable<V> fetcher) {
        // TODO: record whatever necessary information
        // TODO: make a method to do that, and call it from FetchFuture
        return executor.submit(fetcher);
    }
    
    void remove(FetchFuture<?> fetchFuture) {
        evaluatePrefetchDecision(fetchFuture);
        prefetchCache.remove(fetchFuture);
    }
    
    private <V> void evaluatePrefetchDecision(FetchFuture<V> fetchFuture) {
        // TODO: estimate whether I "made a mistake" with this fetch
        //       e.g. too early, too late
    }
    
    public EnergyAdaptiveCache() {
        executor = Executors.newCachedThreadPool();
        prefetchQueue = new LinkedBlockingQueue<FetchFuture<?> >();
        prefetchCache = Collections.synchronizedSet(
            new TreeSet<FetchFuture<?> >()
        );
        
        prefetchThread = new PrefetchThread();
        prefetchThread.start();
    }
    
    private PrefetchThread prefetchThread;
    
    private class PrefetchThread extends Thread {
        public void run() {
            /* TODO 
             *     forever:
             *         Check whether I should invoke a prefetch
             *         If so, submit its callable to the ExecutorService
             *         Decide how long to wait before checking again,
             *             perhaps registering an intnw notification callback
             *             to be woken up at that point
             */
            
            while (true) {
                try {
                    FetchFuture<?> prefetch = prefetchQueue.take();
                    prefetchCache.add(prefetch);

                    // Here is where we implement the prefetch delay strategy.
                    // To start, we implement the most aggressive strategy:
                    //   When a prefetch is enqueued, start it immediately.
                    prefetch.startAsync();
                    
                    // For testing, we can also try the 
                    //  most conservative strategy: never start prefetches.
                    //  That is, do nothing here and remove the above 
                    //  call to startAsync.
                    
                    // TODO: implement the real strategy
                    //  I imagine this will take the form of a 
                    //  callback with a timeout.
                    //  The callback may be triggered by an IntNW
                    //  "enhanced thunk."
                } catch (InterruptedException e) {
                    // if thrown by take(); ignore and try again.
                } catch (CancellationException e) {
                    Log.e(TAG, "Prefetch cancelled before it was sent");
                }
            }
        }
    }
}
