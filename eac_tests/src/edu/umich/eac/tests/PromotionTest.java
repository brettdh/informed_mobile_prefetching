package edu.umich.eac.tests;

import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
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
        cache = new EnergyAdaptiveCache(PrefetchStrategyType.AGGRESSIVE);
        fetcher = new PromotionFetcher();
        future = cache.prefetchNow(fetcher);
        Thread.currentThread().sleep(1000);
        assertFalse("Future not done yet", future.isDone());
        assertFalse("Future not cancelled yet", future.isCancelled());
    }

    public void testWaitForPrefetch() throws Exception {
        try {
             // wait for prefetch to complete
            Thread.currentThread().sleep(3000);
            
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
    
    private class PromotionFetcher implements CacheFetcher<String> {
        public String call(int labels) throws InterruptedException {
            if ((labels & IntNWLabels.BACKGROUND) != 0) {
                // simulate a background fetch taking longer (exaggerated)
                Thread.currentThread().sleep(3000);
                return "Got prefetch result";
            } else {
                return "Got demand fetch result";
            }
        }
    }
}