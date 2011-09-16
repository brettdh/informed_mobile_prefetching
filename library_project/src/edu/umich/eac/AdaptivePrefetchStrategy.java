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
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.util.Log;
import edu.umich.eac.WifiTracker.ConditionChange;
import edu.umich.eac.WifiTracker.Prediction;

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
        public boolean reevaluate() {
            return AdaptivePrefetchStrategy.this.handlePrefetch(this);
        }

        public int compareTo(PrefetchTask another) {
            if (prefetch.equals(another.prefetch)) {
                return 0;
            }
            return scheduledTime.compareTo(another.scheduledTime);
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
            Queue<PrefetchTask> tasks = new LinkedList<PrefetchTask>();
            while (!deferredPrefetches.isEmpty()) {
                // This will never block, because of the isEmpty check,
                //  and because this is the only thread that removes items
                //  from the queue.
                PrefetchTask task = deferredPrefetches.take();
                // take() returns an item or throws InterruptedException.
                //   it does not return null.
                assert(task != null);
                tasks.add(task);
            }
            while (!tasks.isEmpty()) {
                PrefetchTask task = tasks.remove();
                if (task.reevaluate()) {
                    // this prefetch was issued. 
                    //   restart evaluation at beginning, so as to avoid
                    //   the situation where network conditions change
                    //   in the middle of reevaluating the list.
                    //   I know I'm only issuing one prefetch at a time,
                    //   so don't bother checking the rest of the list.
                    break;
                } else {
                    // this prefetch was deferred again, meaning it was
                    //   added back to the deferredPrefetches queue.
                    //   so, we move on to the next one without waiting
                    //   or storing this one again.
                    continue;
                }
            }
            // add back any tasks that I didn't evaluate; they'll go to 
            //  their original place in the queue.
            deferredPrefetches.addAll(tasks);
        }
    }

    @Override
    public void onPrefetchDone(FetchFuture<?> prefetch, boolean cancelled) {
        if (cancelled) {
            PrefetchTask dummy = new PrefetchTask(prefetch);
            deferredPrefetches.remove(dummy);
        }
        prefetchesInProgress.remove(prefetch);
    }
    
    @Override
    public void onPrefetchEnqueued(FetchFuture<?> prefetch) {
        handlePrefetch(new PrefetchTask(prefetch));
    }
    
    private boolean handlePrefetch(PrefetchTask task) {
        FetchFuture<?> prefetch = task.prefetch;
        if (!prefetchesInProgress.offer(prefetch)) {
            // too many prefetches in progress; defer
            logPrint(String.format("%d prefetches outstanding; deferring prefetch 0x%08x", 
                                   prefetchesInProgress.size(), prefetch.hashCode()));
            deferDecision(task);
            return false;
        }
        
        double cost = calculateCost(prefetch);
        double benefit = calculateBenefit(prefetch);
        logPrint(String.format("Cost = %f; benefit = %f; %s prefetch 0x%08x", 
                               cost, benefit, 
                               (cost < benefit) ? "issuing" : "deferring",
                               prefetch.hashCode()));
        if (cost < benefit) {
            issuePrefetch(prefetch);
            return true;
        } else {
            prefetchesInProgress.remove(prefetch);
            deferDecision(task);
            return false;
        }
    }
    
    private double calculateCost(FetchFuture<?> prefetch) {
        double energyWeight = calculateEnergyWeight();
        double dataWeight = calculateDataWeight();
        
        double energyCostNow = estimateEnergyCost(prefetch, currentNetworkStats, currentNetworkPower());
        double dataCostNow = currentDataCost(prefetch);
        
        double energyCostFuture = estimateEnergyCost(prefetch, averageNetworkStats, averageNetworkPower());
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
        } else {
            // TODO: tune adaptively based on resource usage history & projection.
            throw new Error(ADAPTATION_NOT_IMPL_MSG);
        }
    }

    private double calculateDataWeight() {
        if (fixedAdaptiveParamsEnabled) {
            return fixedDataWeight;
        } else {
            // TODO: tune adaptively based on resource usage history & projection.
            throw new Error(ADAPTATION_NOT_IMPL_MSG);
        }
    }
    
    private double estimateEnergyCost(FetchFuture<?> prefetch, 
                                      NetworkStats stats, 
                                      double networkPowerWatts) {
        double fetchTime = prefetch.estimateFetchTime(stats.bandwidthDown, 
                                                      stats.bandwidthUp,
                                                      stats.rttMillis);
        return fetchTime * networkPowerWatts;
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
    
    private void issuePrefetch(FetchFuture<?> prefetch) {
        PrefetchTask dummy = new PrefetchTask(prefetch);
        deferredPrefetches.remove(dummy);
        prefetch.addLabels(IntNWLabels.MIN_ENERGY);
        prefetch.addLabels(IntNWLabels.MIN_COST);
        try {
            prefetch.startAsync(false);
        } catch (CancellationException e) {
            Log.e(TAG, "Prefetch cancelled; discarding");
        }
    }

    private void deferDecision(PrefetchTask task) {
        deferredPrefetches.add(task);
    }
}
