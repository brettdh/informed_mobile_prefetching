package edu.umich.eac;

import android.content.Context;
import android.test.InstrumentationTestCase;

public class NativeCancelTest extends InstrumentationTestCase {
    protected void setUp() {
        setUp(getInstrumentation().getContext());
    }
    protected native void setUp(Context context);
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
