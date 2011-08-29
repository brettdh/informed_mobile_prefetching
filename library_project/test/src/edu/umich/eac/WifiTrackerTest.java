package edu.umich.eac;

import java.util.Arrays;

import edu.umich.eac.WifiTracker.Prediction;

import android.test.InstrumentationTestCase;

public class WifiTrackerTest extends InstrumentationTestCase {
    WifiTracker predictor;
    protected void setUp() throws InterruptedException {
        predictor = new WifiTracker();
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
}
