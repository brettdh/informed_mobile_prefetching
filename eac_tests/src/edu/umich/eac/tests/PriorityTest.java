package edu.umich.eac.tests;

import java.util.List;
import java.util.LinkedList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import android.test.InstrumentationTestCase;

import edu.umich.eac.EnergyAdaptiveCache;
import edu.umich.eac.PrefetchStrategyType;
import edu.umich.eac.CacheFetcher;

public class PriorityTest extends InstrumentationTestCase {
    private EnergyAdaptiveCache cache;
    private List<Future<String>> futures = new LinkedList<Future<String>>();
    
    protected void setUp() throws InterruptedException {
        cache = new EnergyAdaptiveCache(PrefetchStrategyType.AGGRESSIVE);
        
        for (int i = 0; i < 20; ++i) {
            Future future = cache.prefetch(new CancelFetcher());
            futures.add(future); // so it doesn't get GC'd
            assertFalse("Future not done yet", future.isDone());
            assertFalse("Future not cancelled yet", future.isCancelled());
        }
    }

    public void testGet() {
        try {
            Future<String> future = cache.fetch(new TrivialFetcher());
            String msg = future.get(1, TimeUnit.SECONDS);
            assertEquals("Demand fetch took priority", TrivialFetcher.msg, msg);
        } catch (TimeoutException e) {
            fail("Should not have timed out");
        } catch (Exception e) {
            fail("Shouldn't throw any other exception");
        }
    }
    
    private class CancelFetcher implements CacheFetcher<String> {
        public String call(int labels) throws InterruptedException {
            // Never return; just wait to be cancelled.
            while (true) {
                Thread.currentThread().sleep(3600 * 1000);
            }
        }
    }
    
    private class TrivialFetcher implements CacheFetcher<String> {
        final static String msg = "Done!";
        public String call(int labels) {
            return msg;
        }
    }
}
