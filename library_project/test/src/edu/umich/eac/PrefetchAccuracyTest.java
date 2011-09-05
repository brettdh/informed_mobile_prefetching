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
        assertTrue(accuracy.getNextAccuracy() > 0.0);
    }
    
    public void testSimpleAccuracy() {
        FakeFuture[] futures = new FakeFuture[2];
        for (int i = 0; i < futures.length; ++i) {
            futures[i] = new FakeFuture(cache);
        }
        accuracy.addPrefetchHint(futures[0]);
        accuracy.addIssuedPrefetch(futures[0]);
        // cache: [A]
        // hints: [1]
        // hits:  [0]

        accuracy.removePrefetch(futures[0]);
        // cache: 
        // hints: [1]
        // hits:  [0]

        accuracy.addPrefetchHint(futures[1]);
        // cache: 
        // hints: [2]
        // hits:  [0]
        // accuracy would be zero here, but we will want it 'smoothed' up to non-zero;
        //  see testSmoothing()

        accuracy.addIssuedPrefetch(futures[1]);
        // cache: [B]
        // hints: [2]
        // hits:  [0]
        
        accuracy.markDemandFetched(futures[1]);
        // cache: [B]
        // hints: [2]
        // hits:  [1]

        accuracy.removePrefetch(futures[1]);
        // cache: 
        // hints: [2]
        // hits:  [1]
        // next accuracy = 1 hit / 2 hints = 1/2
        assertEquals(0.5, accuracy.getNextAccuracy(), 0.001);
    }
    
    public void testAccuracyWithDepth() {
        FakeFuture[] futures = new FakeFuture[4];
        for (int i = 0; i < futures.length; ++i) {
            futures[i] = new FakeFuture(cache);
        }
    
        for (int i = 0; i < 3; ++i) {
            accuracy.addPrefetchHint(futures[i]);
            accuracy.addIssuedPrefetch(futures[i]);
        }
        // cache: [A][B][C]
        // hints: [1][1][1]
        // hits:  [0][0][0]
        
        accuracy.markDemandFetched(futures[0]);
        // cache: [A][B][C]
        // hints: [1][1][1]
        // hits:  [1][0][0]

        accuracy.removePrefetch(futures[0]);
        // cache: [B][C]
        // hints: [1][1][1]
        // hits:  [1][0][0]
        // next accuracy = 1 hit / (1+1+1) hints = 1/3
        assertEquals(0.3333, accuracy.getNextAccuracy(), 0.001);
        
        accuracy.addPrefetchHint(futures[3]);
        accuracy.addIssuedPrefetch(futures[3]);
        accuracy.markDemandFetched(futures[3]);
        // cache: [B][C][D]
        // hints: [1][1][2]
        // hits:  [1][0][1]
        
        accuracy.removePrefetch(futures[3]);
        // cache: [B][C]
        // hints: [1][1][2]
        // hits:  [1][0][1]
        // next accuracy = 2 hits / 4 hints = 1/2
        assertEquals(0.5, accuracy.getNextAccuracy(), 0.001);
        
        accuracy.removePrefetch(futures[2]);
        // cache: [B]
        // hints: [1][1][2]
        // hits:  [1][0][1]
        // next accuracy = 1 hits / 2 hints = 1/2
        assertEquals(0.5, accuracy.getNextAccuracy(), 0.001);
        
        accuracy.removePrefetch(futures[1]);
        // cache: 
        // hints: [1][1][2]
        // hits:  [1][0][1]
        // next accuracy = 1 hits / 1 hints = 1
        assertEquals(1.0, accuracy.getNextAccuracy(), 0.001);
    }
    
    /*
     * Test that the getNextAccuracy method doesn't return 0.
     * It should instead return some value 
     */
    public void testSmoothing() {
        FakeFuture future = new FakeFuture(cache);
        
        assertTrue(accuracy.getNextAccuracy() > 0.0);
        accuracy.addPrefetchHint(future);
        accuracy.addIssuedPrefetch(future);
        accuracy.removePrefetch(future);
        assertTrue(accuracy.getNextAccuracy() > 0.0);
    }
}
