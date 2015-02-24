package net.inbetween.sectcp;

public class throttleReconnect {
    private double lbInterval; // in seconds
    private int maxItemsPerLbInterval;
    
    private long currentIntervalStartTime;
    private int currentItemsUsedCount;
	
    public throttleReconnect(int _maxItemsPerLbInterval,  int _lbIntervalMinutes){
        maxItemsPerLbInterval = _maxItemsPerLbInterval;
        lbInterval =  _lbIntervalMinutes * 60 * 1000;
        
        currentIntervalStartTime = 0;
        currentItemsUsedCount = 0;
    }
   
    private void refillCheck(){
       long currentTime = System.currentTimeMillis();
        
        if ( (currentTime - currentIntervalStartTime ) > lbInterval){
            currentIntervalStartTime = currentTime;
            currentItemsUsedCount = 0;
        }
    }

    public void take() {
        this.refillCheck();
        
        if (currentItemsUsedCount < maxItemsPerLbInterval){
            currentItemsUsedCount ++;
        }
    }

    // to report stats
    public int itemsLeft(){
        this.refillCheck();
        return (maxItemsPerLbInterval - currentItemsUsedCount);
    }  
    
}
