package edu.umich.eac;

import java.util.Date;

import android.test.InstrumentationTestCase;
import android.util.Log;

public class GoalAdaptiveResourceWeightTest extends InstrumentationTestCase {
    private static final String TAG = GoalAdaptiveResourceWeightTest.class.getName();
    GoalAdaptiveResourceWeight weight;
    
    public void testWeight() throws InterruptedException {
        weight = new GoalAdaptiveResourceWeight(10, secondsInFuture(60));
        double initWeight = weight.getWeight();
        Thread.sleep(1000);
        weight.reportSpentResource(0);
        double nextWeight = weight.getWeight();
        assertEquals(initWeight, nextWeight, 0.00001); // only update every 15 seconds
        
        weight.forceUpdateWeight();
        double smallerWeight = weight.getWeight();
        assertTrue(smallerWeight < initWeight);
        
        weight.reportSpentResource(5);
        nextWeight = weight.getWeight();
        assertEquals(smallerWeight, nextWeight, 0.00001);
        weight.forceUpdateWeight();
        double largerWeight = weight.getWeight();
        assertTrue(largerWeight > smallerWeight);
    }
    
    public void testConstantlyDecreasingWeight() throws InterruptedException {
        double duration = 20.0;
        Date goalTime = secondsInFuture(duration);
        weight = new GoalAdaptiveResourceWeight(duration * (duration + 1) / 2.0, 
                                                goalTime);
        double initWeight = weight.getWeight();
        double prevWeight = initWeight;
        Thread.sleep(1000);
        weight.reportSpentResource(0);
        weight.forceUpdateWeight();
        assertTrue(weight.getWeight() < prevWeight);
        prevWeight = weight.getWeight();
        
        for (int i = 0; i < (int) duration; ++i) {
            Thread.sleep(1000);
            weight.reportSpentResource(duration - i + 1);
            weight.forceUpdateWeight();
            Log.d(TAG, "New weight: " + String.valueOf(weight.getWeight()));
            double curWeight = weight.getWeight();
            if (new Date().after(goalTime)) {
                break;
            }
            assertTrue(prevWeight <= curWeight);
            prevWeight = weight.getWeight();
        }
        assertTrue(initWeight < prevWeight);
    }

    private Date secondsInFuture(double seconds) {
        long millis = (long) (seconds * 1000);
        return new Date(System.currentTimeMillis() + millis);
    }
}
