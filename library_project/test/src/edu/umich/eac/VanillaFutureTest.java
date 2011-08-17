package edu.umich.eac;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import android.test.InstrumentationTestCase;

public class VanillaFutureTest extends InstrumentationTestCase {
    private ExecutorService executor;
    
    protected void setUp() {
        executor = Executors.newCachedThreadPool();
    }
    
    public void testCallCount() throws Exception {
        FakeFetcher fetcher = new FakeFetcher();
        Future<String> f = executor.submit(fetcher);
        String msg = null;
        for (int i = 0; i < 4; ++i) {
            msg = f.get();
        }
        assertEquals("String matches", FakeFetcher.msg, msg);
        assertEquals("Only one call()", 1, fetcher.numCalls);
    }
    
    private class FakeFetcher implements Callable<String> {
        static final String msg = "This is the string you asked for.";
        
        public int numCalls = 0;
        public String call() throws Exception {
            synchronized(this) {
                numCalls++;
            }
            Thread.currentThread();
            Thread.sleep(500);
            return msg;
        }
    }
}
