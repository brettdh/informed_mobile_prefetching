package edu.umich.eac;

import java.util.ArrayList;
import java.util.Collection;

import edu.umich.eac.AdaptivePrefetchStrategy.PrefetchTask;

public class PrefetchBatch {
    private ArrayList<PrefetchTask> prefetches = new ArrayList<PrefetchTask>();
    
    public void addPrefetch(PrefetchTask prefetch) {
        prefetches.add(prefetch);
    }
    
    public int bytesToTransfer() {
        int bytes = 0;
        for (PrefetchTask task : prefetches) {
            bytes += task.prefetch.bytesToTransfer();
        }
        return bytes;
    }

    public double estimateFetchTime(int bandwidthDown,
                                    int bandwidthUp,
                                    int rttMillis) {
        double fetchTime = 0.0;
        for (PrefetchTask task : prefetches) {
            fetchTime += task.prefetch.estimateFetchTime(bandwidthDown,
                                                         bandwidthUp,
                                                         rttMillis);
        }
        return fetchTime;
    }

    public PrefetchTask first() {
        return prefetches.get(0);
    }
    
    public int size() {
        return prefetches.size();
    }

    public void pop() {
        prefetches.remove(0);
    }
    
    public void drainTo(Collection<PrefetchTask> collection) {
        collection.addAll(prefetches);
        prefetches.clear();
    }
}
