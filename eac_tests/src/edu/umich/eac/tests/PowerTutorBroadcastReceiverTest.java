package edu.umich.eac.tests;

import edu.umich.eac.PowerTutorBroadcastReceiver;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.test.InstrumentationTestCase;
import android.appwidget.AppWidgetManager;

public class PowerTutorBroadcastReceiverTest extends InstrumentationTestCase {
    private BroadcastReceiver mReceiver;
    private Context mContext;
    
    @Override
    protected void setUp() throws Exception {
        mReceiver = new PowerTutorBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        mContext = getInstrumentation().getContext();
        mContext.registerReceiver(mReceiver, filter);
    }
    
    @Override
    protected void tearDown() throws Exception {
        mContext.unregisterReceiver(mReceiver);
    }

    public void testReceive() throws InterruptedException {
        Thread.sleep(15000);
        assertTrue(true);
    }
    
}
