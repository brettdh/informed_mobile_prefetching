package edu.umich.eac;

import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import android.test.InstrumentationTestCase;

import edu.umich.eac.EnergyAdaptiveCache;
import edu.umich.eac.PrefetchStrategyType;
import edu.umich.eac.CacheFetcher;

public class CancelTest extends InstrumentationTestCase {
    private EnergyAdaptiveCache cache;
    CancelFetcher fetcher;
    private Future<String> future;
    
    protected void setUp() throws InterruptedException {
        cache = new EnergyAdaptiveCache(getInstrumentation().getContext(),
                                        PrefetchStrategyType.AGGRESSIVE);
        fetcher = new CancelFetcher();
        future = cache.prefetch(fetcher);
        Thread.currentThread();
        Thread.sleep(1000);
        assertFalse("Future not done yet", future.isDone());
        assertFalse("Future not cancelled yet", future.isCancelled());
    }

    public void testGetTimeout() {
        try {
            future.get(1, TimeUnit.SECONDS);
            fail("Should have thrown TimeoutException");
        } catch (TimeoutException e) {
            assertFalse("Future not done after timeout", future.isDone());
            assertFalse("Future not cancelled after timeout", 
                        future.isCancelled());
        } catch (Exception e) {
            fail("Shouldn't throw any other exception");
        }
    }
    
    public void testCancel() {
        boolean ret = future.cancel(true);
        assertTrue("cancel succeeds", ret);
        assertTrue("Future is cancelled", future.isCancelled());
        assertTrue("Future is not done after cancel", future.isDone());
        try {
            future.get(1, TimeUnit.SECONDS);
            fail("Cancelled Future.get() should throw CancellationException");
        } catch (TimeoutException e) {
            fail("Cancelled Future.get() should fail outright, not time out");
        } catch (CancellationException e) {
            // success
        } catch (Exception e) {
            fail("Shouldn't throw any other exception");
        }
    }
    
    private class CancelFetcher extends CacheFetcher<String> {
        public String call(int labels) throws InterruptedException {
            // Never return; just wait to be cancelled.
            while (true) {
                Thread.currentThread();
                Thread.sleep(3600 * 1000);
            }
        }
        public int bytesToTransfer() {
            return 0;
        }
        public double estimateFetchTime(int worstBandwidthDown,
                                        int worstBandwidthUp,
                                        int worstRTT) {
            return ((double) bytesToTransfer() / (double) worstBandwidthDown +
                    ((double) worstRTT) / 1000.0);
        }
    }
}
