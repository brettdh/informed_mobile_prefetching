package edu.umich.eac;

import java.util.concurrent.Future;

import android.test.InstrumentationTestCase;

public class AppControlsCancellationTest extends InstrumentationTestCase {
    private EnergyAdaptiveCache cache;
    private CancelFetcher fetcher;
    private Future<String> future;
    
    protected void setUp() {
        cache = new EnergyAdaptiveCache(getInstrumentation().getContext(),
                                        PrefetchStrategyType.AGGRESSIVE);
    }

    private void submitFetcher() throws InterruptedException {
        fetcher = new CancelFetcher();
        future = cache.prefetch(fetcher);

        Thread.sleep(1000);
        assertFalse("Future not done yet", future.isDone());
        assertFalse("Future not cancelled yet", future.isCancelled());
        assertTrue("Fetcher has started", fetcher.hasStarted());
    }
    
    public void testCancel() throws InterruptedException {
        submitFetcher();
        future.cancel(true);
        assertTrue(fetcher.cancelled);
        
        // check that the task was really cancelled and that another one can run now
        submitFetcher();
        future.cancel(true);
        assertTrue(fetcher.cancelled);
    }
    
    public void testReset() throws InterruptedException {
        submitFetcher();
        FetchFuture<?> futureImpl = (FetchFuture<?>) future;
        futureImpl.reset();
        assertTrue(fetcher.cancelled);
        
        // check that another task can run now
        submitFetcher();
        future.cancel(true);
    }
    
    private class CancelFetcher extends CacheFetcher<String> {
        private boolean cancelled = false;
        private boolean started = false;
        public String call(int labels) {
            synchronized(this) {
                started = true;
                while (!cancelled) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // simulate uninterruptible task by ignoring this exception.
                    }
                }
            }
            return "cancelled!";
        }
        public int bytesToTransfer() {
            return 0;
        }
        
        public void onCancelled() {
            synchronized(this) {
                cancelled = true;
                notifyAll();
            }
        }
        
        public synchronized boolean hasStarted() {
            return started;
        }
    }
}
