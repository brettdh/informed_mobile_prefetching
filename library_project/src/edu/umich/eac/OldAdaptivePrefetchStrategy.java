package edu.umich.eac;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.PriorityBlockingQueue;

import android.util.Log;

import edu.umich.eac.PrefetchStrategy;
import edu.umich.eac.FetchFuture;
import edu.umich.eac.WifiTracker.ConditionChange;
import edu.umich.eac.WifiTracker.Prediction;

/* Missing bits:
 * 1) How to estimate energy supply over a goal period
 *    - It's (energy_goal - energy_spending)
 * 2) Energy goal is given when cache is created.
 *    - ...for eval purposes. In a production system it would be based on 
 *      remaining battery capacity.
 * 3) ...so, how to estimate energy spending?
 *    - First, what's the trivial version of this?
 *      - Just leave it at zero. This means the only factor will be 
 *        the promotion rate of the cache.
 *    - Next: periodically sample current and voltage from sysfs and calculate 
 *      approximate energy consumed.
 *      - Problem: sysfs current measurement is useless when the power monitor
 *        is powering the phone, since no current goes through the battery.
 * 4) How to re-evaluate prefetch decisions later
 *    - Have a thread that monitors resource changes
 *      - It can also get woken up whenever a prefetch is promoted
 *    - That thread will also call the issuePrefetch or deferDecision methods.
 * 5) How to make IntNW do the right things based on my prefetch decisions
 *    - "Energy-conscious" label
 *      - Says "send this on the interface that will use the least energy to do so."
 *    - "Cost-conscious" label
 *      - Says "send this on the interface that has the least cost or least chance
 *        of incurring a cost."  i.e. wifi, not 3G
 *    - These only make sense for BG traffic, since FG traffic should always be sent
 *      on the network that can get it done sooner.
 *      
 *       
 * Another issue:
 *   Currently, a prefetch is either issued or deferred when it arrives.  Later,
 *   when the sampling thread wakes up, all deferred prefetches will be re-evaluated 
 *   in the order in which they were hinted. The effect of this is that a new prefetch
 *   that occurs at a favorable time might jump the entire queue of deferred prefetches.
 *   
 *   We could instead stick all prefetches in the queue and then only consider the decision
 *   (i.e. call what is now named handlePrefetch) for the first prefetch.  This implies the
 *   belief that a newer prefetch should never jump older prefetches, which are perhaps less
 *   likely to be promoted after they've been sitting for a while.  The application knows this,
 *   though, and cancel those prefetches.  So, we will assume that the prefetch order we get 
 *   from the application is good.
 *   
 *   However, we could tune this with the feedback loop as well.
 * 
 */
class OldAdaptivePrefetchStrategy extends PrefetchStrategy {
    private static final String TAG = OldAdaptivePrefetchStrategy.class.getName();

    // TODO: determine this from Android APIs
    private static final String CELLULAR_IFNAME = "rmnet0";
    
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
        public void reevaluate() {
            onPrefetchEnqueued(prefetch);
        }

        public int compareTo(PrefetchTask another) {
            return scheduledTime.compareTo(another.scheduledTime);
        }
    }

    private PriorityBlockingQueue<PrefetchTask> deferredPrefetches = 
        new PriorityBlockingQueue<PrefetchTask>();
    
    private Date mStartTime;
    private Date mGoalTime;
    private int mEnergyBudget;
    private int mDataBudget;
    
    private int mEnergySpent;
    private ProcNetworkStats mDataSpent;
    
    private MonitorThread monitorThread;
    
    @Override
    public void setup(Date goalTime, int energyBudget, int dataBudget) {
        mStartTime = new Date();
        mGoalTime = goalTime;
        mEnergyBudget = energyBudget;
        mDataBudget = dataBudget;
        mEnergySpent = 0;
        mDataSpent = new ProcNetworkStats(CELLULAR_IFNAME);
        
        monitorThread = new MonitorThread();
        monitorThread.start();
    }

    class MonitorThread extends Thread {
        @Override
        public void run() {
            final int SAMPLE_PERIOD_MS = 200;
            while (true) {
                // TODO: update energy stats
                
                mDataSpent.updateStats();
                
                List<PrefetchTask> prefetches = new ArrayList<PrefetchTask>();
                while (!deferredPrefetches.isEmpty()) {
                    prefetches.add(deferredPrefetches.remove());
                }
                
                for (PrefetchTask task : prefetches) {
                    task.reevaluate();
                }
                
                try {
                    Thread.sleep(SAMPLE_PERIOD_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
    
    @Override
    public void onPrefetchCancelled(FetchFuture<?> prefetch) {
        deferredPrefetches.remove(prefetch);
    }
    
    // deterministic for testing
    private Random prng = new Random(424242);

    public void onPrefetchEnqueued(FetchFuture<?> prefetch) {
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
         *     defer
         * 
         * if I have enough supply:
         *   if conditions will get better:
         *     lookup average prefetch->fetch delay
         *     window = predict(time until conditions get better)
         *     if average delay is less than window:
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
        
        EnergyAdaptiveCache cache = prefetch.getCache();
        double[] promotionDelay = cache.stats.getPromotionDelay();
        
        double probability = 1.0 - cache.stats.getPrefetchAccuracy();
        double decider = prng.nextDouble();
        if (decider < probability) {
            if (promotionDelay != null) {
                // until I get some promotion delay stats,
                //  bias towards issuing more prefetches
                //  (i.e. less deferring)
                long avgPromotionDelay = (long) promotionDelay[0];
                if (prefetch.millisSinceCreated() < avgPromotionDelay) {
                    deferDecision(prefetch);
                }
            }
        }
        
        Prediction prediction = wifiPredictor.predictConditionsChange();
        if (enoughSupply()) {
            if (prediction.change == ConditionChange.BETTER) {
                long delay;
                if (promotionDelay != null) {
                    delay = (long) promotionDelay[0];
                } else {
                    // as above, bias towards more prefetches
                    //  until I get some stats
                    delay = 0;
                }
                
                // if prefetch was deferred, discount this avg delay by
                // how much time this prefetch has been waiting
                delay -= prefetch.millisSinceCreated();
                // if this is negative, it means I might have waited too long
                
                if (delay < prediction.timeInFuture) {
                    issuePrefetch(prefetch);
                } else {
                    deferDecision(prefetch);
                }
            } else { // conditions will persist or get worse
                issuePrefetch(prefetch);
            }
        } else { // !enoughSupply()
            if (prediction.change == ConditionChange.WORSE) {
                if (cheaperToIssueNow(prefetch, prediction)) {
                    issuePrefetch(prefetch);
                } else {
                    deferDecision(prefetch);
                }
            } else {
                deferDecision(prefetch);
            }
        }
    }
    
    private WifiTracker wifiPredictor = new WifiTracker(null); // XXX
    
    private boolean cheaperToIssueNow(FetchFuture<?> prefetch, 
                                      Prediction prediction) {
        /* calculate shortfall = (predicted_demand - supply)
         *     calculate cost(send_it_in_worse_conditions) - cost(send_it_now)
         *               = savings(send_it_now)
         *     if savings(send_it_now) > shortfall:
         *       issue it now
         *     else:
         *       defer
         */
        // TODO: implement
        return false;
    }
    
    private double timeUntilGoal() {
        Date now = new Date();
        return (mGoalTime.getTime() - now.getTime()) / 1000.0;
    }
    
    private double timeSinceStart() {
        Date now = new Date();
        return (now.getTime() - mStartTime.getTime()) / 1000.0;
    }
    
    private synchronized double energyUsageRate() {
        return mEnergySpent / timeSinceStart();
    }
    
    private synchronized double dataUsageRate() {
        return mDataSpent.getTotalBytes() / timeSinceStart();
    }
    
    private boolean enoughSupply() {
        int energySupply = Math.max(0, mEnergyBudget - mEnergySpent);
        long dataSupply = Math.max(0, mDataBudget - mDataSpent.getTotalBytes());
        int predictedEnergyDemand = (int) (energyUsageRate() * timeUntilGoal());
        int predictedDataDemand = (int) (dataUsageRate() * timeUntilGoal());
        
        // Odyssey-style; fewer prefetches when supply is low
        int energyBuffer = (int) (mEnergyBudget * 0.01 + energySupply * 0.05);
        int dataBuffer = (int) (mDataBudget * 0.01 + dataSupply * 0.05);
        
        // TODO: refine?
        boolean enoughEnergy = (energySupply > (predictedEnergyDemand + 
                                                energyBuffer));

        boolean enoughData = true;
        if (!isWifiAvailable()) {
            enoughData = (dataSupply > (predictedDataDemand + 
                                        dataBuffer));
        }
        
        return enoughEnergy && enoughData;
    }

    private boolean isWifiAvailable() {
        String wifiDhcpStr = null;
        String[] cmds = new String[2];
        cmds[0] = "getprop";
        cmds[1] = "dhcp.eth0.result";
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(cmds);
            InputStreamReader in = new InputStreamReader(p.getInputStream());
            BufferedReader rdr = new BufferedReader(in);
            String line = null;
            while ((line = rdr.readLine()) != null) {
                if (line.trim().length() > 0) {
                    wifiDhcpStr = line;
                    break;
                }
            }
            rdr.close();
        } catch (IOException e) {
            if (p == null) {
                Log.e(TAG, String.format("Error: failed to exec '%s %s'",
                                         cmds[0], cmds[1]));
            } else {
                // ignore; wifi not available
            }
        }
        
        boolean wifiAvailable = false;
        if (wifiDhcpStr != null) {
            wifiAvailable = wifiDhcpStr.equals("ok");
        }
        return wifiAvailable;
    }
    
    void issuePrefetch(FetchFuture<?> prefetch) {
        deferredPrefetches.remove(prefetch);
        prefetch.addLabels(IntNWLabels.MIN_ENERGY);
        prefetch.addLabels(IntNWLabels.MIN_COST);
        prefetch.startAsync(false);
    }

    private void deferDecision(FetchFuture<?> prefetch) {
        PrefetchTask task = new PrefetchTask(prefetch);
        deferredPrefetches.add(task);
    }
}
