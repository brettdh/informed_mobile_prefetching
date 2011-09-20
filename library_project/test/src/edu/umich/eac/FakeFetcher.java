package edu.umich.eac;

class FakeFetcher extends CacheFetcher<String> {
    private String theString;
    
    FakeFetcher(String theString) {
        this.theString = theString;
    }
    
    @Override
    public String call(int labels) throws Exception {
        return theString;
    }

    @Override
    public int bytesToTransfer() {
        return theString.length();
    }
}
