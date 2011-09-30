package edu.umich.eac;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import android.util.Log;

public class GoalAdaptiveResourceWeight {
    private static final int UPDATE_DURATION_MILLIS = 15000;
    private static final double PROHIBITIVELY_LARGE_WEIGHT = 99999999;
    private static final String TAG = GoalAdaptiveResourceWeight.class.getName();
    private double supply;
    final private double initialSupply;
    private Date goalTime;
    private double weight;
    private Timer updateTimer;
    private TimerTask updateTask;
    
    private double spendingRate = 0.0;
    private Date lastResourceUseSample;
    
    public GoalAdaptiveResourceWeight(double supply, Date goalTime) {
        initialSupply = supply;
        this.supply = supply;
        this.goalTime = goalTime;
        lastResourceUseSample = new Date();
        
        spendingRate = 0.0;
                
        // large starting weight: I'll spend my entire budget to save
        //  an amount of time as big as my entire goal.
        this.weight = secondsUntil(goalTime) / ((double) supply);
        
        updateTimer = new Timer();
        updateTask = new TimerTask() {
            @Override
            public void run() {
                updateWeight();
            }
        };
        updateTimer.schedule(updateTask, UPDATE_DURATION_MILLIS, UPDATE_DURATION_MILLIS);
    }

    private double smoothingFactor() {
        // from Odyssey.
        Date now = new Date();
        if (goalTime.before(now)) {
            return 0.0;
        } else {
            return Math.pow(2, -1.0 / (0.1 * secondsUntil(this.goalTime)));
        }
    }

    private double secondsUntil(Date date) {
        return ((double) (date.getTime() - System.currentTimeMillis())) / 1000.0;
    }
    
    private double secondsSince(Date date) {
        return ((double) (System.currentTimeMillis() - date.getTime())) / 1000.0;
    }
    
    public synchronized void reportSpentResource(double amount) {
        double samplePeriod = secondsSince(lastResourceUseSample);
        lastResourceUseSample = new Date();
        
        double rateSample = amount / samplePeriod;
        double alpha = smoothingFactor();
        spendingRate = (1 - alpha) * rateSample + alpha * spendingRate;
        supply -= amount;
        
        Log.d(TAG, "alpha: " + String.valueOf(alpha));
        Log.d(TAG, String.format("New spending rate: %f   new supply: %f", spendingRate, supply));
    }
    
    synchronized void forceUpdateWeight() {
        updateWeight();
    }
    
    private synchronized void updateWeight() {
        Date now = new Date();
        
        Log.d(TAG, "Old weight: " + String.valueOf(weight));
        // "fudge factor" to avoid overshooting budget.  Borrowed from Odyssey.
        double adjustedSupply = supply - (0.05 * supply + 0.01 * initialSupply);
        if (adjustedSupply <= 0.0) {
            weight = PROHIBITIVELY_LARGE_WEIGHT;
        } else if (now.after(goalTime)) {
            // goal reached; spend away!  (We shouldn't see this in our experiments.)
            weight = 0.0;
        } else {
            double futureDemand = spendingRate * secondsUntil(goalTime);
            //Log.d(TAG, String.for)
            Log.d(TAG, String.format("Future demand: %f  weight %f  multiplier %f",
                                     futureDemand, weight, futureDemand / adjustedSupply));
            weight *= (futureDemand / adjustedSupply);
        }
        weight = Math.max(weight, aggressiveNonZeroWeight());
    }
    
    private double aggressiveNonZeroWeight() {
        // zero is the most aggressive weight, but if we set weight to zero,
        //  it will never increase after that point.
        // So, instead of using zero as the most aggressive weight,
        //  we use something very small but proportional to the initial supply:
        //  "I would spend my entire budget to save 10ms."
        return 0.01 / initialSupply;
    }

    public synchronized double getWeight() {
        return weight;
    }

    public synchronized void updateGoalTime(Date newGoalTime) {
        goalTime = newGoalTime;
    }
}
