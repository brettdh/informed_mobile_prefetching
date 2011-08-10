package edu.umich.eac;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.concurrent.PriorityBlockingQueue;

import android.util.Log;
import edu.umich.eac.WifiBandwidthPredictor.ConditionChange;
import edu.umich.eac.WifiBandwidthPredictor.Prediction;

public class AdaptivePrefetchStrategy extends PrefetchStrategy {
    private static final String TAG = AdaptivePrefetchStrategy.class.getName();

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
                
                PrefetchTask task = deferredPrefetches.remove();
                if (task.reevaluate()) {
                    continue;
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
    
    @Override
    public void onPrefetchEnqueued(FetchFuture<?> prefetch) {
        handlePrefetch(prefetch);
    }
    
    private boolean handlePrefetch(FetchFuture<?> prefetch) {
        // TODO: implement
        return false;
    }
    
    private WifiBandwidthPredictor wifiPredictor = new WifiBandwidthPredictor();
    
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
        prefetch.startAsync(false);
    }

    private void deferDecision(FetchFuture<?> prefetch) {
        PrefetchTask task = new PrefetchTask(prefetch);
        deferredPrefetches.add(task);
    }

}
