package edu.umich.eac;

import junit.framework.TestCase;

public class PrefetchAccuracyTest extends TestCase {
    PrefetchAccuracy accuracy;
    
    private class FakeFuture extends FetchFuture<Integer> {
        FakeFuture() {
            super(null, null);
        }
    }
    
    @Override
    protected void setUp() {
        accuracy = new PrefetchAccuracy();
    }
    
    public void testInitAccuracy() {
        assertEquals(1.0, accuracy.getNextAccuracy(), 0.001);
    }
    
    public void testSimpleAccuracy() {
        FakeFuture[] futures = new FakeFuture[3];
        for (int i = 0; i < futures.length; ++i) {
            futures[i] = new FakeFuture();
        }
        accuracy.addPrefetchHint(futures[0]);
        assertEquals(1.0, accuracy.getNextAccuracy(), 0.001);
        accuracy.addIssuedPrefetch(futures[0]);
        assertEquals(1.0, accuracy.getNextAccuracy(), 0.001);
        accuracy.removePrefetch(futures[0]);
        assertEquals(0.0, accuracy.getNextAccuracy(), 0.001);

        accuracy.addPrefetchHint(futures[1]);
        accuracy.addIssuedPrefetch(futures[1]);
        accuracy.markDemandFetched(futures[1]);
        accuracy.removePrefetch(futures[1]);
        assertEquals(0.5, accuracy.getNextAccuracy(), 0.001);
    }
}
