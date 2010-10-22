package edu.umich.eac;

import java.util.ExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class EnergyAdaptiveCache {
    private ExecutorService executor;
    private Set<FetchFuture<?> > prefetches;
    
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
    public Future<T> prefetch(Callable<T> fetcher) {
        Future<T> fetchFuture = new FetchFuture<T>(fetcher, this);
        prefetches.add(fetchFuture);
        
        return fetchFuture;
    }
    
    Future<T> submit(FetchFuture<T> fetchFuture) {
        // TODO: record whatever necessary information
        return executor.submit(fetchFuture.fetcher);
    }
    
    void remove(FetchFuture<T> fetchFuture) {
        evaluatePrefetchDecision(fetchFuture);
        prefetches.remove(fetchFuture);
    }
    
    private void evaluatePrefetchDecision(FetchFuture<T> fetchFuture) {
        // TODO: estimate whether I "made a mistake" with this fetch
        //       e.g. too early, too late
    }
    
    public EnergyAdaptiveCache() {
        // TODO: init members
        // TODO: start prefetch thread
    }
    
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
        }
    }
}