package edu.umich.eac.tests;

import java.util.concurrent.Future;
import android.test.InstrumentationTestCase;

import edu.umich.eac.EnergyAdaptiveCache;
import edu.umich.eac.PrefetchStrategyType;
import edu.umich.eac.CacheFetcher;

public class SimpleNoNetworkTest extends InstrumentationTestCase {
    private EnergyAdaptiveCache cache;
    
    protected void setUp() {
        cache = new EnergyAdaptiveCache(PrefetchStrategyType.AGGRESSIVE);
    }
    
    public void testImmediateGet() {
        testWithDelay(0);
    }
    
    public void testDelayedGet() {
        testWithDelay(5);
    }
    
    private void testWithDelay(long delaySecs) {
        Future<String> f = cache.prefetch(new FakeFetcher(delaySecs));
        try {
            if (delaySecs > 0) {
                assertFalse("Future not done yet", f.isDone());
            }
            String str = f.get();
            assertEquals("String matches", str, FakeFetcher.msg);
            assertTrue("Future done", f.isDone());
            assertFalse("Future not cancelled", f.isCancelled());
        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected exception:" + e.toString());
        }
    }
    
    private class FakeFetcher implements CacheFetcher<String> {
        static final String msg = "This is the string you asked for.";
        
        long delaySeconds;
        
        FakeFetcher(long delaySecs) {
            delaySeconds = delaySecs;
        }
        
        public String call(int labels) {
            if (delaySeconds > 0) {
                try {
                    Thread.currentThread();
                    Thread.sleep(delaySeconds * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    fail("Thread sleep interrupted; shouldn't happen");
                }
            }
            return msg;
        }
        
        public int bytesToTransfer() {
            return msg.length();
        }
        
        public double estimateFetchTime(int worstBandwidthDown,
                                        int worstBandwidthUp,
                                        int worstRTT) {
            return ((double) bytesToTransfer() / (double) worstBandwidthDown +
                    ((double) worstRTT) / 1000.0);
        }

    }
}
