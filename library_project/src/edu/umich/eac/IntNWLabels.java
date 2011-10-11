package edu.umich.eac;

// XXX: import these from intnw library
public class IntNWLabels {
    public static final int ONDEMAND = 0x4;
    public static final int BACKGROUND = 0x8;
    public static final int SMALL = 0x10;
    public static final int LARGE = 0x20;
    
    private static final int NET_PREF_SHIFT = 16;
    
    public static final int PREFER_WIFI = 1 << NET_PREF_SHIFT;
    public static final int PREFER_THREEG = 2 << NET_PREF_SHIFT;
    public static final int ALL_NET_PREF_LABELS = PREFER_WIFI | PREFER_THREEG;
}
