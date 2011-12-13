package edu.umich.eac;

import java.util.Date;

import android.content.Context;

public class SizeLimitPrefetchStrategy extends PrefetchStrategy {
    // K9's default for prefetching
    private static int sizeLimit = 32*1024;
    
    // just above median size in newsreader article set
    //private static int sizeLimit = 128*1024;

    // TODO: use this in benchmark setup to set the limit with an argument.
    private static void setSizeLimit(int limit) {
        sizeLimit = limit;
    }
    
    public void onPrefetchEnqueued(FetchFuture<?> prefetch) {
        if (prefetch.bytesToTransfer() < sizeLimit) {
            prefetch.startAsync(false);
        }
    }
}
