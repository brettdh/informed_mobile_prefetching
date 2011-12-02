package edu.umich.eac;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import android.test.InstrumentationTestCase;

public class AdaptiveStrategyTest extends InstrumentationTestCase {
    EnergyAdaptiveCache cache;
    
    @Override
    public void setUp() {
        AdaptivePrefetchStrategy.setStaticParams(0, 0.00000001);
        cache = new EnergyAdaptiveCache(getInstrumentation().getContext(),
                                        PrefetchStrategyType.ADAPTIVE, 6, 6000, 2048);
    }
    
    public void testDemandFetchDoesNotImpedePrefetches() throws InterruptedException, ExecutionException {
        FakeFetcher fetcher = new FakeFetcher("the string.");
        Future<String> future = cache.prefetch(fetcher); // shouldn't prefetch; accuracy is zero
        Thread.sleep(5000);
        assertFalse(future.isDone());
        future.get();
        assertTrue(future.isDone());
        
        FakeFetcher fetcher2 = new FakeFetcher("Another string.");
        Future<String> future2 = cache.prefetch(fetcher2); // should prefetch immediately; accuracy is 0.5
        Thread.sleep(5000);
        assertTrue(future2.isDone());
    }
}
