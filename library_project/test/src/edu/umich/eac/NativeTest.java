package edu.umich.eac;

import android.content.Context;
import android.test.InstrumentationTestCase;

public class NativeTest extends InstrumentationTestCase {
    private Context context;
    protected void setUp() {
        this.context = getInstrumentation().getContext();
    }

    public void testImmediateGet() {
        testWithDelay(context, 0);
    }
    
    public void testDelayedGet() {
        testWithDelay(context, 5);
    }

    private native void testWithDelay(Context context, long delaySecs);

    static {
        System.loadLibrary("eac_native");
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
