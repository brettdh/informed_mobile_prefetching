package edu.umich.eac.tests;

import android.test.InstrumentationTestCase;

public class NativeTest extends InstrumentationTestCase {
    protected void setUp() {}

    public void testImmediateGet() {
        testWithDelay(0);
    }
    
    public void testDelayedGet() {
        testWithDelay(5);
    }

    private native void testWithDelay(long delaySecs);

    static {
        System.loadLibrary("eac_native_tests");
    }

    public void doAssertions(boolean futureNotDoneYet, boolean stringMatch,
                             boolean futureDone, boolean cancelled) {
	assertFalse("Future not done yet", futureNotDoneYet);
        assertTrue("String matches", stringMatch);
        assertTrue("Future done", futureDone);
        assertFalse("Future not cancelled", cancelled);
    }
}
