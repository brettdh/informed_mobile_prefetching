package edu.umich.eac;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import android.util.Log;

import edu.umich.eac.CacheFetcher;
import edu.umich.eac.IntNWLabels;

class FetchFuture<V> implements Future<V>, Comparable<FetchFuture<V>> {
    Future<V> realFuture;
    CallableWrapperFetcher fetcher;
    boolean cancelled;
    private Date timeCreated;
    private EnergyAdaptiveCache cache;
    EnergyAdaptiveCache getCache() {
        return cache;
    }
    
    private class CallableWrapperFetcher implements Callable<V> {
        int labels;
        CacheFetcher<V> labeledFetcher;
        FetchFuture<V> future;
        CallableWrapperFetcher(CacheFetcher<V> fetcher_, FetchFuture<V> future_) {
            labeledFetcher = fetcher_;
            future = future_;
            labels = IntNWLabels.BACKGROUND;
        }
        
        boolean isDemand() {
            return ((labels & IntNWLabels.ONDEMAND) != 0);
        }

        public V call() throws Exception {
            V result = labeledFetcher.call(labels);
            if (!isDemand()) {
                cache.stats.onPrefetchDone(future);
                cache.strategy.onPrefetchDone(future, false);
            }
            return result;
        }
    }
    
    boolean wasIssued() {
        return (realFuture != null);
    }
    
    public void addLabels(int labels) {
        fetcher.labels |= labels;
    }
    
    public int bytesToTransfer() {
        return fetcher.labeledFetcher.bytesToTransfer();
    }

    public double estimateFetchTime(int bandwidthDown,
                                    int bandwidthUp,
                                    int rttMillis) {
        return fetcher.labeledFetcher.estimateFetchTime(bandwidthDown,
                                                        bandwidthUp,
                                                        rttMillis);
    }

    FetchFuture(CacheFetcher<V> fetcher_, EnergyAdaptiveCache cache_) {
        realFuture = null;
        fetcher = new CallableWrapperFetcher(fetcher_, this);
        cancelled = false;
        cache = cache_;
        timeCreated = new Date();
    }
    
    long millisSinceCreated() {
        return new Date().getTime() - timeCreated.getTime();
    }
    
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (cancelled) {
            return true;
        }
        cache.stats.onFetchCancelled(this);
        cache.strategy.onPrefetchDone(this, true);
        
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
            if (!demand) {
                cache.stats.onPrefetchIssued(this);
            }
            
            // haven't submitted it yet; better do it now
            realFuture = cache.submit(fetcher, fetcher.labels);
        }
    }
    
    private synchronized Future<V> getFutureRef() {
        return realFuture;
    }
    
    public V get() throws InterruptedException, ExecutionException, 
                          CancellationException {
        cache.stats.onDemandFetch(this);
        cache.strategy.onDemandFetch(this);
        establishFuture(true);
        V result = realFuture.get();
        cache.stats.onDemandFetchDone(this);
        cache.strategy.onPrefetchDone(this, false);
        return result;
    }
    
    public V get(long timeout, TimeUnit unit) 
        throws InterruptedException, ExecutionException, 
               TimeoutException, CancellationException {
        cache.stats.onDemandFetch(this);
        cache.strategy.onDemandFetch(this);
        establishFuture(true);
        V result = realFuture.get(timeout, unit);
        cache.stats.onDemandFetchDone(this);
        cache.strategy.onPrefetchDone(this, false);
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
    
    public int compareTo(FetchFuture<V> other) {
        // arbitrary order
        return (hashCode() - other.hashCode());
    }
    
    protected void finalize() throws Throwable {
        try {
            Log.d(AdaptivePrefetchStrategy.TAG, "Cancelling prefetch because it was garbage-collected");
            cancel(true);
        } finally {
            super.finalize();
        }
    }
}
