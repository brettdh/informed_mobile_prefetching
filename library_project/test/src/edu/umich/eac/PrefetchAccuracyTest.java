package edu.umich.eac;

import java.util.PriorityQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import edu.umich.eac.AdaptivePrefetchStrategy.PrefetchTask;

import android.content.Context;
import android.test.InstrumentationTestCase;

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
    
    public void testAccuracyChangesThroughCacheStats() throws InterruptedException, ExecutionException {
        FakeFetcher fetcher = new FakeFetcher("The string.");
        FetchFuture<?> future = (FetchFuture<?>) cache.prefetch(fetcher);
        assertEquals(0.0, cache.stats.getPrefetchAccuracy(future), 0.001);
        
        future.get();
        assertEquals(1.0, cache.stats.getPrefetchAccuracy(future), 0.001);
        
        FetchFuture<?> future2 = (FetchFuture<?>) cache.prefetch(fetcher);
        assertEquals(0.5, cache.stats.getPrefetchAccuracy(future2), 0.001);
        FetchFuture<?> future3 = (FetchFuture<?>) cache.prefetch(fetcher);
        assertEquals(1.0/3.0, cache.stats.getPrefetchAccuracy(future3), 0.001);

        future3.get();
        assertEquals(2.0/3.0, cache.stats.getPrefetchAccuracy(future3), 0.001);
        future2.get();
        assertEquals(1.0, cache.stats.getPrefetchAccuracy(future2), 0.001);
    }
    
    private class FakeHTTPCacheFetcher extends CacheFetcher<Integer> {
        private int prefetchClass;
        public FakeHTTPCacheFetcher(int prefetchClass) {
            this.prefetchClass = prefetchClass;
        }
        @Override
        public Integer call(int labels) throws Exception {
            return null;
        }

        @Override
        public int bytesToTransfer() {
            return 0;
        }

        @Override
        public int getPrefetchClass() {
            return prefetchClass;
        }
    }
    
    public void testNewsreaderPrefetchOrder() {
        Context context = getInstrumentation().getContext();
        EnergyAdaptiveCache cache = new EnergyAdaptiveCache(context, PrefetchStrategyType.CONSERVATIVE);
        PriorityQueue<PrefetchTask> q = new PriorityQueue<PrefetchTask>();
        PrefetchTask[] fetches = new PrefetchTask[] {
            new PrefetchTask(new FetchFuture<Integer>(new FakeHTTPCacheFetcher(0), cache)),
            new PrefetchTask(new FetchFuture<Integer>(new FakeHTTPCacheFetcher(1), cache)),
            new PrefetchTask(new FetchFuture<Integer>(new FakeHTTPCacheFetcher(2), cache)),
            new PrefetchTask(new FetchFuture<Integer>(new FakeHTTPCacheFetcher(0), cache)),
            new PrefetchTask(new FetchFuture<Integer>(new FakeHTTPCacheFetcher(2), cache)),
            new PrefetchTask(new FetchFuture<Integer>(new FakeHTTPCacheFetcher(1), cache))
        };

        for (PrefetchTask task : fetches) {
            q.add(task);
        }

        assertHeadIs(q, fetches[2]);
        assertHeadIs(q, fetches[4]);
        assertHeadIs(q, fetches[0]);
        assertHeadIs(q, fetches[3]);
        assertHeadIs(q, fetches[1]);
        assertHeadIs(q, fetches[5]);
    }
    
    private void assertHeadIs(PriorityQueue<PrefetchTask> q, PrefetchTask expected) {
        PrefetchTask actual = q.remove();
        assertEquals(expected, actual);
    }
}
