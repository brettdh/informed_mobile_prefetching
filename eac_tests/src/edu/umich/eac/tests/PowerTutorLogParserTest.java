package edu.umich.eac.tests;

import android.test.InstrumentationTestCase;

import edu.umich.eac.PowerTutorLogParser;

public class PowerTutorLogParserTest extends InstrumentationTestCase {
    private PowerTutorLogParser parser;
    
    protected void setUp() {
        parser = new PowerTutorLogParser();
    }
    
    public void testParse() throws InterruptedException {
        parser.start();
        parser.join();
        assertTrue(true);
    }
}
