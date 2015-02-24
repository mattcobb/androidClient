package net.inbetween.log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import android.os.Environment;
import android.text.format.Time;
import android.util.Log;

import net.inbetween.services.WishRunner;
import net.inbetween.util.HttpPostRequest;
import net.inbetween.util.cloudAddress;

public class LogConsumer implements Runnable
{
   private LinkedBlockingQueue<LogEntry> logQueue; 
   private int maxLevel;
   private long uploadTimeout;
   File logPath;
   File logFile;
   OutputStream logStream;
   PrintStream printStream;
   File localDir;
   boolean external;
   WishRunner wrContext;
   boolean lastUploadSucceeded;
   /*
    * Intentionally set to a very low value
    * since for prod users, only errors are logged
    * and we want to know about them right away 
    * And for debug (for xwish devs) where everything
    * is logged we want to get that fast as well
    * 
    */
   private final long maxLogFileSize = 5;
   Time lastPush;
   String cloudUrl = null;
   
   public LogConsumer(int inLevel, LinkedBlockingQueue<LogEntry> inLogQueue,
         long inUploadTimeout, WishRunner inWrContext)
   {
      maxLevel = inLevel;
      logQueue = inLogQueue;
      uploadTimeout = inUploadTimeout;
      wrContext = inWrContext;
      localDir = wrContext.getFilesDir();
      lastUploadSucceeded = true;
      lastPush = new Time();
      
      cloudAddress caddr = wrContext.getCloudAddress();
      if (caddr != null){
    	   cloudUrl = caddr.getAgentLogUrl();
      }
      
		if (cloudUrl == null)
		{
		   String err = "Fatal. Unable to get URL for uploading logs";
	      Log.d("WishRunner", err);
	      speakDebug(err);
	   }
      
      openStream();
   }

   private boolean openStream()
   {
      try {
         if(logStream != null && printStream != null) {
            if(!printStream.checkError()) {
               return true;
            } else {
               printStream.close();
               try { logStream.close(); } catch (Exception cException) {};
               printStream = null;
               logStream = null;
            }
         }
         
         if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            external = true;
            logPath = Environment.getExternalStorageDirectory();
         } else {
            external = false;
            logPath = localDir;
         }
         logPath = new File(logPath, "XWish/");
         
         if(logPath != null) {
            logPath.mkdirs();
            logFile = new File(logPath, "wr.log");
            if(logFile != null) {
               try {
                  logStream = new FileOutputStream(logFile, true);
                  printStream = new PrintStream(logStream, true);
               }
               catch (Exception streamException) {
                  Log.d("WishRunner", streamException.toString());
                  speakDebug("Exception opening log stream");
                  return false; 
               }
            } else { 
               speakDebug("Could not open log file");
               return false;
            }
         } else { 
            speakDebug("Could not get log path");
            return false;
         }
         
         return true;
      } catch (Exception openException) {
         Log.d("WishRunner", "LogConsumer.openStream() " + openException.toString());
         speakDebug("Exception in open stream");
         return false;
      }
   }

   private void logToFile(LogEntry entry)
   {
      JSONObject jsonMsg;
      
      if(openStream()) {
         jsonMsg = new JSONObject();
         
         try {
            entry.getEntryTime().switchTimezone("UTC");
            jsonMsg.put(LogEntry.KEY_TIME, (Object) entry.getEntryTime().format3339(false));
            jsonMsg.put(LogEntry.KEY_LEVEL, entry.getLevel());
            jsonMsg.put(LogEntry.KEY_SEVERITY, (Object) entry.getSeverity());
            jsonMsg.put(LogEntry.KEY_MSG, entry.getMessage().replace('\n', ' '));
            
            printStream.println(jsonMsg.toString());
            if(printStream.checkError()) {
               printStream = null;
               logStream = null;
            } else if (logFile.length() > maxLogFileSize && lastUploadSucceeded) {
               lastUploadSucceeded = uploadLog();
            }
         } catch (Exception jsonException) {
            Log.d("WishRunner", "LogConsumer.logToFile() " + jsonException.toString());
            speakDebug("Exception in log to file");
         }
      }
      return;
   }

   private boolean uploadLog()
   {
      boolean result = false;
      String ticket;

      if(logFile==null || logFile.length()<=0) {
         result=true;
         return result;
      }
      
      try {
         try { if(printStream != null) printStream.close(); } catch (Exception pstreamException) {};
         try { if(logStream!=null) logStream.close(); } catch(Exception streamException) {};
         printStream = null;
         logStream = null;
         
         ticket = wrContext.getTicket();
         
         if(ticket!=null) {
            HttpPostRequest post = new HttpPostRequest(ticket, 
                  wrContext.getAppContext().getString(net.inbetween.webview.R.string.app_name));
            
            if(post != null) {
               try {           	  
            	  if (cloudUrl != null){           		  
                      result = post.sendPost(cloudUrl, logFile.getAbsolutePath());
            	  }else{
            		  String err = "Fatal. Unable to get URL for uploading logs";
                      Log.d("WishRunner", err);
                      speakDebug(err);
            	  }
               } 
               catch (Exception postException) {
                  Log.d("WishRunner", "Upload log go exception: " + postException.toString());
                  speakDebug("Unable to upload log");
               }
            }
         } 
   
         if(result==true || logFile.length() > maxLogFileSize)
         {
            File backupFile;
            
            backupFile = new File(logFile.getAbsolutePath() + "_old");
            if(backupFile!=null) {
               logFile.renameTo(backupFile);
            }
            logFile = null;
         }
         
         if(result == true) {
            lastPush.setToNow();
         }
      } catch (Exception uploadException) {
         Log.d("WishRunner", "LogConsumer.uploadLog() exception: " + uploadException.toString());
         speakDebug("Exception uploading logfile");
      }
      return result;
   }
   
   @Override
   public void run()
   {
      LogEntry entry;
      Time now = new Time();
      lastPush = new Time();

      now.setToNow();
      lastPush.setToNow();

      while(true) {
         try {
            entry = logQueue.poll(uploadTimeout, TimeUnit.SECONDS);
            if(entry != null && entry.getLevel() <= maxLevel) {
               logToFile(entry);
            }
            now.setToNow();
            if((now.toMillis(false) - lastPush.toMillis(false)) >= uploadTimeout*1000) {
               lastUploadSucceeded = uploadLog();
            }
         } catch(Exception ex) {
            Log.d("WishRunner", "LogConsumer.run exception: " + ex.toString());
            speakDebug("Exception getting log entry");
         };            
      }
   }
   
   private void speakDebug(String message)
   {
      if(wrContext!=null) {
         wrContext.speakDebug(message);
      }
   }
}
