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
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.util.Log;
import edu.umich.eac.WifiTracker.ConditionChange;
import edu.umich.eac.WifiTracker.Prediction;
import edu.umich.libpowertutor.EnergyEstimates;

public class AdaptivePrefetchStrategy extends PrefetchStrategy {
    private static final String LOG_FILENAME = "/sdcard/intnw/adaptive_prefetch_decisions.log";
    static final String TAG = AdaptivePrefetchStrategy.class.getName();

    private PrintWriter logFileWriter;
    private void logPrint(String msg) {
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
        private FetchFuture<?> prefetch;
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
    
    private Date mStartTime;
    private Date mGoalTime;
    private int mEnergyBudget;
    private int mDataBudget;
    
    private static boolean fixedAdaptiveParamsEnabled = false;
    private static double fixedEnergyWeight;
    private static double fixedDataWeight;
    
    private int mEnergySpent;
    private ProcNetworkStats mDataSpent;
    
    private NetworkStats currentNetworkStats;
    private NetworkStats averageNetworkStats;
    private int numNetworkStatsUpdates;
    
    private MonitorThread monitorThread;
    
    @Override
    public void setup(Context context, Date goalTime, int energyBudget, int dataBudget) {
        try {
            logFileWriter = new PrintWriter(new FileWriter(LOG_FILENAME, true), true);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create log file: " + e.getMessage());
        }
        
        mStartTime = new Date();
        mGoalTime = goalTime;
        mEnergyBudget = energyBudget;
        mDataBudget = dataBudget;
        mEnergySpent = 0;
        mDataSpent = new ProcNetworkStats(CELLULAR_IFNAME);
        
        wifiTracker = new WifiTracker(context);
        
        currentNetworkStats = NetworkStats.getNetworkStats();
        averageNetworkStats = NetworkStats.getNetworkStats();
        numNetworkStatsUpdates = 1;
        
        monitorThread = new MonitorThread();
        monitorThread.start();
    }
    
    @Override
    public void updateGoalTime(Date newGoalTime) {
        mGoalTime = newGoalTime;
    }
    
    public static void setStaticParams(double energyWeight, double dataWeight) {
        fixedAdaptiveParamsEnabled = true;
        fixedEnergyWeight = energyWeight;
        fixedDataWeight = dataWeight;
    }

    private Date lastStatsUpdate = new Date();
    
    private synchronized void updateStats() {
        updateEnergyStats();
        
        mDataSpent.updateStats();
        if (System.currentTimeMillis() - lastStatsUpdate.getTime() > 1000) {
            updateNetworkStats();
        }
        lastStatsUpdate = new Date();
    }

    private synchronized void updateEnergyStats() {
        // TODO: implement.
    }

    private synchronized void updateNetworkStats() {
        currentNetworkStats = NetworkStats.getNetworkStats();
        averageNetworkStats.updateAsAverage(currentNetworkStats, numNetworkStatsUpdates);
        numNetworkStatsUpdates++;
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
            
            deferredPrefetches.drainTo(tasksToEvaluate);
            logPrint(String.format("Reevaluating %d deferred prefetches", tasksToEvaluate.size()));

            while (!tasksToEvaluate.isEmpty()) {
                PrefetchTask task = tasksToEvaluate.remove();
                logPrint(String.format("Evaluating prefetch 0x%08x", task.prefetch.hashCode()));
                if (shouldIssuePrefetch(task.prefetch)) {
                    //   restart evaluation at beginning, so as to avoid
                    //   the situation where network conditions change
                    //   in the middle of reevaluating the list.
                    //   I know I'm only issuing one prefetch at a time,
                    //   so don't bother checking the rest of the list.
                    PrefetchTask firstTask = deferredPrefetches.poll();
                    if (firstTask != null && shouldIssuePrefetch(firstTask.prefetch)) {
                        issuePrefetch(firstTask.prefetch);
                        
                        deferDecision(task);
                    } else {
                        issuePrefetch(task.prefetch);

                        if (firstTask != null) {
                            deferDecision(firstTask);
                        }
                    }
                    break;
                } else {
                    deferDecision(task);
                }
            }
            
            // add back any tasks that I didn't evaluate; they'll go to 
            //  their original place in the queue.
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

    private boolean shouldIssuePrefetch(FetchFuture<?> prefetch) {
        Double cost = calculateCost(prefetch);
        Double benefit = calculateBenefit(prefetch);
        final boolean shouldIssuePrefetch = cost < benefit;
        
        logPrint(String.format("Cost = %s; benefit = %s; %s prefetch 0x%08x", 
                               cost.toString(), benefit.toString(),
                               shouldIssuePrefetch ? "issuing" : "deferring",
                               prefetch.hashCode()));
        return shouldIssuePrefetch;
    }
    
    private double calculateCost(FetchFuture<?> prefetch) {
        double energyWeight = calculateEnergyWeight();
        double dataWeight = calculateDataWeight();
        
        double energyCostNow = currentEnergyCost(prefetch);
        double dataCostNow = currentDataCost(prefetch);
        
        double energyCostFuture = averageEnergyCost(prefetch, averageNetworkStats, averageNetworkPower());
        double dataCostFuture = averageDataCost(prefetch);
        
        double hintAccuracy = prefetch.getCache().stats.getPrefetchAccuracy();
        double energyCostDelta = energyCostNow - (hintAccuracy * energyCostFuture);
        double dataCostDelta = dataCostNow - (hintAccuracy * dataCostFuture);
        
        return (energyWeight * energyCostDelta) + (dataWeight * dataCostDelta);
    }

    private static final String ADAPTATION_NOT_IMPL_MSG =
       "Fixed adaptive params not set and auto-adaptation not yet implemented";

    private double calculateEnergyWeight() {
        if (fixedAdaptiveParamsEnabled) {
            return fixedEnergyWeight;
        }

        // TODO: tune adaptively based on resource usage history & projection.
        throw new Error(ADAPTATION_NOT_IMPL_MSG);
    }

    private double calculateDataWeight() {
        if (fixedAdaptiveParamsEnabled) {
            return fixedDataWeight;
        }

        // TODO: tune adaptively based on resource usage history & projection.
        throw new Error(ADAPTATION_NOT_IMPL_MSG);
    }
    
    private double currentEnergyCost(FetchFuture<?> prefetch) {
        int datalen = prefetch.bytesToTransfer();
        NetworkStats stats = currentNetworkStats;
        if (wifiTracker.isWifiAvailable()) {
            return EnergyEstimates.estimateWifiEnergyCost(datalen, 
                                                          stats.bandwidthDown,
                                                          stats.rttMillis);
        } else {
            return EnergyEstimates.estimateMobileEnergyCost(datalen, 
                                                            stats.bandwidthDown,
                                                            stats.rttMillis);
        }
    }
    
    private double averageEnergyCost(FetchFuture<?> prefetch) {
        int datalen = prefetch.bytesToTransfer();
        NetworkStats stats = averageNetworkStats;
        
        
    }
    
    private double currentNetworkPower() {
        // TODO: use a power model to pick the power coefficient based on available networks.
        return 0;
    }
    
    private double averageNetworkPower() {
        // TODO: use a power model to calculate the average power coefficient 
        //       based on network history.
        return 0;
    }

    private double currentDataCost(FetchFuture<?> prefetch) {
        if (!wifiTracker.isWifiAvailable()) {
            return prefetch.bytesToTransfer();
        } else {
            return 0;
        }
    }
    
    private double averageDataCost(FetchFuture<?> prefetch) {
        return prefetch.bytesToTransfer() * (1 - wifiTracker.availability());
    }

    private double calculateBenefit(FetchFuture<?> prefetch) {
        // Application implements this computation.
        // averageNetworkStats contains an estimate of the average network conditions
        //   that the fetch might encounter, so estimateFetchTime represents the 
        //   average benefit of prefetching (taking size into account).
        double benefit = prefetch.estimateFetchTime(averageNetworkStats.bandwidthDown,
                                                    averageNetworkStats.bandwidthUp,
                                                    averageNetworkStats.rttMillis);
        double accuracy = prefetch.getCache().stats.getPrefetchAccuracy();
        Log.d(TAG, String.format("Computed prefetch accuracy: %f", accuracy));
        return (accuracy * benefit);
    }

    private WifiTracker wifiTracker;
    
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
