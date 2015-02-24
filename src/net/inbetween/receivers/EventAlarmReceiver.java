package net.inbetween.receivers;

import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.log.LogEntry;
import net.inbetween.log.LogProducer;
import net.inbetween.services.ServiceEvent;
import net.inbetween.services.ServiceEvent.EventType;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class EventAlarmReceiver extends AlarmReceiver
{
   private LinkedBlockingQueue<ServiceEvent> serviceEventQ;
   private LogProducer logger;
   
   public EventAlarmReceiver(
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
      ServiceEvent.EventType eventType = EventType.EVENT_UNKNOWN;
      Bundle eventBundle;
      Object info;
      
      try
      {
         eventBundle = intent.getExtras();
         eventType = EventType.valueOf(eventBundle.getString("net.inbetween.receivers.EVENT_TYPE"));
   
         info = eventBundle.getString("net.inbetween.receivers.EVENT_INFO");
         logger.log(LogEntry.SEV_INFO, 8, "Event Alarm Receiver called with: " + eventType.name() + " " + 
            (String) info);
         
         serviceEventQ.put(new ServiceEvent(eventType, info));
      }
      catch (Exception ePut) {
         logger.log(LogEntry.SEV_INFO, 1, "Alarm work failed for " + eventType.toString() + " " + ePut.toString());
      }
      return;
   }
}
