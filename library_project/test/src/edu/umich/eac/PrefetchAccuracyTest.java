package edu.umich.eac;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import android.test.InstrumentationTestCase;


/*
 * Assumptions:
 * 
 * Insofar as they contribute to the accuracy calculation,
 *  hints are recorded as affecting the accuracy of the
 *  *next* cache size up, since one hint indicates that
 *  the cache should potentially be expanded by one item.
 * Hits (utilized prefetch hint) are recorded as contributing to
 *  the accuracy of the minimum cache size that contains the
 *  prefetched data item. 
 */
public class PrefetchAccuracyTest extends InstrumentationTestCase {
    PrefetchAccuracy accuracy;
    EnergyAdaptiveCache cache;
    
    private class FakeFuture extends FetchFuture<Integer> {
        FakeFuture(EnergyAdaptiveCache cache) {
            super(null, cache);
        }
    }
    
    private class FakeFetcher extends CacheFetcher<String> {
        private String theString = "The string.";
        
        @Override
        public String call(int labels) throws Exception {
            return theString;
        }

        @Override
        public int bytesToTransfer() {
            return theString.length();
        }
    }
    
    @Override
    protected void setUp() {
        accuracy = new PrefetchAccuracy();
        cache = new EnergyAdaptiveCache(getInstrumentation().getContext(),
                                        PrefetchStrategyType.CONSERVATIVE);
    }
    
    public void testInitAccuracy() {
        assertEquals(0.0, accuracy.getAccuracy(), 0.001);
    }
    
    public void testSimpleAccuracyQuotient() {
        FakeFuture[] futures = new FakeFuture[5];
        for (int i = 0; i < futures.length; ++i) {
            futures[i] = new FakeFuture(cache);
            accuracy.addPrefetchHint(futures[i]);
            assertEquals(0.0, accuracy.getAccuracy(), 0.001);
        }
        
        // accuracy should increase as 1/5, 2/5, 3/5, 4/5, 5,5
        //  as newest hints are consumed
        for (int i = futures.length - 1; i >= 0; --i) {
            accuracy.markDemandFetched(futures[i]);
            assertEquals((futures.length - i) / 5.0, accuracy.getAccuracy(), 0.001);
        }
    }
    
    public void testAccuracyWithTrailingUnconsumedHints() {
        FakeFuture[] futures = new FakeFuture[6];
        for (int i = 0; i < futures.length; ++i) {
            futures[i] = new FakeFuture(cache);
            accuracy.addPrefetchHint(futures[i]);
            assertEquals(0.0, accuracy.getAccuracy(), 0.001);
        }

        accuracy.markDemandFetched(futures[0]);
        assertEquals(1.0, accuracy.getAccuracy(), 0.001);

        accuracy.markDemandFetched(futures[2]);
        assertEquals(2.0/3.0, accuracy.getAccuracy(), 0.001);

        accuracy.markDemandFetched(futures[5]);
        assertEquals(3.0/6.0, accuracy.getAccuracy(), 0.001);
    }
    
    public void testAccuracyChangesThroughCacheStats() throws InterruptedException, ExecutionException {
        assertEquals(0.0, cache.stats.getPrefetchAccuracy(), 0.001);
        
        FakeFetcher fetcher = new FakeFetcher();
        Future<String> future = cache.prefetch(fetcher);
        assertEquals(0.0, cache.stats.getPrefetchAccuracy(), 0.001);
        
        future.get();
        assertEquals(1.0, cache.stats.getPrefetchAccuracy(), 0.001);
        
        Future<String> future2 = cache.prefetch(fetcher);
        assertEquals(1.0, cache.stats.getPrefetchAccuracy(), 0.001);
        Future<String> future3 = cache.prefetch(fetcher);
        assertEquals(1.0, cache.stats.getPrefetchAccuracy(), 0.001);

        future3.get();
        assertEquals(2.0/3.0, cache.stats.getPrefetchAccuracy(), 0.001);
        future2.get();
        assertEquals(1.0, cache.stats.getPrefetchAccuracy(), 0.001);
    }
}
