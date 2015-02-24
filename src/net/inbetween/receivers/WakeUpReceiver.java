package net.inbetween.receivers;

import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.services.ServiceEvent;
import net.inbetween.services.WishRunner;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public class WakeUpReceiver extends BroadcastReceiver
{
   private LinkedBlockingQueue<ServiceEvent> serviceEventQ;
   
   public WakeUpReceiver(LinkedBlockingQueue<ServiceEvent> inEventQ)
   {
      serviceEventQ = inEventQ;
   }
   
   @Override
   public void onReceive(Context context, Intent intent)
   {
      try
      {
         serviceEventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_PHONE_HOME_TIMER, "Alarm")); 
      }
      catch (Exception ePut) {
         WishRunner.startSelf(context);
      }
      SystemClock.sleep(1000);
      return;
   }
}
