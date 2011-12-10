package edu.umich.eac;

import java.util.HashMap;
import java.util.Map;

public class AverageNetworkStats {
    public void initialize(Map<Integer, NetworkStats> initStats) {
        for (int type : initStats.keySet()) {
            stats.put(type, initStats.get(type));
            statsUpdateCounts.put(type, 1);
        }
    }
    
    public NetworkStats get(Integer type) {
        return stats.get(type);
    }
    
    /**
     * If the network type is not in the map, insert its stats.
     * If the network type is already in the map, update the stats as a running average.
     * @param type The network type; currently either TYPE_WIFI or TYPE_MOBILE
     * @param stats The new stats to add for this network type
     */
    public void add(Integer type, NetworkStats newStats) {
        if (stats.containsKey(type)) {
            Integer numUpdates = statsUpdateCounts.get(type);
            assert(numUpdates != null);
            
            stats.get(type).updateAsAverage(newStats, numUpdates);
        } else {
            assert(!statsUpdateCounts.containsKey(type));
            stats.put(type, newStats);
            statsUpdateCounts.put(type, 1);
        }
    }
    
    private Map<Integer, NetworkStats> stats = new HashMap<Integer, NetworkStats>();
    private Map<Integer, Integer> statsUpdateCounts = new HashMap<Integer, Integer>();
}
