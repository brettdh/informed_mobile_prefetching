package edu.umich.eac;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Map;
import java.util.EnumMap;

import android.content.Context;
import android.util.Log;
import edu.umich.eac.PrefetchStrategyType;
import edu.umich.eac.AggressivePrefetchStrategy;
import edu.umich.eac.ConservativePrefetchStrategy;

abstract class PrefetchStrategy {
    // based on 20 minutes of the Ypsilanti driving trace.
    //private static final double HARDCODED_HISTORICAL_WIFI_AVAILABILITY = 0.506568;
    
    // based on the first A2 walking trace, SoE -> Broken Egg.
    private static final double HARDCODED_HISTORICAL_WIFI_AVAILABILITY = 0.177907;

    protected WifiTracker wifiTracker;
    public abstract void onPrefetchEnqueued(FetchFuture<?> prefetch);
    public void onPrefetchDone(FetchFuture<?> prefetch, boolean cancelled) {}
    public void onDemandFetch(FetchFuture<?> prefetch) {}
    
    /** 
     * Initialize the object with the strategy parameters.
     * Subclasses should override this to get the info.
     * 
     * @param goalTime Ending time of the goals
     * @param energyGoal Amount of energy spendable before goalTime
     *        (note: units are currently %-battery.)
     * @param dataGoal Bytes of mobile data spendable before goalTime
     */
    public void setup(Context context, Date goalTime, double energyGoal, int dataGoal) {
        // initialize the wifi availability from the trace.
        wifiTracker = new WifiTracker(context, 
                                      HARDCODED_HISTORICAL_WIFI_AVAILABILITY, 
                                      goalTime.getTime() - System.currentTimeMillis());
        try {
            logFileWriter = new PrintWriter(new FileWriter(LOG_FILENAME, true), true);
        } catch (IOException e) {
            Log.e(this.getClass().getName(), "Failed to create log file: " + e.getMessage());
        }
    }
    
    /**
     * Update the goal time.  Used to synchronize the user-replay script
     *  with the prefetch strategy.
     * @param newGoalTime new ending time of the goals.
     */
    public void updateGoalTime(Date newGoalTime) {}
    
    public static PrefetchStrategy create(Context context,
                                          PrefetchStrategyType type,
                                          Date goalTime,
                                          double energyGoal,
                                          int dataGoal) {
        PrefetchStrategy strategy = null;
        Class<?> cls = strategies.get(type);
        try {
            strategy = (PrefetchStrategy) cls.newInstance();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
        strategy.setup(context, goalTime, energyGoal, dataGoal);
        return strategy;
    }
    
    private static Map<PrefetchStrategyType, Class<?> > strategies;
    static {
        strategies = new EnumMap<PrefetchStrategyType, Class<?> >(
            PrefetchStrategyType.class
        );
        strategies.put(PrefetchStrategyType.AGGRESSIVE,
                       AggressivePrefetchStrategy.class);
        strategies.put(PrefetchStrategyType.CONSERVATIVE,
                       ConservativePrefetchStrategy.class);
        strategies.put(PrefetchStrategyType.ADAPTIVE,
                       AdaptivePrefetchStrategy.class);
        strategies.put(PrefetchStrategyType.SIZE_LIMIT,
                       SizeLimitPrefetchStrategy.class);
    }
    
    private static final String LOG_FILENAME = "/sdcard/intnw/adaptive_prefetch_decisions.log";
    
    private PrintWriter logFileWriter;
    void logPrint(String msg) {
        if (logFileWriter != null) {
            final long now = System.currentTimeMillis();
            logFileWriter.println(String.format("%d %s", now, msg));
        }
    }
}
