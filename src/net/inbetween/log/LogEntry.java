package net.inbetween.log;

import android.text.format.Time;

public class LogEntry
{
   private String severity;
   private int level;
   private String message;
   private Time entryTime;
   
   public static final String SEV_ERROR = "error";
   public static final String SEV_WARNING = "warn";
   public static final String SEV_INFO = "info";
   
   public static final String KEY_TIME = "time";
   public static final String KEY_SEVERITY = "severity";
   public static final String KEY_MSG = "msg";
   public static final String KEY_LEVEL = "debugLevel";
   
   public LogEntry(String inSeverity, int inLevel, String inMessage) {
      level = inLevel;
      message = inMessage;
      severity = inSeverity;
      entryTime = new Time();
      entryTime.setToNow();
   }
   
   public String getSeverity() { return severity; }
   
   public int getLevel() { return level; }
   
   public String getMessage() { return message; }
   
   public Time getEntryTime() { return entryTime; }
}
