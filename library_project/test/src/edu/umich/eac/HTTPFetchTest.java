package edu.umich.eac;

import java.util.concurrent.Future;
import java.net.URL;
import java.net.URLConnection;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import android.test.InstrumentationTestCase;

import edu.umich.eac.EnergyAdaptiveCache;
import edu.umich.eac.PrefetchStrategyType;
import edu.umich.eac.CacheFetcher;

public class HTTPFetchTest extends InstrumentationTestCase {
    private EnergyAdaptiveCache cache;
    
    protected void setUp() {
        cache = new EnergyAdaptiveCache(getInstrumentation().getContext(),
                                        PrefetchStrategyType.AGGRESSIVE);
    }
    
    public void testFetch() {
        // fetch www.eecs.umich.edu
        // put the IP here so as to avoid weird DNS issues on the G1
        HTTPFetcher fetcher = new HTTPFetcher("http://141.212.113.110/");
        Future<String> f = cache.prefetch(fetcher);
        try {
            String str = f.get();
            assertTrue("Future done", f.isDone());
            assertFalse("Future not cancelled", f.isCancelled());
            assertTrue("HTML fetched", str.contains("University of Michigan"));
        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected exception:" + e.toString());
        }
    }
    
    public void testFetchAndPrefetch() {
        HTTPFetcher fetcher = new HTTPFetcher("http://141.212.113.110/");
        Future<String> f = cache.fetch(fetcher);
        try {
            String str = f.get();
            assertTrue("Future done", f.isDone());
            assertFalse("Future not cancelled", f.isCancelled());
            assertTrue("HTML fetched", str.contains("University of Michigan"));
            
            str = f.get();
            assertEquals("Fetcher only called once", 1, fetcher.numCalls);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Unexpected exception:" + e.toString());
        }
        
    }
    
    private class HTTPFetcher extends CacheFetcher<String> {
        private String myUrl;
        public HTTPFetcher(String url_) {
            myUrl = url_;
        }
        
        public int numCalls = 0;
        
        public String call(int labels) throws IOException {
            synchronized(this) {
                numCalls++;
            }
            
            URL url = new URL(myUrl);
            URLConnection conn = url.openConnection();
            InputStream in = conn.getInputStream();
            InputStreamReader streamReader = new InputStreamReader(in);
            BufferedReader reader = new BufferedReader(streamReader);

            StringBuffer buffer = new StringBuffer();
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
            in.close();

            return buffer.toString();
        }
        
        public int bytesToTransfer() {
            return 1024; // arbitrary
        }
        
        public double estimateFetchTime(int worstBandwidthDown,
                                        int worstBandwidthUp,
                                        int worstRTT) {
            return ((double) bytesToTransfer() / (double) worstBandwidthDown +
                    ((double) worstRTT) / 1000.0);
        }
    }
}
