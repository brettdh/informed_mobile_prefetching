package edu.umich.eac;

import java.util.Date;
import java.util.Map;
import java.util.EnumMap;

import android.content.Context;
import edu.umich.eac.PrefetchStrategyType;
import edu.umich.eac.AggressivePrefetchStrategy;
import edu.umich.eac.ConservativePrefetchStrategy;

abstract class PrefetchStrategy {
    public abstract void onPrefetchEnqueued(FetchFuture<?> prefetch);
    public void onPrefetchDone(FetchFuture<?> prefetch, boolean cancelled) {}
    
    /** 
     * Initialize the object with the strategy parameters.
     * Subclasses should override this to get the info.
     * 
     * @param goalTime Ending time of the goals
     * @param energyGoal Amount of energy spendable before goalTime
     *        (note: units are currently %-battery.)
     * @param dataGoal Bytes of mobile data spendable before goalTime
     */
    public void setup(Context context, Date goalTime, int energyGoal, int dataGoal) {}
    
    /**
     * Update the goal time.  Used to synchronize the user-replay script
     *  with the prefetch strategy.
     * @param newGoalTime new endingn time of the goals.
     */
    public void updateGoalTime(Date newGoalTime) {}
    
    public static PrefetchStrategy create(Context context,
                                          PrefetchStrategyType type,
                                          Date goalTime,
                                          int energyGoal,
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
    }
}
