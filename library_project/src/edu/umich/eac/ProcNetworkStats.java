package edu.umich.eac;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

public class ProcNetworkStats {
    private long bytesDownAtCreation;
    private long bytesUpAtCreation;
    
    private long bytesDown;
    private long bytesUp;
    
    private String iface;
    
    /*
     * Slight misnomer; turns out it's easier to read from sysfs.
     */
    public ProcNetworkStats(String iface) {
        this.iface = iface;
        bytesDown = bytesUp = 0;

        updateStats();
        bytesDownAtCreation = bytesDown;
        bytesUpAtCreation = bytesUp;
    }
    
    public synchronized void updateStats() {
        final String path = "/sys/class/net/" + iface + "/statistics/";
        try {
            for (String suffix : Arrays.asList("rx_bytes", "tx_bytes")) {
                FileReader in = new FileReader(path + suffix);
                BufferedReader reader = new BufferedReader(in, 64);
                String line = reader.readLine();
                if(suffix == "tx_bytes")
                    bytesUp = Long.parseLong(line);
                else
                    bytesDown = Long.parseLong(line);
                reader.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public synchronized long getTotalBytes() {
        return getBytesDown() + getBytesUp();
    }
    
    public synchronized long getBytesDown() {
        return (bytesDown - bytesDownAtCreation);
    }
    
    public synchronized long getBytesUp() {
        return (bytesUp - bytesUpAtCreation);
    }
}
