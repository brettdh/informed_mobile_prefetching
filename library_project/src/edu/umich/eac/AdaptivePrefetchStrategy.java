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
import edu.umich.eac.WifiTracker.ConditionChange;
import edu.umich.eac.WifiTracker.Prediction;
import edu.umich.libpowertutor.EnergyEstimates;

public class AdaptivePrefetchStrategy extends PrefetchStrategy {
    private static final String LOG_FILENAME = "/sdcard/intnw/adaptive_prefetch_decisions.log";
    static final String TAG = AdaptivePrefetchStrategy.class.getName();

    private PrintWriter logFileWriter;
    void logPrint(String msg) {
        if (logFileWriter != null) {
            final long now = System.currentTimeMillis();
            logFileWriter.println(String.format("%d %s", now, msg));
        }
    }
    
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
            return order - another.order;
        }
    }

    private PriorityBlockingQueue<PrefetchTask> deferredPrefetches = 
        new PriorityBlockingQueue<PrefetchTask>();
    
    private static final int NUM_CONCURRENT_PREFETCHES = 1;
    private BlockingQueue<FetchFuture<?> > prefetchesInProgress = 
        new ArrayBlockingQueue<FetchFuture<?> >(NUM_CONCURRENT_PREFETCHES);
    
    private static boolean fixedAdaptiveParamsEnabled = false;
    private static double fixedEnergyWeight;
    private static double fixedDataWeight;
    
    private GoalAdaptiveResourceWeight energyWeight;
    private GoalAdaptiveResourceWeight dataWeight;
    
    private int mLastEnergySpent; // in mJ
    private ProcNetworkStats mDataSpent;
    
    // These map TYPE_WIFI or TYPE_MOBILE to the network stats.
    private Map<Integer, NetworkStats> currentNetworkStats;
    private AverageNetworkStats averageNetworkStats;
    
    private MonitorThread monitorThread;
    
    @Override
    public void setup(Context context, Date goalTime, int energyBudget, int dataBudget) {
        this.context = context;
        try {
            logFileWriter = new PrintWriter(new FileWriter(LOG_FILENAME, true), true);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create log file: " + e.getMessage());
        }
        
        long millisUntilGoal = goalTime.getTime() - System.currentTimeMillis();
        logPrint(String.format("Setup adaptive strategy with energy budget %d%% data budget %d bytes  goal %d ms from now",
                               energyBudget, dataBudget, millisUntilGoal));
        
        double energyBudgetJoules = convertBatteryPercentToJoules(energyBudget);
        energyWeight = new GoalAdaptiveResourceWeight(this, "energy", energyBudgetJoules, goalTime);
        dataWeight = new GoalAdaptiveResourceWeight(this, "data", dataBudget, goalTime);
        
        mLastEnergySpent = 0;
        mDataSpent = new ProcNetworkStats(CELLULAR_IFNAME);
        
        wifiTracker = new WifiTracker(context);
        
        currentNetworkStats = NetworkStats.getAllNetworkStats(context);
        averageNetworkStats = new AverageNetworkStats();
        averageNetworkStats.initialize(NetworkStats.getAllNetworkStats(context));
        
        monitorThread = new MonitorThread();
        monitorThread.start();
    }
    
    private double convertBatteryPercentToJoules(double energyBudgetBatteryPercent) {
        // TODO: sanity-check.
        double charge = 1400.0 * energyBudgetBatteryPercent / 100.0; // mAh
        double energy = charge * 4.0; // average voltage 4V;  mAh*V, or mWh
        energy *= 3600; // mWs or mJ
        return energy / 1000.0; // mJ to J
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

    private Date lastStatsUpdate = new Date();
    private Context context;
    
    private synchronized void updateStats() {
        if (System.currentTimeMillis() - lastStatsUpdate.getTime() > 1000) {
            updateEnergyStats();
            updateNetworkStats();
            updateDataStats();
            lastStatsUpdate = new Date();
        }
    }

    private synchronized void updateDataStats() {
        long dataSpent = mDataSpent.getTotalBytes();
        mDataSpent.updateStats();
        long dataSpentRecently = mDataSpent.getTotalBytes() - dataSpent;
        dataWeight.reportSpentResource(dataSpentRecently);
    }

    private synchronized void updateEnergyStats() {
        int energySpent = EnergyEstimates.energyConsumedSinceReset();
        double newEnergySpent = energySpent - mLastEnergySpent;
        mLastEnergySpent = energySpent;
        energyWeight.reportSpentResource(newEnergySpent / 1000.0);
    }

    private synchronized void updateNetworkStats() {
        currentNetworkStats = NetworkStats.getAllNetworkStats(context);
        for (Integer type : currentNetworkStats.keySet()) {
            NetworkStats newStats = currentNetworkStats.get(type);
            averageNetworkStats.add(type, newStats);
        }
    }

    class MonitorThread extends Thread {
        BlockingQueue<PrefetchTask> tasksToEvaluate = 
            new LinkedBlockingQueue<PrefetchTask>();
        
        @Override
        public void run() {
            final int SAMPLE_PERIOD_MS = 200;
            updateStats();
            
            while (true) {
                try {
                    reevaluateAllDeferredPrefetches();
                    updateStats();
                    Thread.sleep(SAMPLE_PERIOD_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        private void reevaluateAllDeferredPrefetches() throws InterruptedException {
            if (prefetchesInProgress.remainingCapacity() == 0) {
                // too many prefetches in progress; defer
                logPrint(String.format("%d prefetches outstanding (first is 0x%08x); deferring", 
                        prefetchesInProgress.size(), 
                        prefetchesInProgress.peek().hashCode()));
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
                if (shouldIssuePrefetch(batch)) {
                    issuePrefetch(batch.first().prefetch);
                    batch.pop();
                    break;
                }
            }
            
            // add back any tasks that I didn't issue; they'll go to 
            //  their original place in the queue.
            batch.drainTo(deferredPrefetches);
            tasksToEvaluate.drainTo(deferredPrefetches);
        }
        
        void removeTask(FetchFuture<?> prefetch) {
            PrefetchTask dummy = new PrefetchTask(prefetch);
            tasksToEvaluate.remove(dummy);
            deferredPrefetches.remove(dummy);
        }
    }

    @Override
    public void onDemandFetch(FetchFuture<?> prefetch) {
        prefetchesInProgress.remove(prefetch);
        monitorThread.removeTask(prefetch);
    }
    
    @Override
    public void onPrefetchDone(FetchFuture<?> prefetch, boolean cancelled) {
        if (cancelled) {
            monitorThread.removeTask(prefetch);
        }
        prefetchesInProgress.remove(prefetch);
    }
    
    @Override
    public void onPrefetchEnqueued(FetchFuture<?> prefetch) {
        // The prefetch will get evaluated the next time around the loop.
        //  Queue instead of calling issuePrefetch so that
        //  issuePrefetch never hangs on prefetchesInProgress.offer();
        //  otherwise there's a race with
        //  prefetchesInProgress.remainingCapacity()
        deferDecision(new PrefetchTask(prefetch));
    }

    private boolean shouldIssuePrefetch(PrefetchBatch batch) {
        updateNetworkStats();

        Double cost = calculateCost(batch);
        Double benefit = calculateBenefit(batch);
        final boolean shouldIssuePrefetch = cost < benefit;
        
        logPrint(String.format("Cost = %s; benefit = %s; %s prefetch 0x%08x", 
                               cost.toString(), benefit.toString(),
                               shouldIssuePrefetch ? "issuing" : "deferring",
                               batch.first().hashCode()));
        return shouldIssuePrefetch;
    }
    
    private double calculateCost(PrefetchBatch batch) {
        double energyWeight = calculateEnergyWeight();
        double dataWeight = calculateDataWeight();
        
        double energyCostNow = currentEnergyCost(batch);
        double dataCostNow = currentDataCost(batch);
        
        double energyCostFuture = averageEnergyCost(batch);
        double dataCostFuture = averageDataCost(batch);
        
        double hintAccuracy = batch.first().prefetch.getCache().stats.getPrefetchAccuracy();
        double energyCostDelta = energyCostNow - (hintAccuracy * energyCostFuture);
        double dataCostDelta = dataCostNow - (hintAccuracy * dataCostFuture);
        
        double weightedEnergyCost = energyWeight * energyCostDelta;
        double weightedDataCost = dataWeight * dataCostDelta;
        double totalCost = weightedEnergyCost + weightedDataCost;
        logCost("Energy", energyCostNow, energyCostFuture, energyCostDelta,
                energyWeight, weightedEnergyCost);
        logCost("Data", dataCostNow, dataCostFuture, dataCostDelta, 
                dataWeight, weightedDataCost);
        logPrint(String.format("Total cost: %s", String.valueOf(totalCost)));
        return totalCost;
    }

    private void logCost(String type, 
                         double costNow, double costFuture, double costDelta,
                         double weight, double weightedCost) {
        logPrint(String.format("%s cost:  now %s later %s delta %s weight %s  weighted cost %s",
                               type,
                               String.valueOf(costNow), String.valueOf(costFuture),
                               String.valueOf(costDelta), String.valueOf(weight),
                               String.valueOf(weightedCost)));
    }

    private static final String ADAPTATION_NOT_IMPL_MSG =
       "Fixed adaptive params not set and auto-adaptation not yet implemented";

    private double calculateEnergyWeight() {
        if (fixedAdaptiveParamsEnabled) {
            return fixedEnergyWeight;
        } else {
            return energyWeight.getWeight();
        }
    }

    private double calculateDataWeight() {
        if (fixedAdaptiveParamsEnabled) {
            return fixedDataWeight;
        } else {
            return dataWeight.getWeight();
        }
    }
    
    private double currentEnergyCost(PrefetchBatch batch) {
        int datalen = batch.bytesToTransfer();
        NetworkStats wifiStats = currentNetworkStats.get(ConnectivityManager.TYPE_WIFI);
        NetworkStats mobileStats = currentNetworkStats.get(ConnectivityManager.TYPE_MOBILE);
        double energyCost = 0.0;
        if (wifiTracker.isWifiAvailable() && wifiStats != null) {
            // TODO: should try sending on wifi even if I don't have the stats yet.
            // But: in my experiments, I should have the stats.
            energyCost = EnergyEstimates.estimateWifiEnergyCost(datalen, 
                                                                wifiStats.bandwidthDown,
                                                                wifiStats.rttMillis);
        } else {
            energyCost = EnergyEstimates.estimateMobileEnergyCost(datalen, 
                                                                  mobileStats.bandwidthDown,
                                                                  mobileStats.rttMillis);
        }
        return energyCost / 1000.0; // mJ to J
    }
    
    private double averageEnergyCost(PrefetchBatch batch) {
        int datalen = batch.bytesToTransfer();
        NetworkStats wifiStats = averageNetworkStats.get(ConnectivityManager.TYPE_WIFI);
        NetworkStats mobileStats = averageNetworkStats.get(ConnectivityManager.TYPE_MOBILE);
        double wifiEnergyCost = 
            EnergyEstimates.estimateWifiEnergyCost(datalen, 
                                                   wifiStats.bandwidthDown,
                                                   wifiStats.rttMillis);
        
        double mobileEnergyCost = 
            EnergyEstimates.estimateMobileEnergyCostAverage(datalen, 
                                                            mobileStats.bandwidthDown,
                                                            mobileStats.rttMillis);
        
        double wifiAvailability = wifiTracker.availability();
        return expectedValue(wifiEnergyCost, mobileEnergyCost, wifiAvailability) / 1000.0;
    }

    private double currentDataCost(PrefetchBatch batch) {
        if (!wifiTracker.isWifiAvailable()) {
            return batch.bytesToTransfer();
        } else {
            return 0;
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
        double accuracy = batch.first().prefetch.getCache().stats.getPrefetchAccuracy();
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

    private WifiTracker wifiTracker;
    
    private void issuePrefetch(FetchFuture<?> prefetch) {
        if (alreadyIssued(prefetch)) {
            return;
        }
        
        if (!prefetchesInProgress.offer(prefetch)) {
            // shouldn't happen, because only one thread calls this,
            //  and it always checks prefetchesInProgress.remainingCapacity() > 0 
            //  before doing so.
            Log.e(TAG, "WARNING: pending queue refused prefetch.  Shouldn't happen.");
        }

        prefetch.addLabels(IntNWLabels.MIN_ENERGY);
        prefetch.addLabels(IntNWLabels.MIN_COST);
        try {
            prefetch.startAsync(false);
        } catch (CancellationException e) {
            Log.e(TAG, "Prefetch cancelled; discarding");
        }
    }

    private boolean alreadyIssued(FetchFuture<?> prefetch) {
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
        if (!alreadyIssued(task.prefetch)) {
            deferredPrefetches.add(task);
        }
    }
}
