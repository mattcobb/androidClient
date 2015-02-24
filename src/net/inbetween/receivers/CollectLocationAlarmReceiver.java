package net.inbetween.receivers;

import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.proximity.LocationEvent;
import net.inbetween.services.WishRunner;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class CollectLocationAlarmReceiver extends BroadcastReceiver
{
   private LinkedBlockingQueue<LocationEvent> locEventQ;
   private PowerManager powerManager;
   
   public CollectLocationAlarmReceiver(LinkedBlockingQueue<LocationEvent> inEventQ, PowerManager powerManager)
   {
      locEventQ = inEventQ;
      this.powerManager = powerManager;
   }
   
   @Override
   public void onReceive(Context context, Intent intent)
   {
      try
      {
         PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "net.inbetween.proximity");
         locEventQ.add(new LocationEvent(LocationEvent.LocEventType.valueOf(intent.getStringExtra("net.inbetween.receivers.EVENT_TYPE")),
               null, wakeLock)); 
      }
      catch (Exception ePut) {
         WishRunner.startSelf(context);
      }
      return;
   }
}