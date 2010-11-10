package edu.umich.eac;

public interface CacheFetcher<V> {
    /** Similar to Callable<V>, but takes the arguments
     *    that will change depending on e.g. whether we're 
     *    doing a prefetch or a demand fetch.
     */
    public V call(int labels) throws Exception;
}
