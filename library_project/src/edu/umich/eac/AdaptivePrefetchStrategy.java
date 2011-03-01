package edu.umich.eac;

import java.util.Date;
import java.util.PriorityQueue;
import java.util.Timer;
import java.util.TimerTask;

import edu.umich.eac.PrefetchStrategy;
import edu.umich.eac.FetchFuture;

class AdaptivePrefetchStrategy extends PrefetchStrategy {
    class PrefetchTask extends TimerTask implements Comparable<PrefetchTask> {
        private Date scheduledTime;
        private FetchFuture<?> prefetch;
        
        /** 
         * Schedule the prefetch for this many milliseconds in the future. 
         */
        PrefetchTask(FetchFuture<?> pf, int millis) {
            prefetch = pf;
            schedule(millis);
        }
        public void run() {
            prefetch.addToPrefetchQueue();
        }
        
        void schedule(int millis) {
            scheduledTime = new Date(System.currentTimeMillis() + millis);
        }

        public int compareTo(PrefetchTask another) {
            return scheduledTime.compareTo(another.scheduledTime);
        }
    }
    private Timer timer = new Timer();
    private PriorityQueue<PrefetchTask> deferredPrefetches = 
        new PriorityQueue<PrefetchTask>();
    
    private Date mStartTime;
    private Date mGoalTime;
    private int mEnergyBudget;
    private int mDataBudget;
    
    private int mEnergySpent;
    private int mDataSpent;
    
    // these are sampled rates of spending.
    private double mSampledEnergyUsage; // Joules/sec (Watts)
    private double mSampledDataUsage;   // bytes/sec
    
    @Override
    public void setup(Date goalTime, int energyBudget, int dataBudget) {
        mStartTime = new Date();
        mGoalTime = goalTime;
        mEnergyBudget = energyBudget;
        mDataBudget = dataBudget;
        mEnergySpent = 0;
        mDataSpent = 0;
        
        mSampledEnergyUsage = 0;
        mSampledDataUsage = 0;
        
        // TODO: start monitoring thread?
    }
    
    public void handlePrefetch(FetchFuture<?> prefetch) {
        /* Possible actions to do with a prefetch:
         * a) Issue it now
         * b) Defer it until I get new information
         * 
         * How to pick between these?
         * 
         * if I have enough supply:
         *   if the time is right:
         *     issue it now
         *   else:
         *     lookup average prefetch->fetch delay
         *     window = predict(time until conditions get better)
         *     if average delay is less than time until conditions get better:
         *       issue it now
         *     else:
         *       defer
         * else:
         *   if the time is right:
         *     #  One possible scenario here is:
         *     #  - I'm going to lose the wifi soon
         *     #  - Sometime after I lose the wifi, my resource supply
         *     #     will catch up with the predicted demand
         *     #  - The resource budget tells me that I should defer, but...
         *     #  - ...deferring will actually use more energy
         *     #  - So, I should (maybe) do it now
         *     # How to detect this: 
         *     calculate shortfall = (predicted_demand - supply)
         *     calculate cost(send_it_in_worse_conditions) - cost(send_it_now)
         *               = savings(send_it_now)
         *     if savings(send_it_now) > shortfall:
         *       issue it now
         *     else:
         *       defer
         *   else: # conditions aren't improving, don't have enough supply
         *     defer
         * 
         * 
         * Ways in which I get new information:
         * 1) New energy/data samples (fixed sampling interval)
         * 2) New prediction information (fixed sampling interval)
         *    - Using this at a short sampling interval will require the
         *      re-implementation in C
         * 3) New AP information from Breadcrumbs (when connected)
         * 
         * Things I can learn from the Future interface:
         * 1) Cache hit rate
         *    -All other things being equal, issue more prefetches
         *     if hit rate is lower
         * 2) Prefetch->fetch delay
         *    -Tells me whether I'm issuing prefetches too early / too late
         *    -Goal: not too late, just a little early.  
         *           Use feedback to make this settle.
         * 3) Percentage of prefetches that are actually fetched
         *    - All other things being equal, issue fewer prefetches
         *      if this percentage is low 
         *    - This and hit rate are the utility of the cache.
         * 
         * 
         * ---------------
         *  First attempt
         *  
         * if the time is right and I have enough supply:
         *   issue it now
         * else if the time is right but I don't have enough supply:
         *   compute the earliest possible time that I'll have enough
         *   reschedule decision for then
         * else if the time's not right but I have enough supply:
         *   issue it now
         * else if the time's not right and I don't have enough supply:
         *   reschedule for earliest time that I might have enough supply
         * 
         * 
         * Breaking down what this means a bit:
         * 1) "Enough supply" means that supply exceeds predicted demand
         *    by at least the computed threshold (Odyssey-style; 
         *    SOSP '99, Section 5.1.3, "Triggering adaptation")
         * 2) "The time is right" means that I currently have the best
         *    network conditions, for the foreseeable future, to minimize
         *    resource usage and fetch latency
         * 3) "The earliest possible time that I'll have enough"
         *    means the time when, if resource demand dropped to the
         *    baseline starting now, the supply would exceed the
         *    predicted demand by at least the computed threshold.
         * ---------------
         *
         * For the time being, supply and demand will be measured in percent of
         * full battery capacity rather than Joules, since the G1 doesn't
         * provide current measurements on the battery. For future reference,
         * the Nexus One does provide current measurements.
         * Alternatively, I might be able to get these measurements from the 
         * PowerTutor power monitor by parsing its ongoing log file.
         * EDIT: this is looking less and less likely without the source code.
         *       The log file can't be parsed on-line, since it's generated
         *       as a compressed stream, so it doesn't get written out in
         *       real time.  I could listen for widget update broadcasts,
         *       but I haven't been able to get that working yet.
         *       Plan: just get something working first.
         * 
         * The relation to the Odyssey energy adaptation seems fairly direct,
         * if fetch response time is our analog to fidelity.
         * 
         */
        
        if (shouldPrefetchNow() && enoughSupply()) {
            issuePrefetch(prefetch);
        } else if (shouldPrefetchNow() && !enoughSupply()) {
            int millis = computeRescheduleTime();
            rescheduleDecision(prefetch, millis);
        } else if (!shouldPrefetchNow() && enoughSupply()) {
            prefetch.startAsync(false);
        } else {
            int millis = computeRescheduleTime();
            rescheduleDecision(prefetch, millis);
        }
    }
    
    /**
     * Compute the time in the future at which this prefetch decision
     * should be reconsidered.
     * @return amount of time in milliseconds.
     */
    private int computeRescheduleTime() {
        // TODO: implement
        return 1000;
    }

    void issuePrefetch(FetchFuture<?> prefetch) {
        deferredPrefetches.remove(prefetch);
        prefetch.startAsync(false);
    }
    
    private boolean shouldPrefetchNow() {
        // TODO: implement
        return true;
    }
    
    private double timeUntilGoal() {
        Date now = new Date();
        return (mGoalTime.getTime() - now.getTime()) / 1000.0;
    }
    
    private double sampledEnergyUsage() {
        /*
        synchronized (mSampledEnergyUsage) {
            return mSampledEnergyUsage;
        }
        */
        return 0.0;
    }
    
    private double sampledDataUsage() {
        /*
        synchronized (mSampledDataUsage) {
            return mSampledDataUsage;
        }
        */
        return 0.0;
    }
    
    private boolean enoughSupply() {
        // TODO: implement
        /*
        int energySupply = mEnergyBudget - mEnergySpent;
        int dataSupply = mDataBudget - mDataSpent;
        int predictedEnergyDemand = (int) (sampledEnergyUsage() * timeUntilGoal());
        int predictedDataDemand = (int) (sampledDataUsage() * timeUntilGoal());
        */
        
        return true;
    }
    
    private void rescheduleDecision(FetchFuture<?> prefetch, 
                                    int millisInFuture) {
        PrefetchTask task = new PrefetchTask(prefetch, millisInFuture);
        deferredPrefetches.add(task);
        timer.schedule(task, millisInFuture);
    }
}
