package edu.umich.eac.tests;

import android.test.InstrumentationTestCase;

public class NativePromotionTest extends InstrumentationTestCase {
    protected native void setUp();
    protected native void tearDown();
    public native void testWaitForPrefetch();
    public native void testPromotion();
}
