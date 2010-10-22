package edu.umich.eac;

import java.util.ExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class EnergyAdaptiveCache {
    /** Hints a future access and gets a Future for that access.
     *
     *  TODO: figure out if ordering constraints, 
     *
     *  @param fetcher Application-specific logic to fetch a data item
     *                 and return an object representing it.
     *  @return A Future that can be used later to actually carry out
     *          the hinted access, via get().
     */
    public Future<T> prefetch(Callable<T> fetcher) {
        
    }
    
    private ExecutorService executor;
}