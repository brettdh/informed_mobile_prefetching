package edu.umich.eac;

import java.util.Arrays;
import java.util.Date;

import edu.umich.eac.WifiTracker.Prediction;

import android.content.Context;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.test.InstrumentationTestCase;

public class WifiTrackerTest extends InstrumentationTestCase {
    WifiTracker predictor;
    protected void setUp() throws InterruptedException {
        predictor = new WifiTracker(getInstrumentation().getContext());
    }
    
    private void printPrediction(Prediction pred) {
        System.out.println(pred.toString());
    }
    
    public void testPrediction() {
        Prediction current = predictor.getCurrentConditions();
        assertTrue(current != null);
        printPrediction(current);
        
        Prediction future = predictor.predictConditionsChange();
        assertTrue(future != null);
        printPrediction(future);
        
        for (Double timeInFuture : Arrays.asList(3.0, 6.0, 10.0, 15.0, 24.0, 28.4)) {
            future = predictor.getFutureConditions(timeInFuture);
            assertTrue(future != null);
            printPrediction(future);
        }
        
        double tooFar = WifiTracker.predictionHorizon + 5.0;
        future = predictor.getFutureConditions(tooFar);
        assertTrue(future == null);
    }
    
    public void testAvailabilityTracking() throws InterruptedException {
        Context context = getInstrumentation().getContext();
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        assertTrue(wifi.isWifiEnabled());
        assertTrue(wifi.getConnectionInfo().getSupplicantState() == SupplicantState.COMPLETED);
        assertNotNull(wifi.getDhcpInfo());
        int netId = wifi.getConnectionInfo().getNetworkId();
        assertTrue(wifi.setWifiEnabled(false));
        while (wifi.isWifiEnabled()) {
            Thread.sleep(1000);
        }
        
        WifiTracker tracker = new WifiTracker(context);
        assertEquals(0.0, tracker.availability());
        Thread.sleep(5000); // ignore the brief bit of connectivity at the beginning
        assertTrue(tracker.availability() < 0.5);
        
        int secondsToWait = 30;
        assertTrue(wifi.setWifiEnabled(true));
        while (wifi.enableNetwork(netId, true) == false) {
            Thread.sleep(1000);
        }
        for (int i = 0; i < secondsToWait; ++i) {
            if (tracker.availability() > 0.5) {
                return;
            }
            Thread.sleep(1000);
        }
        fail("Should have seen > 50% wifi availability");
    }
}
