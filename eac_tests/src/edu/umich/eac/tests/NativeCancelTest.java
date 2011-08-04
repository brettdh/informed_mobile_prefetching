package edu.umich.eac.tests;

import java.util.concurrent.CancellationException;

import android.test.InstrumentationTestCase;

public class NativeCancelTest extends InstrumentationTestCase {
    protected native void setUp();
    protected native void tearDown();
    
    // These don't pass right now due to a weird uncaught 
    //  Future::CancellationException in the native code.
    // TODO: come back and fix it.
    private native void testGetTimeout();
    private native void testCancel();
    
    static {
        System.loadLibrary("eac_native");
        System.loadLibrary("eac_native_tests");
    }
}
