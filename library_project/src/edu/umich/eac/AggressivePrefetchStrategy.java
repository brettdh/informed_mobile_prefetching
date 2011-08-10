package edu.umich.eac;

import edu.umich.eac.PrefetchStrategy;
import edu.umich.eac.FetchFuture;

class AggressivePrefetchStrategy extends PrefetchStrategy {
    public void onPrefetchEnqueued(FetchFuture<?> prefetch) {
        // To start, we implement the most aggressive strategy:
        //   When a prefetch is enqueued, start it immediately.
        prefetch.startAsync(false);
    }
}
