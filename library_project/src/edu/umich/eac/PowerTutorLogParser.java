package edu.umich.eac;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.zip.InflaterInputStream;

import android.util.Log;

public class PowerTutorLogParser extends Thread {
    private static final String TAG = "PowerTutorLogParser";
    
    protected void processLine(String line) {
        Log.d(TAG, line);
    }
    
    public void run() {
        try {
            final String logFile = "/data/data/edu.umich.PowerTutor/files/PowerTrace.log";
            FileInputStream logFileStream = new FileInputStream(logFile);
            InflaterInputStream inflaterStream = 
                new InflaterInputStream(logFileStream);
            InputStreamReader inReader = 
                new InputStreamReader(inflaterStream);
            BufferedReader lineReader = new BufferedReader(inReader);

            String line = null;
            while ((line = lineReader.readLine()) != null) {
                // Do something with the bytes
                processLine(line.trim());
            }
            // EOF
        } catch (IOException e) {
            // ooooops...
            Log.e(TAG, "Failed parsing log: " + e.toString());
        }
    }
}
