package edu.umich.eac;

import java.util.Date;

import android.content.Context;

public class SizeLimitPrefetchStrategy extends PrefetchStrategy {
    // K9's default for prefetching
    private static int sizeLimit = 32768;

    public static void setSizeLimit(int limit) {
        sizeLimit = limit;
    }
    
    public void onPrefetchEnqueued(FetchFuture<?> prefetch) {
        if (prefetch.bytesToTransfer() < sizeLimit) {
            prefetch.startAsync(false);
        }
    }
}
