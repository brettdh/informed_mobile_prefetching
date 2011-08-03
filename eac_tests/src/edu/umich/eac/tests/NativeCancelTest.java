package edu.umich.eac.tests;

import java.util.concurrent.CancellationException;

import android.test.InstrumentationTestCase;

public class NativeCancelTest extends InstrumentationTestCase {
    protected native void setUp();
    protected native void tearDown();
    public native void testGetTimeout();
    public native void testCancel() throws CancellationException;
    
    static {
        System.loadLibrary("eac_native");
        System.loadLibrary("eac_native_tests");
    }
}
