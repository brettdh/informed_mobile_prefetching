package edu.umich.eac;

public abstract class CacheFetcher<V> {
    static final int DEFAULT_PREFETCH_CLASS = -1;

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
     * IMPORTANT: subclasses of this MUST return from call() immediately
     *  after being cancelled via interruption or the onCancelled callback.
     */
    public void onCancelled() {}

    /**
     * Override to separate prefetch hints by class, where different classes
     * may differ significantly in terms of the accuracy of the prefetch hint.
     * For example, a newsreader application may build a model over time for
     * predicting which feeds the user is most interested in, and it can use this
     * method to separate high-confidence feeds from low-confidence feeds.
     * @return An integer that will be used to group prefetch hints into classes.
     */
    public int getPrefetchClass() {
        return DEFAULT_PREFETCH_CLASS;
    }
}
