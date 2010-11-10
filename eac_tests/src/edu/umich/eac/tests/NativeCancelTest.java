package edu.umich.eac.tests;

import android.test.InstrumentationTestCase;

public class NativeCancelTest extends InstrumentationTestCase {
    protected native void setUp();
    protected native void tearDown();
    public native void testGetTimeout();
    public native void testCancel();
}
