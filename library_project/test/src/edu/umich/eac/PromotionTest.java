package edu.umich.eac;

import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import android.test.InstrumentationTestCase;

import edu.umich.eac.EnergyAdaptiveCache;
import edu.umich.eac.PrefetchStrategyType;
import edu.umich.eac.CacheFetcher;
import edu.umich.eac.IntNWLabels;

public class PromotionTest extends InstrumentationTestCase {
    private EnergyAdaptiveCache cache;
    PromotionFetcher fetcher;
    private Future<String> future;
    
    protected void setUp() throws InterruptedException {
        cache = new EnergyAdaptiveCache(getInstrumentation().getContext(),
                                        PrefetchStrategyType.AGGRESSIVE);
        fetcher = new PromotionFetcher();
        future = cache.prefetchNow(fetcher);
        Thread.currentThread();
        Thread.sleep(1000);
        assertFalse("Future not done yet", future.isDone());
        assertFalse("Future not cancelled yet", future.isCancelled());
    }

    public void testWaitForPrefetch() throws Exception {
        try {
             Thread.currentThread();
            // wait for prefetch to complete
            Thread.sleep(3000);
            
            String msg = future.get(1, TimeUnit.SECONDS);
            assertTrue("Did the prefetch", msg.contains("prefetch"));
        } catch (TimeoutException e) {
            fail("Prefetch shouldn't time out");
        }
    }

    public void testPromotion() throws Exception {
        try {
            // should promote BG fetch to FG
            String msg = future.get(1, TimeUnit.SECONDS);
            assertTrue("Did the demand fetch", msg.contains("demand"));
        } catch (TimeoutException e) {
            fail("Demand fetch shouldn't time out");
        }
    }
    
    private class PromotionFetcher extends CacheFetcher<String> {
        private static final String PREFETCH_MSG = "Got prefetch result";
        private static final String DEMAND_FETCH_MSG = "Got demand fetch result";

        public String call(int labels) throws InterruptedException {
            if ((labels & IntNWLabels.BACKGROUND) != 0) {
                Thread.currentThread();
                // simulate a background fetch taking longer (exaggerated)
                Thread.sleep(3000);
                return PREFETCH_MSG;
            } else {
                return DEMAND_FETCH_MSG;
            }
        }
        
        public int bytesToTransfer() {
            return DEMAND_FETCH_MSG.length();
        }
        
        public double estimateFetchTime(int worstBandwidthDown,
                                        int worstBandwidthUp,
                                        int worstRTT) {
            return ((double) bytesToTransfer() / (double) worstBandwidthDown +
                    ((double) worstRTT) / 1000.0);
        }

    }
}