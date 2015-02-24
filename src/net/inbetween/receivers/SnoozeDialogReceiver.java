package net.inbetween.receivers;

import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.log.LogEntry;
import net.inbetween.log.LogProducer;
import net.inbetween.services.ServiceEvent;
import net.inbetween.services.ServiceEvent.EventType;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class SnoozeDialogReceiver extends AlarmReceiver
{
   private LinkedBlockingQueue<ServiceEvent> serviceEventQ;
   private LogProducer logger;
   
   public SnoozeDialogReceiver(
         Context context, String action, Bundle bundle, long wakeInMsec,
         LinkedBlockingQueue<ServiceEvent> inEventQ,
         LogProducer inLogger)
   {
      super(context, action, bundle, wakeInMsec);
      serviceEventQ = inEventQ;
      logger = inLogger;
   }
   
   @Override
   public void doWork(Context context, Intent intent)
   {
      ServiceEvent.EventType eventType;
      Bundle dialogBundle;
      try
      {
         dialogBundle = intent.getExtras();
         eventType = EventType.valueOf(dialogBundle.getString("net.inbetween.receivers.EVENT_TYPE"));
   
         logger.log(LogEntry.SEV_INFO, 8, "SnoozeDialogReceiver called");
         
         serviceEventQ.put(new ServiceEvent(eventType, dialogBundle));
      }
      catch (Exception ePut) {
      }
      return;
   }
}
