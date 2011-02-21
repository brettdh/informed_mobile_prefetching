package edu.umich.eac;

import java.util.Map;
import java.util.EnumMap;
import edu.umich.eac.PrefetchStrategyType;
import edu.umich.eac.AggressivePrefetchStrategy;
import edu.umich.eac.ConservativePrefetchStrategy;

abstract class PrefetchStrategy {
    public abstract void handlePrefetch(FetchFuture<?> prefetch);
    
    public static PrefetchStrategy create(PrefetchStrategyType type) {
        PrefetchStrategy strategy = null;
        Class<?> cls = strategies.get(type);
        try {
            strategy = (PrefetchStrategy) cls.newInstance();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
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
    }
}
