package edu.umich.eac;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import edu.umich.eac.CacheFetcher;
import edu.umich.eac.IntNWLabels;

class FetchFuture<V> implements Future<V>, Comparable<FetchFuture<V>> {
    Future<V> realFuture;
    CallableWrapperFetcher fetcher;
    boolean cancelled;
    private EnergyAdaptiveCache cache;
    EnergyAdaptiveCache getCache() {
        return cache;
    }
    
    private class CallableWrapperFetcher implements Callable<V> {
        int labels;
        CacheFetcher<V> labeledFetcher;
        CallableWrapperFetcher(CacheFetcher<V> fetcher_) {
            labeledFetcher = fetcher_;
            labels = IntNWLabels.BACKGROUND;
        }
        
        boolean isDemand() {
            return ((labels & IntNWLabels.ONDEMAND) != 0);
        }

        public V call() throws Exception {
            return labeledFetcher.call(labels);
        }
    }
    
    FetchFuture(CacheFetcher<V> fetcher_, EnergyAdaptiveCache cache_) {
        realFuture = null;
        fetcher = new CallableWrapperFetcher(fetcher_);
        cancelled = false;
        cache = cache_;
    }
    
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (cancelled) {
            return true;
        }
        cache.remove(this);
        
        Future<V> f = getFutureRef();
        if (f == null) {
            cancelled = true;
            return true;
        } else {
            cancelled = f.cancel(mayInterruptIfRunning);
            return cancelled;
        }
    }
    
    private synchronized void establishFuture(boolean demand)
        throws CancellationException {
        if (cancelled) throw new CancellationException();
        if (demand) {
            if (realFuture != null && !fetcher.isDemand() && 
                !realFuture.isDone()) {
                // there's a pending background prefetch.
                // cancel it and start a demand fetch.
                // XXX: this might be bad if the fetch is 
                //      large and almost done.  Hence label promotion.
                realFuture.cancel(true);
                realFuture = null;
            }
            fetcher.labels &= ~IntNWLabels.BACKGROUND;
            fetcher.labels |= IntNWLabels.ONDEMAND;
        } else {
            fetcher.labels &= ~IntNWLabels.ONDEMAND;
            fetcher.labels |= IntNWLabels.BACKGROUND;
        }
        
        if (realFuture == null) {
            // haven't submitted it yet; better do it now
            realFuture = cache.submit(fetcher, fetcher.labels);
        }
    }
    
    private synchronized Future<V> getFutureRef() {
        return realFuture;
    }
    
    public V get() throws InterruptedException, ExecutionException, 
                          CancellationException {

        establishFuture(true);
        V result = realFuture.get();
        cache.remove(this);
        return result;
    }
    
    public V get(long timeout, TimeUnit unit) 
        throws InterruptedException, ExecutionException, 
               TimeoutException, CancellationException {
            
        establishFuture(true);
        V result = realFuture.get(timeout, unit);
        cache.remove(this);
        return result;
    }
    
    public boolean isCancelled() {
        Future <V> f = getFutureRef();
        return cancelled || (f != null && f.isCancelled());
    }
    
    public boolean isDone() {
        Future<V> f = getFutureRef();
        return cancelled || (f != null && f.isDone());
    }
    
    /** Asynchronously, get this prefetch going as a fetch.
     *
     *  This just involves submitting the Callable to the ExecutorService.
     */
    void startAsync(boolean demand) {
        establishFuture(demand);
    }
    
    /**
     * Resubmit this prefetch to its EnergyAdaptiveCache.
     * This is used to make a new prefetch decision after
     * a prefetch has been deferred and rescheduled.
     */
    void addToPrefetchQueue() {
        try {
            cache.addToQueue(this);
        } catch (InterruptedException e) {
            // queue is unbounded; shouldn't happen
            assert false;
        }
    }
    
    public int compareTo(FetchFuture<V> other) {
        // arbitrary order
        return (hashCode() - other.hashCode());
    }
}