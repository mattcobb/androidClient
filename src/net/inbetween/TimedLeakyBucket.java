package net.inbetween;

public class TimedLeakyBucket {
	private int bucketSize;
	private long replenishTime;
	private long changeTime;
	private int bucket;
	
	public TimedLeakyBucket(int bucketSize, long replenishTime) {
		this.bucketSize = bucketSize;
		this.replenishTime = replenishTime;
		bucket = bucketSize;
	}
	
	public boolean isEmpty() {
		return bucket == 0;
	}
	
	public boolean takeToken() {
		replenish();
		if(isEmpty()) {
			return false;
		} else {
			bucket--;
			changeTime = System.currentTimeMillis();
			return true;
		}
	}
	
	public boolean isFull() {
		return bucket == bucketSize;
	}
	
	public void replenish() {
		if(isFull()) return;
		
		long currentTime = System.currentTimeMillis();
		long timeDiff = currentTime - changeTime;
		
		int replenishTokens = (int) (timeDiff / replenishTime);
		long remainingTime = timeDiff % replenishTime;
		
		if(replenishTokens > 0) {
			bucket += replenishTokens;
			if(bucket > bucketSize) bucket = bucketSize;
			
			changeTime = currentTime - remainingTime;
		}
	}
	
	public int getBucket() {
		return bucket;
	}
	
	public int getBucketSize() {
		return bucketSize;
	}
	
	public void setBucketSize(int newBucketSize) {
		bucketSize = newBucketSize;
		if(bucket > bucketSize) {
			bucket = bucketSize;
		}
	}
	
	public void setReplenishTime(long newReplenishTime) {
		replenishTime = newReplenishTime;
	}
	
	public long getReplenishTime() {
		return replenishTime;
	}
}
