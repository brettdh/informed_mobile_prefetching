package edu.umich.eac;

import java.util.Date;
import java.util.PriorityQueue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import edu.umich.eac.PrefetchStrategy;
import edu.umich.eac.FetchFuture;

class AdaptivePrefetchStrategy extends PrefetchStrategy {
    class PrefetchTask implements Comparable<PrefetchTask> {
        private Date scheduledTime;
        private FetchFuture<?> prefetch;
        
        /** 
         * Schedule the prefetch for this many milliseconds in the future. 
         */
        PrefetchTask(FetchFuture<?> pf) {
            prefetch = pf;
            scheduledTime = new Date();
        }
        public void run() {
            prefetch.addToPrefetchQueue();
        }

        public int compareTo(PrefetchTask another) {
            return scheduledTime.compareTo(another.scheduledTime);
        }
    }

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
         * # try to only spend resources on the fraction of prefetches
         * #  that are likely to be promoted
         * with probability(1.0 - prefetch promotion rate):
         *   if time_since_app_hinted_prefetch < avg prefetch->fetch delay:
         *   defer
         * 
         * if I have enough supply:
         *   if conditions will get better:
         *     lookup average prefetch->fetch delay
         *     window = predict(time until conditions get better)
         *     if average delay is less than time until conditions get better:
         *       issue it now
         *     else:
         *       defer
         *   else: # conditions will persist or get worse
         *     issue it now
         * else:
         *   if conditions will get worse:
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
         * Breaking down what this means a bit:
         * 1) "Enough supply" means that supply exceeds predicted demand
         *    by at least the computed threshold (Odyssey-style; 
         *    SOSP '99, Section 5.1.3, "Triggering adaptation")
         * 2) "Conditions will [change somehow]" refers to a prediction
         *    about network availability and quality, for a certain window 
         *    into the future, that might change my current decision of 
         *    whether to prefetch or not.
         * 
         * Ways in which I get new information (and when I get it):
         * 1) New energy/data samples (fixed sampling interval)
         * 2) New prediction information (fixed sampling interval)
         *    - Using this at a short sampling interval will require the
         *      partial re-implementation in C
         * 3) New AP information (when connected)
         * 4) Stats updated by Future interface (when application uses it)
         * 
         * Things I can learn from the Future interface:
         * 1) Cache hit rate
         *    -Two kinds of cache miss:
         *      a) Fetches that the app didn't hint
         *         (or perhaps hinted and then immediately fetched)
         *      b) Prefetches that I haven't promoted yet
         *    -Lots of (a) misses indicates the app isn't hinting well
         *    -Lots of (b) misses indicates I'm not issuing prefetches
         *     soon enough
         *    -All other things being equal, issue fewer prefetches
         *     if (a) is lower and more prefetches if (b) is lower
         *    -Initialize to 0.0 to force prefetches at first
         *    -Update when future->get() is called
         *    -Not quite sure how to incorporate (a)
         *    -The prefetch->fetch delay tracking may take care of (b)
         * 2) Prefetch->fetch delay
         *    -Tells me whether I'm issuing prefetches too early / too late
         *    -Goal: not too late, just a little early.  
         *           Use feedback to make this settle.
         *    -Keep avg, stddev; update when future->get() is called
         * 3) Prefetch promotion rate
         *    - Percentage of prefetches that are actually fetched
         *    - All other things being equal, issue fewer prefetches
         *      if this percentage is low 
         *    - This and hit rate are the utility of the cache.
         *    - How to track this:
         *      - Keep as stable EWMA (init 1.0)
         *        - Biases towards more prefetches
         *      - Start per-prefetch timer when prefetch() is called
         *      - Update rate if future->get() is called
         *      - Consider a prefetch not promoted after time greater
         *        than (avg prefetch->fetch delay + stddev) has passed
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
        
        // TODO: implement the version that we converge on.
        
        if (shouldPrefetchNow() && enoughSupply()) {
            issuePrefetch(prefetch);
        } else if (shouldPrefetchNow() && !enoughSupply()) {
            deferDecision(prefetch);
        } else if (!shouldPrefetchNow() && enoughSupply()) {
            issuePrefetch(prefetch);
        } else {
            deferDecision(prefetch);
        }
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
    
    private synchronized double sampledEnergyUsage() {
        return mSampledEnergyUsage;
    }
    
    private synchronized double sampledDataUsage() {
        return mSampledDataUsage;
    }
    
    private boolean enoughSupply() {
        // TODO: implement
        int energySupply = mEnergyBudget - mEnergySpent;
        int dataSupply = mDataBudget - mDataSpent;
        int predictedEnergyDemand = (int) (sampledEnergyUsage() * timeUntilGoal());
        int predictedDataDemand = (int) (sampledDataUsage() * timeUntilGoal());
        
        // Odyssey-style; fewer prefetches when supply is low
        int energyBuffer = (int) (mEnergyBudget * 0.01 + energySupply * 0.05);
        int dataBuffer = (int) (mDataBudget * 0.01 + dataSupply * 0.05);
        
        // TODO: refine?
        boolean enoughEnergy = (energySupply > (predictedEnergyDemand + 
                                                energyBuffer));
        boolean wifiAvailable = false;
        // TODO: check whether wifi is available
        
        boolean enoughData = true;
        if (!wifiAvailable) {
            enoughData = (dataSupply > (predictedDataDemand + 
                                        dataBuffer));
        }
        
        return enoughEnergy && enoughData;
    }
    
    private void deferDecision(FetchFuture<?> prefetch) {
        PrefetchTask task = new PrefetchTask(prefetch);
        deferredPrefetches.add(task);
    }
}
