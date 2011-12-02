package edu.umich.eac;

import android.content.Context;
import android.test.InstrumentationTestCase;

public class NativePromotionTest extends InstrumentationTestCase {
    protected void setUp() {
        setUp(getInstrumentation().getContext());
    }
    protected native void setUp(Context context);
    protected native void tearDown();
    public native void testWaitForPrefetch();
    public native void testPromotion();
    
    static {
        System.loadLibrary("eac_native_tests");
    }
}
