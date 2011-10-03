package edu.umich.eac;

import android.content.Context;
import android.test.InstrumentationTestCase;

public class NativeCancelTest extends InstrumentationTestCase {
    protected void setUp() {
        setUp(getInstrumentation().getContext());
    }
    protected native void setUp(Context context);
    protected native void tearDown();
    
    public native void testGetTimeout();
    public native void testCancel();
    
    static {
        System.loadLibrary("eac_native_tests");
    }
}
