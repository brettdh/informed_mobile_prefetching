package edu.umich.eac;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Date;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;

public class WifiTracker extends BroadcastReceiver {
    public class Prediction {
        public ConditionChange change;
        public double bwDown;
        public double bwUp;
        public double timeInFuture; // seconds
        public Prediction(ConditionChange c, 
                          double bw_down, 
                          double bw_up, 
                          double time) {
            change = c;
            bwDown = bw_down;
            bwUp = bw_up;
            timeInFuture = time;
        }
        
        public String toString() {
            return String.format("Prediction(%s, bwDown: %f, bwUp: " +
                                 "%f, timeInFuture: %f)",
                                 change.toString(), bwDown, bwUp, 
                                 timeInFuture);
        }
    }

    // This has to encompass all aspects:
    //  1) Will wifi become (un)available? 
    //     - Changes 3G data usage, energy
    //     - "Unavailable" is reflected as bandwidth=0, I think,
    //       so (2) might be sufficient.
    //  2) Will the bandwidth increase/decrease? 
    //     - Changes energy; radio active longer
    public enum ConditionChange {
        NO_CHANGE, BETTER, WORSE
    }

    public Prediction predictConditionsChange() {
        try {
            getPredictedBandwidth();
            
            for (int i = 0; i < mAvgBandwidthDown.length; ++i) {
                double time = predictionStep * i;
                ConditionChange change = getConditionChange(time);
                if (change != ConditionChange.NO_CHANGE) {
                    return new Prediction(change, mAvgBandwidthDown[i],
                                          mAvgBandwidthUp[i], time);
                }
            }
        } catch (IOException e) {
            // couldn't get bandwidth; assume it's steady
        } 
        return new Prediction(ConditionChange.NO_CHANGE, 0.0, 0.0, 0.0);
    }

    public Prediction getCurrentConditions() {
        Prediction pred = new Prediction(ConditionChange.NO_CHANGE, 
                                         0.0, 0.0, 0.0);
        try {
            getPredictedBandwidth();
            pred.bwDown = mAvgBandwidthDown[0];
            pred.bwUp = mAvgBandwidthUp[0];
            pred.timeInFuture = 0.0;
        } catch (IOException e) {
            // ignore
        }
        return pred;
    }

    public Prediction getFutureConditions(double timeInFuture) {
        if (timeInFuture > predictionHorizon) {
            Log.e(TAG, String.format("Error: prediction requested for " +
                                     "%f seconds from now",
                                     timeInFuture));
            Log.e(TAG, String.format("Error: (prediction horizon is " +
                                     "%f seconds)", 
                                     predictionHorizon));
            return null;
        }
        Prediction pred = new Prediction(ConditionChange.NO_CHANGE,
                                         0.0, 0.0, 0.0);
        try {
            getPredictedBandwidth();
        } catch (IOException e) {
            return null;
        }
        
        int step = (int) Math.floor(timeInFuture / predictionStep);
        assert(step < mAvgBandwidthDown.length);
        pred.change = getConditionChange(timeInFuture);
        pred.bwDown = mAvgBandwidthDown[step];
        pred.bwUp = mAvgBandwidthUp[step];
        pred.timeInFuture = timeInFuture;
        return pred;
    }

    /**
     * Returns the fraction of the time since this WifiTracker's creation that
     * wifi has been available.
     * @return Wifi availability, in the range [0.0, 1.0].
     */
    public synchronized double availability() {
        // XXX: will always return zero if null context is passed to constructor.
        Date now = new Date();
        long millisSinceCreation = now.getTime() - trackerCreated.getTime();
        if (millisSinceCreation == 0) {
            return 0.0;
        }
        long availableMillis = wifiAvailableMillis;
        if (wifiAvailable) {
            availableMillis += (now.getTime() - lastEvent.getTime());
        }
        
        return ((double) availableMillis) / ((double) millisSinceCreation);
    }
    
    public boolean isWifiAvailable() {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        return (wifi != null && wifi.isWifiEnabled() &&
                wifi.getConnectionInfo().getSupplicantState() == SupplicantState.COMPLETED &&
                wifi.getDhcpInfo() != null);
    }
    
    @Override
    public void onReceive(Context unused, Intent intent) {
        Log.d(TAG, "Got update: intent = " + intent.toString());
        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                Log.d(TAG, key + "=>" + extras.get(key));
            }
        }
        assert(intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION));
        NetworkInfo networkInfo = 
                intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
        if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            updateAvailability(networkInfo);
        }
    }

    private synchronized void updateAvailability(NetworkInfo networkInfo) {
        boolean nowConnected = networkInfo.isConnected();
        if (nowConnected) {
            if (!wifiAvailable) {
                lastEvent = new Date();
                wifiAvailable = true;
                EnergyAdaptiveCache.logEvent("wifi-up", 0);
            }
        } else {
            if (wifiAvailable) {
                Date now = new Date();
                wifiAvailableMillis += (now.getTime() - lastEvent.getTime());
                lastEvent = now;
                wifiAvailable = false;
                EnergyAdaptiveCache.logEvent("wifi-down", 0);
            }
        }
    }
    
    public WifiTracker(Context context) {
        this.context = context;
        trackerCreated = new Date();
        lastEvent = trackerCreated;
        
        if (context != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            context.registerReceiver(this, filter);
        }
    }
    
    protected void finalize() throws Throwable {
        if (context != null) {
            context.unregisterReceiver(this);
        }
    }

    private Context context;
    private long wifiAvailableMillis = 0;
    private Date trackerCreated;
    private Date lastEvent;
    private boolean wifiAvailable = false;
    
    // TODO: how much bandwidth change is appreciable?
    private static final double bwChangeThreshold = 50000;
    
    public static final double predictionHorizon = 30.0;
    private static final int predictionIntervals = 6;
    private static final double predictionStep = 
        predictionHorizon / predictionIntervals;
    private static final int predictionServerPort = 3500;

    private static final String TAG = WifiTracker.class.getName();
    
    private double[] mAvgBandwidthDown = new double[predictionIntervals];
    private double[] mAvgBandwidthUp = new double[predictionIntervals];
    private double[] mStddevBandwidthDown = new double[predictionIntervals];
    private double[] mStddevBandwidthUp = new double[predictionIntervals];
    
    private Date mUpdateTime = null;
    
    private void getPredictedBandwidth() throws IOException {
        // TODO: memoize?  bound to fixed update frequency?
        
        Socket sock = new Socket("127.0.0.1", predictionServerPort);
        PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
        for (int i = 0; i < predictionIntervals; ++i) {
            double future = i * predictionStep;
            String req = String.format("average %f %f both", future, 
                                       future + predictionStep);
            out.println(req);
        }
        
        BufferedReader in = new BufferedReader(
                new InputStreamReader(sock.getInputStream())
        );
        for (int i = 0; i < predictionIntervals; ++i) {
            String line = in.readLine();
            if (line == null) {
                throw new IOException("Failed to read line from prediction server socket");
            }
            String[] fields = line.trim().split(" +");
            mAvgBandwidthDown[i] = Double.parseDouble(fields[1]);
            mStddevBandwidthDown[i] = Double.parseDouble(fields[2]);
            mAvgBandwidthUp[i] = Double.parseDouble(fields[4]);
            mStddevBandwidthUp[i] = Double.parseDouble(fields[5]);
        }
        out.close();
        in.close();
        sock.close();
        mUpdateTime = new Date();
    }
    
    // assumes bandwidth predictions are up-to-date
    //  (i.e. call getPredictedBandwidth before this)
    private ConditionChange getConditionChange(double timeInFuture) {
        assert(timeInFuture < predictionHorizon);
        
        double init_bw = mAvgBandwidthDown[0] + mAvgBandwidthUp[0];
        int step = (int) Math.floor(timeInFuture / predictionStep);
        double cur_bw = mAvgBandwidthDown[step] + mAvgBandwidthUp[step];
        double threshold = bwChangeThreshold + 
                           mStddevBandwidthDown[step] + 
                           mStddevBandwidthUp[step];
        ConditionChange change = ConditionChange.NO_CHANGE;
        if (Math.abs(cur_bw - init_bw) > threshold) {
            if (cur_bw > init_bw) {
                change = ConditionChange.BETTER;
            } else {
                change = ConditionChange.WORSE;
            }
        }
        return change;
    }
}

