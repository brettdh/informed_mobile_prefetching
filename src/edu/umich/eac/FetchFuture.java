package edu.umich.eac;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

private class FetchFuture<V> implements Future<V> {
    Future<V> fetchFuture;
    Callable<V> fetcher;
    V result;
    private EnergyAdaptiveCache cache;
    
    FetchFuture(Callable<V> fetcher_, EnergyAdaptiveCache cache_) {
        fetchFuture = null;
        fetcher = fetcher_;
        result = null;
        cache = cache_;
    }
    
    public boolean cancel(boolean mayInterruptIfRunning) {
        
    }
    
    private synchronized void establishFuture() {
        if (fetchFuture == null) {
            // haven't submitted it yet; better do it now
            fetchFuture = cache.submit(fetcher);
        }
    }
    
    public V get() {
        establishFuture();
        result = fetchFuture.get();
        cache.remove(this);
        return result;
    }
    
    public V get(long timeout, TimeUnit unit) {
        establishFuture();
        result = fetchFuture.get(timeout, unit);
        cache.remove(this);
        return result;
    }
    
    public boolean isCancelled() {
        
    }
    
    public boolean isDone() {
        Future<V> f;
        synchronized (this) {
            f = fetchFuture;
        }
        return (f != null && f.isDone());
    }
}