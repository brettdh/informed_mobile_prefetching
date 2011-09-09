package edu.umich.eac;

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
}
