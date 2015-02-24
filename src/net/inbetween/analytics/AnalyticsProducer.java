package net.inbetween.analytics;

import java.util.concurrent.LinkedBlockingQueue;

import org.json.JSONObject;

import android.util.Log;

public class AnalyticsProducer {
	private LinkedBlockingQueue<JSONObject> logQueue;
	
	public AnalyticsProducer(LinkedBlockingQueue<JSONObject> logQueue) {
		this.logQueue = logQueue;
	}
	
	public void send(JSONObject newEntry) {
		try {
			logQueue.put(newEntry);
		} catch (Exception e) {
			Log.d("XW Analytics", "log queue failed" + e.getMessage());
		}
	}
	
	public boolean sendWV(String analyticsObjString) {
		try {
			JSONObject analyticsObj = new JSONObject(analyticsObjString);
			send(analyticsObj);
		} catch (Exception e) {
			Log.d("XW Analytics", "Malformed analytics object from webview: " + e.getMessage());
			return false;
		}
		
		return true;
	}
}
