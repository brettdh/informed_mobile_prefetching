package edu.umich.eac;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math.optimization.GoalType;

import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;
import edu.umich.eac.AdaptivePrefetchStrategy.PrefetchTask;
import edu.umich.eac.WifiTracker.ConditionChange;
import edu.umich.eac.WifiTracker.Prediction;
import edu.umich.libpowertutor.EnergyEstimates;
import edu.umich.libpowertutor.EnergyUsage;

public class AdaptivePrefetchStrategy extends PrefetchStrategy {
    static final String TAG = AdaptivePrefetchStrategy.class.getName();

    // TODO: determine this from Android APIs
    private static final String CELLULAR_IFNAME = "rmnet0";
    
    private static int nextOrder = 0;
    
    static class PrefetchTask implements Comparable<PrefetchTask> {
        private Date scheduledTime;
        FetchFuture<?> prefetch;
        private int order;
        
        /** 
         * Schedule the prefetch for this many milliseconds in the future. 
         */
        PrefetchTask(FetchFuture<?> pf) {
            prefetch = pf;
            scheduledTime = new Date();
            synchronized(PrefetchTask.class) {
                order = ++nextOrder;
            }
        }

        public int compareTo(PrefetchTask another) {
            if (prefetch.equals(another.prefetch)) {
                return 0;
            }
            double myAccuracy = prefetch.getCache().stats.getHardcodedPrefetchAccuracy(prefetch);
            double yourAccuracy = another.prefetch.getCache().stats.getHardcodedPrefetchAccuracy(another.prefetch);
            if (Math.abs(myAccuracy - yourAccuracy) > 0.001) {
                // sort in descending order by accuracy
                if (myAccuracy > yourAccuracy) {
                    // higher accuracy; I'm first
                    return -1;
                } else if (myAccuracy < yourAccuracy) {
                    // lower accuracy; you're first
                    return 1;
                }
            }
            // all prefetch accuracies being equal, preserve hint order
            return order - another.order;
        }

        public void reset() {
            prefetch.reset();
        }
        
        public String toString() {
            StringBuffer buffer = new StringBuffer();
            //double myAccuracy = prefetch.getCache().stats.getPrefetchAccuracy(prefetch);
            double myAccuracy = prefetch.getCache().stats.getHardcodedPrefetchAccuracy(prefetch);
            buffer.append("PrefetchTask: ").append("class ").append(prefetch.getPrefetchClass())
                  .append(" accuracy ").append(myAccuracy)
                  .append(" fetcher: ").append(prefetch.toString());
            return buffer.toString();
        }
    }

    private PriorityBlockingQueue<PrefetchTask> deferredPrefetches = 
        new PriorityBlockingQueue<PrefetchTask>();
    
    private static final int NUM_CONCURRENT_PREFETCHES = 1;

    private static NetworkStats HARDCODED_INITIAL_THREEG_STATS = null;
    private BlockingQueue<PrefetchTask> prefetchesInProgress = 
        new ArrayBlockingQueue<PrefetchTask>(NUM_CONCURRENT_PREFETCHES);
    
    private static boolean fixedAdaptiveParamsEnabled = false;
    private static double fixedEnergyWeight;
    private static double fixedDataWeight;
    
    private GoalAdaptiveResourceWeight energyWeight;
    private GoalAdaptiveResourceWeight dataWeight;
    
    private int mLastEnergySpent; // in mJ
    private EnergyUsage energyUsage;
    private ProcNetworkStats mDataSpent;
    
    // These map TYPE_WIFI or TYPE_MOBILE to the network stats.
    private Map<Integer, NetworkStats> currentNetworkStats;
    private AverageNetworkStats averageNetworkStats;
    
    private PassiveThreegEstimate threegEstimate = new PassiveThreegEstimate();
    
    private MonitorThread monitorThread;
    
    @Override
    public void setup(Context context, Date goalTime, double energyBudget, int dataBudget) {
        super.setup(context, goalTime, energyBudget, dataBudget);
        
        this.context = context;
        
        long millisUntilGoal = goalTime.getTime() - System.currentTimeMillis();
        logPrint(String.format("Setup adaptive strategy with energy budget %.3f%% data budget %d bytes  goal %d ms from now",
                               energyBudget, dataBudget, millisUntilGoal));
        
        double energyBudgetJoules = EnergyEstimates.convertBatteryPercentToJoules(energyBudget);
        energyWeight = new GoalAdaptiveResourceWeight(this, "energy", energyBudgetJoules, goalTime);
        dataWeight = new GoalAdaptiveResourceWeight(this, "data", dataBudget, goalTime);
        
        mLastEnergySpent = 0;
        energyUsage = new EnergyUsage();
        mDataSpent = new ProcNetworkStats(CELLULAR_IFNAME);
        
        // set from initial values in trace
        HARDCODED_INITIAL_THREEG_STATS = new NetworkStats();
//        HARDCODED_INITIAL_THREEG_STATS.bandwidthDown = 51196;
//        HARDCODED_INITIAL_THREEG_STATS.bandwidthUp = 1497;
//        HARDCODED_INITIAL_THREEG_STATS.rttMillis = 207;
        
        // walking trace 1
        HARDCODED_INITIAL_THREEG_STATS.bandwidthDown = 66052; //test
        HARDCODED_INITIAL_THREEG_STATS.bandwidthUp = 6792;
        HARDCODED_INITIAL_THREEG_STATS.rttMillis = 143;

        // walking trace 1, reversed
//        HARDCODED_INITIAL_THREEG_STATS.bandwidthDown = 87620;
//        HARDCODED_INITIAL_THREEG_STATS.bandwidthUp = 18924;
//        HARDCODED_INITIAL_THREEG_STATS.rttMillis = 115;

        currentNetworkStats = NetworkStats.getAllNetworkStats(context);
        currentNetworkStats.put(ConnectivityManager.TYPE_MOBILE, HARDCODED_INITIAL_THREEG_STATS);
        averageNetworkStats = new AverageNetworkStats();
        averageNetworkStats.initialize(currentNetworkStats);
        
        monitorThread = new MonitorThread();
        monitorThread.start();
    }
    
    @Override
    public void updateGoalTime(Date newGoalTime) {
        energyWeight.updateGoalTime(newGoalTime);
        dataWeight.updateGoalTime(newGoalTime);
    }
    
    public static void setStaticParams(double energyWeight, double dataWeight) {
        fixedAdaptiveParamsEnabled = true;
        fixedEnergyWeight = energyWeight;
        fixedDataWeight = dataWeight;
    }

    private Date lastResourceStatsUpdate = new Date();
    private Context context;
    
    private synchronized void updateStats() {
        if (System.currentTimeMillis() - lastResourceStatsUpdate.getTime() > 1000) {
            updateEnergyStats();
            updateDataStats();
            lastResourceStatsUpdate = new Date();
        }
        updateNetworkStats();
    }

    private synchronized void updateDataStats() {
        long dataSpent = mDataSpent.getTotalBytes();
        mDataSpent.updateStats();
        long dataSpentRecently = mDataSpent.getTotalBytes() - dataSpent;
        dataWeight.reportSpentResource(dataSpentRecently);
    }

    private synchronized void updateEnergyStats() {
        int energySpent = energyUsage.energyConsumed();
        double newEnergySpent = energySpent - mLastEnergySpent;
        mLastEnergySpent = energySpent;
        energyWeight.reportSpentResource(newEnergySpent / 1000.0);
    }

    private synchronized void updateNetworkStats() {
        Map<Integer, NetworkStats> newStats = NetworkStats.getAllNetworkStats(context);
//        for (Integer type : currentNetworkStats.keySet()) {
//            NetworkStats newStats = currentNetworkStats.get(type);
//            averageNetworkStats.add(type, newStats);
//        }
        // Only update wifi, because the 3G measurement is stale.
        if (newStats.containsKey(ConnectivityManager.TYPE_WIFI)) {
            NetworkStats wifiStats = newStats.get(ConnectivityManager.TYPE_WIFI);
            currentNetworkStats.put(ConnectivityManager.TYPE_WIFI, wifiStats);
            averageNetworkStats.add(ConnectivityManager.TYPE_WIFI, wifiStats);
        }
    }

    class MonitorThread extends Thread {
        BlockingQueue<PrefetchTask> tasksToEvaluate = 
            new LinkedBlockingQueue<PrefetchTask>();
        
        @Override
        public void run() {
            final int SAMPLE_PERIOD_MS = 200;
            
            while (true) {
                try {
                    updateStats();
                    reevaluateAllDeferredPrefetches();
                    waitForWakeup(SAMPLE_PERIOD_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        private synchronized void waitForWakeup(long waitMillis) throws InterruptedException {
            wait(waitMillis);
        }

        private void reevaluateAllDeferredPrefetches() throws InterruptedException {
            PrefetchTask firstFetch = prefetchesInProgress.peek();
            if (firstFetch != null && cannotComplete(firstFetch)) {
                logPrint(String.format("Prefetch 0x%08x was interrupted; re-deferring",
                                       firstFetch.prefetch.hashCode()));
                prefetchesInProgress.remove(firstFetch);
                firstFetch.reset();
                deferDecision(firstFetch);
                return;
            }
            
            if (prefetchesInProgress.remainingCapacity() == 0) {
                // too many prefetches in progress; defer
                logPrint(String.format("%d prefetches outstanding (first is 0x%08x); deferring", 
                        prefetchesInProgress.size(), 
                        firstFetch == null ? 0 : firstFetch.prefetch.hashCode()));
                return;
            }

            // TODO: check whether we have new information to prompt re-evaluation?
            
            deferredPrefetches.drainTo(tasksToEvaluate);
            if (tasksToEvaluate.isEmpty()) {
                logPrint("No prefetches queued");
                return;
            }
            logPrint(String.format("Reevaluating %d deferred prefetches", tasksToEvaluate.size()));

            PrefetchBatch batch = new PrefetchBatch();
            while (!tasksToEvaluate.isEmpty()) {
                batch.addPrefetch(tasksToEvaluate.remove());
                if (batch.size() > 1) {
                    logPrint(String.format("Evaluating prefetch 0x%08x batched with %d others", 
                                           batch.first().prefetch.hashCode(), batch.size() - 1));
                } else {
                    logPrint(String.format("Evaluating prefetch 0x%08x", batch.first().prefetch.hashCode()));
                }
                if (alreadyIssued(batch.first())) {
                    batch.pop();
                    continue;
                }
                if (shouldIssuePrefetch(batch)) {
                    if (!wifiTracker.isWifiAvailable()) {
                        threegEstimate.beginEstimation(batch.first().prefetch);
                    }
                    issuePrefetch(batch.first());
                    batch.pop();
                    break;
                }
            }
            
            // add back any tasks that I didn't issue; they'll go to 
            //  their original place in the queue.
            batch.drainTo(deferredPrefetches);
            tasksToEvaluate.drainTo(deferredPrefetches);
        }

        private boolean cannotComplete(PrefetchTask firstFetch) {
            if (firstFetch.prefetch.hasLabels(IntNWLabels.WIFI_ONLY) &&
                !wifiTracker.isWifiAvailable()) {
                return true;
            } // else: should handle the 3G-only case too, but it's unlikely
            return false;
        }

        void removeTask(FetchFuture<?> prefetch) {
            removePrefetchFromList(tasksToEvaluate, prefetch);
            removePrefetchFromList(deferredPrefetches, prefetch);
        }

        synchronized void wakeup() {
            notify();
        }
    }

    @Override
    public void onDemandFetch(FetchFuture<?> prefetch) {
        logPrint(String.format("Demand fetch arrived for fetcher 0x%08x; removing its prefetch",
                               prefetch.hashCode()));
        PrefetchTask dummy = new PrefetchTask(prefetch);
        //prefetchesInProgress.remove(dummy);
        removePrefetchFromList(prefetchesInProgress, prefetch);
        monitorThread.removeTask(prefetch);
        monitorThread.wakeup();
    }
    
    @Override
    public void onPrefetchDone(FetchFuture<?> prefetch, boolean cancelled) {
        logPrint(String.format("Prefetch %s for fetcher 0x%08x",
                 cancelled ? "cancelled" : "done", prefetch.hashCode()));
        if (cancelled) {
            monitorThread.removeTask(prefetch);
        }
        removePrefetchFromList(prefetchesInProgress, prefetch);
        monitorThread.wakeup();
        
        int threegBandwidthEstimate = threegEstimate.getBandwidthAfterPrefetchDone(prefetch);
        if (threegBandwidthEstimate > 0) {
            updateThreegBandwidthDown(threegBandwidthEstimate);
        }
    }
    
    private /*synchronized*/ void updateThreegBandwidthDown(int threegBandwidthEstimate) {
//        int threegType = ConnectivityManager.TYPE_MOBILE;
//        NetworkStats newStats = currentNetworkStats.get(threegType);
//        newStats.bandwidthDown = threegBandwidthEstimate;
//        currentNetworkStats.put(threegType, newStats);
//        averageNetworkStats.add(threegType, newStats);
        
        // These can be very very wrong sometimes, and that affects cost calculations badly.
        //   i.e. when I have wifi, I think the prefetch will have astronomical cost on average,
        //   so of course I must prefetch it right now.
        logPrint(String.format("New passive 3G bandwidth-down estimate: %d bytes/sec (not using; just logging)", threegBandwidthEstimate));
//        logPrint(String.format("New avg 3G bandwidth-down value: %d bytes/sec", 
//                               averageNetworkStats.get(threegType).bandwidthDown));
                               
    }

    private void removePrefetchFromList(BlockingQueue<PrefetchTask> prefetches, FetchFuture<?> prefetch) {
        PrefetchTask victim = null;
        for (PrefetchTask task : prefetches) {
            if (task.prefetch.equals(prefetch)) {
                victim = task;
                break;
            }
        }
        if (victim != null) {
            prefetches.remove(victim);
        }
    }

    @Override
    public void onPrefetchEnqueued(FetchFuture<?> prefetch) {
        // The prefetch will get evaluated the next time around the loop.
        //  Queue instead of calling issuePrefetch so that
        //  issuePrefetch never hangs on prefetchesInProgress.offer();
        //  otherwise there's a race with
        //  prefetchesInProgress.remainingCapacity()
        deferDecision(new PrefetchTask(prefetch));
        if (deferredPrefetches.size() == 1) {
            monitorThread.wakeup();
        }
    }

    private boolean shouldIssuePrefetch(PrefetchBatch batch) {
        updateNetworkStats();

        /* if wifiCost < benefit and threegCost > benefit,
         *   issue prefetch as wifi-preferred.
         * if wifiCost > benefit and threegCost < benefit (less likely),
         *   issue prefetch as 3G-preferred.
         * if wifiCost < benefit and threegCost < benefit, 
         *   this is the uncertain point.  Could either issue it with no preference 
         *   or with a preference based on which cost is lower.
         * If we issue it with no preference, we should really be calculating the cost
         *   of doing the prefetch on both networks - but it's probably smaller than
         *   the cost of doing it on 3G.
         * Jason's advice: pick one, document it, and make it easy to change.
         *   (i.e. toggling one line of code)
         *   This is the place to do that.
         */
        Double cost = null;
        
        if (alreadyIssued(batch.first())) {
            // caller will "issue" the prefetch by realizing that it's already done 
            //  and removing it from the queue.
            logPrint(String.format("Already issued prefetch 0x%x; will remove it", batch.first().prefetch.hashCode()));
            return true;
        }
        
        Double threegCost = calculateCost(batch, ConnectivityManager.TYPE_MOBILE);
        Double benefit = calculateBenefit(batch);

        batch.first().prefetch.clearLabels(IntNWLabels.ALL_NET_RESTRICTION_LABELS);
        if (wifiTracker.isWifiAvailable()) {
            Double wifiCost = calculateCost(batch, ConnectivityManager.TYPE_WIFI);

            // here's a conservative should-I-stripe-it calculation,
            //  if we want to try that later
            /*
            if ((wifiCost + threegCost) < benefit) {
                cost = wifiCost = threegCost;
                // no label preference; default to striping
            } else
            */
            if (wifiCost < benefit && threegCost < benefit) {
                batch.first().prefetch.addLabels(IntNWLabels.WIFI_ONLY);
                cost = wifiCost;
            } else if (wifiCost < benefit && threegCost > benefit) {
                batch.first().prefetch.addLabels(IntNWLabels.WIFI_ONLY);
                cost = wifiCost;
            } else if (wifiCost > benefit && threegCost < benefit) {
                batch.first().prefetch.addLabels(IntNWLabels.THREEG_ONLY);
                cost = threegCost;
            } else {
                // cost is too high; will defer either way
                cost = wifiCost;
            }
        } else {
            cost = threegCost;
            // don't set a preference, so as to allow use of wifi if it appears
        }
        
        final boolean shouldIssuePrefetch = cost < benefit;
        
        logPrint(String.format("Cost = %s; benefit = %s; %s prefetch 0x%08x", 
                               cost.toString(), benefit.toString(),
                               shouldIssuePrefetch ? "issuing" : "deferring",
                               batch.first().prefetch.hashCode()));
        return shouldIssuePrefetch;
    }
    
    private static double PROHIBITIVE_ENERGY_COST = 1.071 * Math.pow(10, 20); // U.S. energy consumption, 2007
    private static double PROHIBITIVE_DATA_COST = 200 * Math.pow(2, 40); // 200TB, about the size of Google's index
    
    private double calculateCost(PrefetchBatch batch, int netType) {
        double energyCostNow = currentEnergyCost(batch, netType);
        double dataCostNow = currentDataCost(batch, netType);
        
        double duration = currentPrefetchDuration(batch, netType);
        double energyWeight = calculateEnergyWeight(energyCostNow, duration);
        double dataWeight = calculateDataWeight(dataCostNow, duration);
        
        double energyCostFuture = averageEnergyCost(batch);
        double dataCostFuture = averageDataCost(batch);
        
        //double hintAccuracy = batch.first().prefetch.getCache().stats.getPrefetchAccuracy(batch.first().prefetch);
        double hintAccuracy = batch.first().prefetch.getCache().stats.getHardcodedPrefetchAccuracy(batch.first().prefetch);
        double energyCostDelta = energyCostNow - (hintAccuracy * energyCostFuture);
        double dataCostDelta = dataCostNow - (hintAccuracy * dataCostFuture);
        
        double weightedEnergyCost = energyWeight * energyCostDelta;
        double weightedDataCost = dataWeight * dataCostDelta;
        double totalCost = weightedEnergyCost + weightedDataCost;
        logCost("Energy", energyCostNow, energyCostFuture, hintAccuracy, energyCostDelta,
                energyWeight, weightedEnergyCost);
        logCost("Data", dataCostNow, dataCostFuture, hintAccuracy, dataCostDelta, 
                dataWeight, weightedDataCost);
        logPrint(String.format("Total cost: %s", String.valueOf(totalCost)));
        return totalCost;
    }

    private double currentPrefetchDuration(PrefetchBatch batch, int netType) {
        NetworkStats stats = null;
        if (netType == ConnectivityManager.TYPE_MOBILE) {
            stats = currentNetworkStats.get(ConnectivityManager.TYPE_MOBILE);
        } else {
            stats = currentNetworkStats.get(ConnectivityManager.TYPE_WIFI);
            if (stats == null) {
                // spending rate will be high, wifi cost will be higher
                return 1;
            }
        }
        return batch.estimateFetchTime(stats.bandwidthDown, stats.bandwidthUp, stats.rttMillis);
    }

    private void logCost(String type, 
                         double costNow, double costFuture, double hintAccuracy, double costDelta,
                         double weight, double weightedCost) {
        logPrint(String.format("%s cost:  now %s later %s hint accuracy %s delta %s weight %s  weighted cost %s",
                               type,
                               String.valueOf(costNow), String.valueOf(costFuture),
                               String.valueOf(hintAccuracy),
                               String.valueOf(costDelta), String.valueOf(weight),
                               String.valueOf(weightedCost)));
    }

    private double calculateEnergyWeight(double prefetchCost, double prefetchDuration) {
        if (fixedAdaptiveParamsEnabled) {
            return fixedEnergyWeight;
        } else {
            return energyWeight.getWeight("energy", prefetchCost, prefetchDuration);
        }
    }

    private double calculateDataWeight(double prefetchCost, double prefetchDuration) {
        if (fixedAdaptiveParamsEnabled) {
            return fixedDataWeight;
        } else {
            return dataWeight.getWeight("data", prefetchCost, prefetchDuration);
        }
    }
    
    private double currentEnergyCost(PrefetchBatch batch, int netType) {
        if (energyWeight.supplyIsExhausted()) {
            return PROHIBITIVE_ENERGY_COST;
        }
        
        int datalen = batch.bytesToTransfer();
        double energyCost;
        if (netType == ConnectivityManager.TYPE_MOBILE) {
            NetworkStats mobileStats = currentNetworkStats.get(ConnectivityManager.TYPE_MOBILE);
            logPrint(String.format("Calculating energy cost on 3G... net estimates: bw_down %d bw_up %d rtt %d",
                                   mobileStats.bandwidthDown, mobileStats.bandwidthUp, mobileStats.rttMillis));
            energyCost = 
                EnergyEstimates.estimateMobileEnergyCost(datalen, 
                                                         mobileStats.bandwidthDown,
                                                         mobileStats.rttMillis);
        } else {
            NetworkStats wifiStats = currentNetworkStats.get(ConnectivityManager.TYPE_WIFI);
            if (wifiStats == null) {
                // should always have the average stats.  fall back on those
                wifiStats = averageNetworkStats.get(ConnectivityManager.TYPE_WIFI);
                if (wifiStats == null) {
                    // shouldn't happen in my experiments.
                    return PROHIBITIVE_ENERGY_COST;
                }
            }
            logPrint(String.format("Calculating energy cost on wifi... net estimates: bw_down %d bw_up %d rtt %d",
                                   wifiStats.bandwidthDown, 
                                   wifiStats.bandwidthUp,
                                   wifiStats.rttMillis));
            energyCost =
                EnergyEstimates.estimateWifiEnergyCost(datalen, 
                                                       wifiStats.bandwidthDown,
                                                       wifiStats.rttMillis);
        } 
        
        return energyCost / 1000.0; // mJ to J
    }
    
    private double averageEnergyCost(PrefetchBatch batch) {
        int datalen = batch.bytesToTransfer();
        NetworkStats wifiStats = averageNetworkStats.get(ConnectivityManager.TYPE_WIFI);
        NetworkStats mobileStats = averageNetworkStats.get(ConnectivityManager.TYPE_MOBILE);

        logPrint(String.format("Calculating average energy cost"));

        double mobileEnergyCost = 
            EnergyEstimates.estimateMobileEnergyCostAverage(datalen, 
                                                            mobileStats.bandwidthDown,
                                                            mobileStats.rttMillis);
        logPrint(String.format("  avg cost on 3G: %f mJ avg net estimates: bw_down %d bw_up %d rtt %d",
                                mobileEnergyCost,
                                mobileStats.bandwidthDown,
                                mobileStats.bandwidthUp, 
                                mobileStats.rttMillis));

        if (wifiStats == null) {
            return mobileEnergyCost / 1000.0;
        }
        
        double wifiAvailability = wifiTracker.availability();
        double wifiEnergyCost = 
            EnergyEstimates.estimateWifiEnergyCost(datalen, 
                                                   wifiStats.bandwidthDown,
                                                   wifiStats.rttMillis);
        
        logPrint(String.format("  avg cost on wifi: %f mJ avg net estimates: bw_down %d bw_up %d rtt %d",
                                wifiEnergyCost, 
                                wifiStats.bandwidthDown,
                                wifiStats.bandwidthUp,
                                wifiStats.rttMillis));
        logPrint(String.format("  expected wifi availability: %.2f%%", wifiAvailability * 100.0));
        
        return expectedValue(wifiEnergyCost, mobileEnergyCost, wifiAvailability) / 1000.0;
    }

    private double currentDataCost(PrefetchBatch batch, int netType) {
        if (netType == ConnectivityManager.TYPE_WIFI) {
            return 0;
        } else {
            if (dataWeight.supplyIsExhausted()) {
                return PROHIBITIVE_DATA_COST;
            }
            return batch.bytesToTransfer();
        }
    }
    
    private double averageDataCost(PrefetchBatch batch) {
        return expectedValue(0, batch.bytesToTransfer(), wifiTracker.availability());
    }

    private double calculateBenefit(PrefetchBatch batch) {
        // Application implements this computation.
        // averageNetworkStats contains an estimate of the average network conditions
        //   that the fetch might encounter, so estimateFetchTime represents the 
        //   average benefit of prefetching (taking size into account).
        NetworkStats expectedNetworkStats = calculateExpectedNetworkStats();
        double benefit = batch.estimateFetchTime(expectedNetworkStats.bandwidthDown,
                                                 expectedNetworkStats.bandwidthUp,
                                                 expectedNetworkStats.rttMillis);
        FetchFuture<?> prefetch = batch.first().prefetch;
        //double accuracy = prefetch.getCache().stats.getPrefetchAccuracy(prefetch);
        double accuracy = prefetch.getCache().stats.getHardcodedPrefetchAccuracy(prefetch);
        //Log.d(TAG, String.format("Computed prefetch accuracy: %f", accuracy));
        return (accuracy * benefit);
    }

    private NetworkStats calculateExpectedNetworkStats() {
        NetworkStats wifiStats = averageNetworkStats.get(ConnectivityManager.TYPE_WIFI);
        NetworkStats mobileStats = averageNetworkStats.get(ConnectivityManager.TYPE_MOBILE);
        if (wifiStats == null) {
            return mobileStats;
        }
        
        double wifiAvailability = wifiTracker.availability();
        NetworkStats expectedStats = new NetworkStats();
        expectedStats.bandwidthDown = (int) expectedValue(wifiStats.bandwidthDown,
                                                          mobileStats.bandwidthDown,
                                                          wifiAvailability);
        expectedStats.bandwidthUp = (int) expectedValue(wifiStats.bandwidthUp,
                                                        mobileStats.bandwidthUp,
                                                        wifiAvailability);
        expectedStats.rttMillis = (int) expectedValue(wifiStats.rttMillis,
                                                      mobileStats.rttMillis,
                                                      wifiAvailability);
        return expectedStats;
    }

    private double expectedValue(double valueIfTrue,
                                 double valueIfFalse,
                                 double probability) {
        return probability * valueIfTrue + (1 - probability) * valueIfFalse;
    }

    private void issuePrefetch(PrefetchTask task) {
        if (alreadyIssued(task)) {
            return;
        }
        
        if (!prefetchesInProgress.offer(task)) {
            // shouldn't happen, because only one thread calls this,
            //  and it always checks prefetchesInProgress.remainingCapacity() > 0 
            //  before doing so.
            Log.e(TAG, "WARNING: pending queue refused prefetch.  Shouldn't happen.");
        }

        try {
            task.prefetch.startAsync(false);
        } catch (CancellationException e) {
            Log.e(TAG, "Prefetch cancelled; discarding");
        }
    }

    private boolean alreadyIssued(PrefetchTask task) {
        FetchFuture<?> prefetch = task.prefetch;
        
        // don't issue a prefetch if some fetch for this data
        //  was already issued; it'll only hang up future prefetches
        boolean issued = prefetch.wasIssued() || prefetch.isCancelled();
        if (issued) {
            logPrint(String.format("Ignoring already-issued prefetch 0x%08x", 
                                   prefetch.hashCode()));
        }
        return issued;
    }

    private void deferDecision(PrefetchTask task) {
        if (!alreadyIssued(task)) {
            deferredPrefetches.add(task);
        }
    }
}
