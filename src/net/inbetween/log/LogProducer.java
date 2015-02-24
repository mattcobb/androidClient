package net.inbetween.log;

import java.util.concurrent.LinkedBlockingQueue;

import android.util.Log;

import net.inbetween.log.LogEntry;

public class LogProducer {
   // used to specify policy for what severity should be logged 
   public static final int POL_SEV_ERROR = 0x1000;
   public static final int POL_SEV_WARNING = 0x2000;
   public static final int POL_SEV_INFO = 0x4000;
		
   private LinkedBlockingQueue<LogEntry> logQueue;
   private int maxLevel;
   boolean privateMode;
   int severityPol;
     
   public LogProducer(int inLogLevel, LinkedBlockingQueue<LogEntry> inLogQueue, int inSeverityPol) {
      maxLevel = inLogLevel;
      logQueue = inLogQueue;
      privateMode = false;
      severityPol = inSeverityPol;
   }
   
   public void setSeverityPolicy(int inSeverityPol){
	   severityPol = inSeverityPol;
   }

   public boolean isSeverityPolicy (int inSeverityPol){
	   return (inSeverityPol & severityPol) == inSeverityPol;
   }
   
   public void setPrivate(boolean inPrivate)
   {
      privateMode = inPrivate;
   }
   
   public void log(String severity, int level, String msg) {
	  boolean loggingAllowed = false;
	  
	  if (severity.compareTo(LogEntry.SEV_ERROR) == 0){
         loggingAllowed = this.isSeverityPolicy(POL_SEV_ERROR);
	  }else if (severity.compareTo(LogEntry.SEV_WARNING) == 0){
	     loggingAllowed = this.isSeverityPolicy(POL_SEV_WARNING);
	  }else if (severity.compareTo(LogEntry.SEV_INFO) == 0){
	     loggingAllowed = this.isSeverityPolicy(POL_SEV_INFO);
	  }	   		  
	   
      if(loggingAllowed && !privateMode && level <= maxLevel) {
         try {
            logQueue.put(new LogEntry(severity, level, msg));
         } catch (Exception qException) {
            Log.d("bad", "log queue failed" + qException.getMessage());
         };
      }
   }
}
