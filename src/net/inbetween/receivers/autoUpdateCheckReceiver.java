package net.inbetween.receivers;

import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.autoUpdate.updateEvent;
import net.inbetween.log.LogEntry;
import net.inbetween.log.LogProducer;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class autoUpdateCheckReceiver extends AlarmReceiver
{
   
   private LinkedBlockingQueue<updateEvent> autoUpdateQ;
   private LogProducer logger;
     
   public autoUpdateCheckReceiver(Context context, String action,
         Bundle bundle, long wakeInMsec, 
         LinkedBlockingQueue<updateEvent> _autoUpdateQ, LogProducer _logger)
   {
      super(context, action, bundle, wakeInMsec);
      autoUpdateQ = _autoUpdateQ;
      logger = _logger;
   }
   
   @Override
   public void doWork(Context context, Intent intent)
   {
      try
      {
         if(logger!=null) logger.log(LogEntry.SEV_INFO, 8, "auto update alarm fired");
         if (autoUpdateQ != null){
        	 autoUpdateQ.put(new updateEvent(updateEvent.UpdateEventType.CHECK_FOR_WEB_UPDATE, null, false)); 	 
         }
      }
      catch (Exception shutdownEx) {
         logger.log(LogEntry.SEV_ERROR, 2, "Could not tell auto update to check for updates : " + shutdownEx.toString());
      }
      return;
   }
}