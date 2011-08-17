package edu.umich.eac;

import edu.umich.eac.tests.R;
import android.app.Activity;
import android.os.Bundle;

public class StubActivity extends Activity
{
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
}
