package edu.umich.eac.tests;

import java.util.concurrent.Future;
import java.util.concurrent.Callable;
import java.net.URL;
import java.net.URLConnection;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import android.test.InstrumentationTestCase;

import edu.umich.eac.EnergyAdaptiveCache;

public class HTTPFetchTest extends InstrumentationTestCase {
    private EnergyAdaptiveCache cache;
    
    protected void setUp() {
        cache = new EnergyAdaptiveCache();
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
    
    private class HTTPFetcher implements Callable<String> {
        private String myUrl;
        public HTTPFetcher(String url_) {
            myUrl = url_;
        }
        
        public String call() throws IOException {
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
    }
}
