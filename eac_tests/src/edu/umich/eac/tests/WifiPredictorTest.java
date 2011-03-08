package edu.umich.eac.tests;

import java.util.Arrays;

import android.test.InstrumentationTestCase;

import edu.umich.eac.WifiBandwidthPredictor;
import edu.umich.eac.WifiBandwidthPredictor.Prediction;

public class WifiPredictorTest extends InstrumentationTestCase {
    WifiBandwidthPredictor predictor;
    protected void setUp() throws InterruptedException {
        predictor = new WifiBandwidthPredictor();
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
        
        double tooFar = WifiBandwidthPredictor.predictionHorizon + 5.0;
        future = predictor.getFutureConditions(tooFar);
        assertTrue(future == null);
    }
}
