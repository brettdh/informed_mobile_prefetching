package edu.umich.eac;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.concurrent.CancellationException;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.util.Log;
import edu.umich.eac.WifiTracker.ConditionChange;
import edu.umich.eac.WifiTracker.Prediction;

public class AdaptivePrefetchStrategy extends PrefetchStrategy {
    static final String TAG = AdaptivePrefetchStrategy.class.getName();

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
            return AdaptivePrefetchStrategy.this.handlePrefetch(prefetch);
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
    
    private static boolean fixedAdaptiveParamsEnabled = false;
    private static double fixedEnergyWeight;
    private static double fixedDataWeight;
    
    private int mEnergySpent;
    private ProcNetworkStats mDataSpent;
    
    // TODO: update at the right times!
    private NetworkStats currentNetworkStats;
    private NetworkStats averageNetworkStats;
    private int numNetworkStatsUpdates;
    
    private MonitorThread monitorThread;
    
    @Override
    public void setup(Context context, Date goalTime, int energyBudget, int dataBudget) {
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
    
    public static void setStaticParams(double energyWeight, double dataWeight) {
        fixedAdaptiveParamsEnabled = true;
        fixedEnergyWeight = energyWeight;
        fixedDataWeight = dataWeight;
    }

    class MonitorThread extends Thread {
        @Override
        public void run() {
            final int SAMPLE_PERIOD_MS = 200;
            Date lastNetworkStatsUpdate = new Date();
            while (true) {
                // TODO: update energy stats
                
                mDataSpent.updateStats();
                if (System.currentTimeMillis() - lastNetworkStatsUpdate.getTime() > 1000) {
                    updateNetworkStats();
                }
                
                try {
                    PrefetchTask task = 
                        deferredPrefetches.poll(SAMPLE_PERIOD_MS, TimeUnit.MILLISECONDS);
                    if (task == null) {
                        // timed out
                        continue;
                    }
                    if (task.reevaluate()) {
                        continue;
                    }
                
                    Thread.sleep(SAMPLE_PERIOD_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private synchronized void updateNetworkStats() {
        currentNetworkStats = NetworkStats.getNetworkStats();
        averageNetworkStats.updateAsAverage(currentNetworkStats, numNetworkStatsUpdates);
        numNetworkStatsUpdates++;
    }
    
    @Override
    public void onPrefetchCancelled(FetchFuture<?> prefetch) {
        deferredPrefetches.remove(prefetch);
    }
    
    @Override
    public void onPrefetchEnqueued(FetchFuture<?> prefetch) {
        handlePrefetch(prefetch);
    }
    
    private boolean handlePrefetch(FetchFuture<?> prefetch) {
        double cost = calculateCost(prefetch);
        double benefit = calculateBenefit(prefetch);
        if (cost < benefit) {
            issuePrefetch(prefetch);
            return true;
        } else {
            deferDecision(prefetch);
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
        if (!isWifiAvailable()) {
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
        // networkStats contains an estimate of the average network conditions
        //   that the fetch might encounter, so estimateFetchTime represents the 
        //   average benefit of prefetching (taking size into account).
        NetworkStats networkStats = NetworkStats.getNetworkStats();
        double benefit = prefetch.estimateFetchTime(networkStats.bandwidthDown,
                                                    networkStats.bandwidthUp,
                                                    networkStats.rttMillis);
        double accuracy = prefetch.getCache().stats.getPrefetchAccuracy();
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
        deferredPrefetches.remove(prefetch);
        prefetch.addLabels(IntNWLabels.MIN_ENERGY);
        prefetch.addLabels(IntNWLabels.MIN_COST);
        try {
            prefetch.startAsync(false);
        } catch (CancellationException e) {
            Log.e(TAG, "Prefetch cancelled; discarding");
        }
    }

    private void deferDecision(FetchFuture<?> prefetch) {
        PrefetchTask task = new PrefetchTask(prefetch);
        deferredPrefetches.add(task);
    }
}
