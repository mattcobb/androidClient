package net.inbetween.analytics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.services.WishRunner;
import net.inbetween.util.HttpPostRequest;
import net.inbetween.util.cloudAddress;

import org.json.JSONObject;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class AnalyticsConsumer implements Runnable {
	private LinkedBlockingQueue<JSONObject> logQueue; 
	private File analyticsFile;
	private PrintStream printStream;
	private File localDir;
	private WishRunner wrContext;
	private final long maxAnalyticsFileSize = 204800;
	private String cloudUrl = null;
	private ConnectivityManager connManager;
	private String fileName = "analytics.log";
	private String secondFileSuffix = "_old";
	
	
	public AnalyticsConsumer(WishRunner wrContext, LinkedBlockingQueue<JSONObject> logQueue) {
		this.logQueue = logQueue;
		this.wrContext = wrContext;
		localDir = wrContext.getFilesDir();
		connManager = (ConnectivityManager) wrContext.getSystemService(Context.CONNECTIVITY_SERVICE);
	      
		cloudAddress caddr = wrContext.getCloudAddress();
		if (caddr != null){
			cloudUrl = caddr.getAnalyticsBaseUrl();
		}
		
		if (cloudUrl == null){
			String err = "Fatal. Unable to get URL for uploading logs";
			Log.d("WishRunner", err);
			speakDebug(err);
			return;
		}
	      
		openStream();
	}

	private boolean openStream() {
		try {
			if(printStream != null) {
				if(!printStream.checkError()) {
					return true;
				} else {
	            	printStream.close();
	            	printStream = null;
				}
			}
			
			File analyticsPath = new File(localDir, "analytics/");
			analyticsPath.mkdirs();
			analyticsFile = new File(analyticsPath, fileName);
			
			
			try {
				FileOutputStream analyticsStream = new FileOutputStream(analyticsFile, true);
				printStream = new PrintStream(analyticsStream, true);
				return true;
			} catch (Exception streamException) {
				Log.d("WishRunner", streamException.getMessage());
				speakDebug("Exception opening analytics stream");
			}
		} catch (Exception openException) {
			Log.d("WishRunner", "LogConsumer.openStream() " + openException.getMessage());
			speakDebug("Exception in open stream");
		}
		
		return false;
	}

	private void saveToFile(JSONObject entry) {
		if(entry == null) return;
		
		//Note: moves first to ensure that analyticsFile is only empty if there were no analytics
		if(analyticsFile != null && analyticsFile.length() > maxAnalyticsFileSize) {
			moveToSecondFile();
		}
		
		if(openStream()) {
			try {
				printStream.println(entry.toString());
				
				if(printStream.checkError()) {
					printStream = null;
				}
			} catch (Exception jsonException) {
				Log.d("WishRunner", "LogConsumer.logToFile() " + jsonException.toString());
				speakDebug("Exception in log to file");
			}
		}
	}
	
	private boolean uploadFile(String filePath, String ticket) {
		boolean success = false;
		
		if(ticket != null) {
			String appName = wrContext.getString(net.inbetween.webview.R.string.app_name);
			HttpPostRequest post = new HttpPostRequest(ticket, appName);

			try {
				if(cloudUrl != null) {
					success = post.sendPost(cloudUrl + "/chunk", filePath);
				} else {
					String err = "Fatal. Unable to get URL for uploading logs";
					Log.d("WishRunner", err);
					speakDebug(err);
				}
			} catch (Exception postException) {
				Log.d("WishRunner", "Upload log go exception: " + postException.toString());
				speakDebug("Unable to upload log");
			}
		}
		
		return success;
	}
	
	private boolean uploadFiles() {
		if(analyticsFile == null || analyticsFile.length() <= 0) {
			return true;
		}
		
		boolean success = false;
		
		if(printStream != null) {
			printStream.flush();
			printStream.close();
			printStream = null;
		}
		
		try {
			
			String ticket = wrContext.getTicket();
			String secondFilePath = analyticsFile.getAbsolutePath() + secondFileSuffix;
			File secondFile = new File(secondFilePath);
			
			boolean secondSuccess = true;
			if(secondFile.length() > 0) {
				secondSuccess = uploadFile(secondFilePath, ticket);
			}
			
			if(secondSuccess) {
				secondFile.delete();
				success = uploadFile(analyticsFile.getAbsolutePath(), ticket);
			}
			
			if(success) {
				analyticsFile.delete();
				analyticsFile = null;
			}
			
		} catch (Exception uploadException) {
			Log.d("WishRunner", "LogConsumer.uploadLog() exception: " + uploadException.toString());
			speakDebug("Exception uploading logfile");
		}
		
		return success;
	}
	
	private boolean moveToSecondFile() {
		printStream.flush();
		printStream.close();
		printStream = null;
		File newFile = new File(analyticsFile.getAbsoluteFile() + secondFileSuffix);
		return analyticsFile.renameTo(newFile);
	}
	
	private boolean sendAnalytics(JSONObject entry) {
		if(entry == null) return true;
		
		boolean result = false;
		String ticket = wrContext.getTicket();
		
		if(ticket != null) {
			String appName = wrContext.getString(net.inbetween.webview.R.string.app_name);
			HttpPostRequest post = new HttpPostRequest(ticket, appName);
			
			try {
				if(cloudUrl != null) {
					result = post.sendContentPost(cloudUrl + "/single", entry.toString(), "application/json");
				} else {
					String err = "Fatal. Unable to get URL for uploading logs";
					Log.d("WishRunner", err);
					speakDebug(err);
				}
			} catch (Exception postException) {
				Log.d("WishRunner", "Upload log go exception: " + postException.toString());
				speakDebug("Unable to upload log");
			}
		}
		
		return result;
	}
	   
	@Override
	public void run() {
		JSONObject entry;
		
		while(true) {
			try {
				entry = logQueue.take();
				NetworkInfo wifiInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

				if (wifiInfo.isConnected()) {
					uploadFiles();
					
					boolean sent = sendAnalytics(entry);
					if(!sent) {
						saveToFile(entry);
					}
				} else {
					saveToFile(entry);
				}
			} catch(Exception ex) {
				Log.d("WishRunner", "LogConsumer.run exception: " + ex.toString());
				speakDebug("Exception getting log entry");
			}
		}
	}
	
	private void speakDebug(String message) {
		if(wrContext!=null) {
			wrContext.speakDebug(message);
		}
	}
}
