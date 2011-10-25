package edu.umich.eac;

public abstract class CacheFetcher<V> {
    /** Similar to Callable<V>, but takes the arguments
     *    that will change depending on e.g. whether we're 
     *    doing a prefetch or a demand fetch.
     */
    public abstract V call(int labels) throws Exception;

    // TODO: give this a default implementation too.
    // that default impl will just keep an average of all fetch sizes.
    public abstract int bytesToTransfer();

    public double estimateFetchTime(int worstBandwidthDown,
                                    int worstBandwidthUp,
                                    int worstRTT) {
        // default implementation; assume downstream transfer dominates
        return ((double) bytesToTransfer()) / ((double) worstBandwidthDown);
    }

    /**
     * Override onCancelled to specify app-specific cancellation handing,
     *  if Java-level task interruption isn't sufficient to cancel the fetch.
     *  Example: a JNI-implemented fetcher that calls pthread_cond_wait.
     */
    public void onCancelled() {}
}
