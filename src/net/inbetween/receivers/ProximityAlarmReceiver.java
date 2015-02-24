package net.inbetween.receivers;

import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.log.LogEntry;
import net.inbetween.log.LogProducer;
import net.inbetween.proximity.LocationEvent;
import net.inbetween.proximity.LocationEvent.*;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class ProximityAlarmReceiver extends AlarmReceiver
{
   private LinkedBlockingQueue<LocationEvent> eventQ;
   private LogProducer logger;
   
   public ProximityAlarmReceiver(Context context, String action, Bundle bundle, long wakeInMsec,
         LinkedBlockingQueue<LocationEvent> inEventQ, LogProducer inLogger)
   {
      super(context, action, bundle, wakeInMsec);
      eventQ = inEventQ;
      logger = inLogger;
   }
   
   @Override
   public void doWork(Context context, Intent intent)
   {
      LocEventType eventType;
      Bundle eventBundle;
      Object extra;
      
      try
      {
         eventBundle = intent.getExtras();
         eventType = LocEventType.valueOf(eventBundle.getString("net.inbetween.receivers.EVENT_TYPE"));
   
         extra = eventBundle.getString("net.inbetween.receivers.EVENT_EXTRA");
         //if(extra == null) extra="";
         
         logger.log(LogEntry.SEV_INFO, 8, "Proximity Alarm Receiver called with: " + eventType.name() + " " + 
            (String) extra);
         
         eventQ.add(new LocationEvent(eventType, extra));
      }
      catch (Exception ePut) {
      }
      return;
   }
}