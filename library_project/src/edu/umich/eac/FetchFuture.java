package edu.umich.eac;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

class FetchFuture<V> implements Future<V>, Comparable<FetchFuture<V> > {
    Future<V> realFuture;
    Callable<V> fetcher;
    boolean cancelled;
    V result;
    private EnergyAdaptiveCache cache;
    
    FetchFuture(Callable<V> fetcher_, EnergyAdaptiveCache cache_) {
        realFuture = null;
        fetcher = fetcher_;
        cancelled = false;
        result = null;
        cache = cache_;
    }
    
    public boolean cancel(boolean mayInterruptIfRunning) {
        // XXX: needs a closer look and testing.
        if (cancelled) {
            return true;
        }
        
        Future<V> f = getFutureRef();
        if (f != null) {
            cancelled = f.cancel(mayInterruptIfRunning);
            return cancelled;
        } else {
            cache.remove(this);
            cancelled = true;
            return true;
        }
    }
    
    private synchronized void establishFuture() throws CancellationException {
        if (cancelled) throw new CancellationException();
        if (realFuture == null) {
            // haven't submitted it yet; better do it now
            realFuture = cache.submit(this);
        }
    }
    
    private synchronized Future<V> getFutureRef() {
        return realFuture;
    }
    
    public V get() throws InterruptedException, ExecutionException, 
                          CancellationException {

        establishFuture();
        result = realFuture.get();
        cache.remove(this);
        return result;
    }
    
    public V get(long timeout, TimeUnit unit) 
        throws InterruptedException, ExecutionException, 
               TimeoutException, CancellationException {
            
        establishFuture();
        result = realFuture.get(timeout, unit);
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
    
    void startAsync() throws ExecutionException {
        try {
            get(0, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // thrown by get(); expected
            // nothing; I just wanted to get this fetch a-rollin'
        } catch (InterruptedException e) {
            // if thrown by get(), fetch is still in progress,
            //  even though get() was somehow interrupted. ignore.
        }
    }
    
    public int compareTo(FetchFuture<V> other) {
        // arbitrary order
        return (hashCode() - other.hashCode());
    }
}