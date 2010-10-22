package edu.umich.eac;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

private class FetchFuture<V> implements Future<V> {
    private Future<V> fetchFuture;
    private Callable<V> fetcher;
    private ExecutorService executor;
    
    FetchFuture(Callable<V> fetcher_, ExecutorService executor_) {
        fetchFuture = null;
        fetcher = fetcher_;
        executor = executor_;
    }
    
    public boolean cancel(boolean mayInterruptIfRunning) {
        
    }
    
    private synchronized void establishFuture() {
        if (fetchFuture == null) {
            // haven't submitted it yet; better do it now
            fetchFuture = executor.submit(fetcher);
        }
    }
    
    private void evaluatePrefetchDecision() {
        // TODO: estimate whether I "made a mistake" with this fetch
        //       e.g. too early, too late
    }
    
    public V get() {
        establishFuture();
        V result = fetchFuture.get();
        evaulatePrefetchDecision();
        return result;
    }
    
    public V get(long timeout, TimeUnit unit) {
        establishFuture();
        V result = fetchFuture.get(timeout, unit);
        evaulatePrefetchDecision();
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