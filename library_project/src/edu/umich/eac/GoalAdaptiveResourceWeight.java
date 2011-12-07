package edu.umich.eac;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import android.util.Log;

public class GoalAdaptiveResourceWeight {
    private static final int UPDATE_DURATION_MILLIS = 1000;
    private static final double PROHIBITIVELY_LARGE_WEIGHT = Math.pow(2, 200); // really large, but shouldn't overflow (max ~ 2^1023)
    
    private static final String TAG = GoalAdaptiveResourceWeight.class.getName();
    private double supply;
    final private double initialSupply;
    private Date goalTime;
    private double weight;
    private Timer updateTimer;
    private TimerTask updateTask;
    
    private double spendingRate = 0.0;
    private Date lastResourceUseSample;
    private AdaptivePrefetchStrategy strategy;
    private String type;
    
    private void logPrint(String msg) {
        if (strategy != null) {
            strategy.logPrint(msg);
        }
    }
    
    public GoalAdaptiveResourceWeight(AdaptivePrefetchStrategy strategy, String type,
                                      double supply, Date goalTime) {
        this.strategy = strategy;
        this.type = type;
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

        logPrint(String.format("Old %s spending rate: %s   old supply: %s",
                               type, 
                               String.valueOf(spendingRate), 
                               String.valueOf(supply)));
        logPrint(String.format("current %s spent amount %s over past %s seconds",
                               type, 
                               String.valueOf(amount), 
                               String.valueOf(samplePeriod)));
        
        double rateSample = amount / samplePeriod;
        double alpha = smoothingFactor();
        spendingRate = (1 - alpha) * rateSample + alpha * spendingRate;
        supply -= amount;
        
        logPrint(String.format("New %s spending rate: %s   new supply: %s  (alpha %s)",
                               type, 
                               String.valueOf(spendingRate), 
                               String.valueOf(supply), 
                               String.valueOf(alpha)));
    }
    
    synchronized void forceUpdateWeight() {
        updateWeight();
    }
    
    public synchronized boolean supplyIsExhausted() {
        double adjustedSupply = computeAdjustedSupply();
        return (supply <= 0.0 || adjustedSupply <= 0.0);
    }
    
    private synchronized void updateWeight() {
        Date now = new Date();
        
        logPrint(String.format("Old %s weight: %s", type, String.valueOf(weight)));
        // "fudge factor" to avoid overshooting budget.  Borrowed from Odyssey.
        double adjustedSupply = computeAdjustedSupply();
        if (supply <= 0.0 || adjustedSupply <= 0.0) {
            weight = PROHIBITIVELY_LARGE_WEIGHT;
        } else if (now.after(goalTime)) {
            // goal reached; spend away!  (We shouldn't see this in our experiments.)
            // weight = 0.0;
            // on second thought, let's try to avoid spending like crazy at the end
            //  due to subtle timing issues.
            weight = PROHIBITIVELY_LARGE_WEIGHT;
        } else {
            double futureDemand = spendingRate * secondsUntil(goalTime);
            logPrint(String.format("Future %s demand: %s  weight %s  multiplier %s",
                                   type, 
                                   String.valueOf(futureDemand), 
                                   String.valueOf(weight), 
                                   String.valueOf(futureDemand / adjustedSupply)));
            weight *= (futureDemand / adjustedSupply);
        }
        weight = Math.max(weight, aggressiveNonZeroWeight());
        
        logPrint(String.format("New %s weight: %s", type, String.valueOf(weight)));
    }

    private static final double VARIABLE_BUFFER_WEIGHT = 0.05;
    //private static final double CONSTANT_BUFFER_WEIGHT = 0.01;
    
    // trying a higher constant factor because of the artificially short experiment.
    // This is based on taking 1% of a 2-hour experiment's starting supply.
    //  If X is the 15-minute supply and 8X is the 2-hour supply,
    //  then the fudge factor is 0.01 * 8X, or 0.08X.
    private static final double CONSTANT_BUFFER_WEIGHT = 0.08;

    // adjust the resource supply by an Odyssey-style "fudge factor" to 
    //  make it less likely we'll overshoot the budget.
    private double computeAdjustedSupply() {
        return supply - (VARIABLE_BUFFER_WEIGHT * supply + CONSTANT_BUFFER_WEIGHT * initialSupply);
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
