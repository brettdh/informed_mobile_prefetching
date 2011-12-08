package edu.umich.eac;

import java.util.Date;

public class PassiveThreegEstimate {
    // ignore small fetches when passively estimating bandwidth.
    private static final int TRANSFER_THRESHOLD = 8000;
    
    // Some brief observations indicate that the wifi receives 100-400 bytes/sec
    //  when not associated.
    private static final int WIFI_TRANSFER_RATE_THRESHOLD = 500;

    private static final double WIFI_PROPORTION_THRESHOLD = 0.1;
    
    private FetchFuture<?> prefetch;
    private int startingSize;
    private Date startingTime;
    private ProcNetworkStats wifiStats = new ProcNetworkStats("eth0");
    
    public void beginEstimation(FetchFuture<?> prefetch) {
        this.prefetch = prefetch;
        this.startingSize = prefetch.bytesToTransfer();
        this.startingTime = new Date();
        wifiStats.updateStats();
    }
    
    public int getBandwidthAfterPrefetchDone(FetchFuture<?> prefetch) {
        if (this.prefetch != null && this.prefetch.equals(prefetch)) {
            // zero if finished; else number of bytes remaining
            int endingSize = prefetch.bytesToTransfer();
            int bytesTransferred = startingSize - endingSize;
            
            double durationSecs = ((double)(System.currentTimeMillis() - startingTime.getTime())) / 1000.0;
            
            long startWifiBytes = wifiStats.getTotalBytes();
            wifiStats.updateStats();
            long wifiBytes = wifiStats.getTotalBytes() - startWifiBytes;
            
            if (bytesTransferred > TRANSFER_THRESHOLD &&
                wifiNotInterfering(durationSecs, wifiBytes, bytesTransferred)) {
                // try to avoid making estimates for tiny prefetches and 
                //   prefetches that might have been partially sent on wifi, 
                //   so as to avoid wild overestimates and future reckless prefetching.
                return (int)(((double)bytesTransferred) / durationSecs);
            }
        }
        return -1;
    }

    private boolean wifiNotInterfering(double durationSecs, long wifiBytes, int prefetchBytes) {
        if ((((double) wifiBytes) / durationSecs) < WIFI_TRANSFER_RATE_THRESHOLD) {
            double wifiProportion = ((double) wifiBytes) / ((double) prefetchBytes);
            return wifiProportion < WIFI_PROPORTION_THRESHOLD;
        }
        return true;
    }
}
