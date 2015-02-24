package net.inbetween.receivers;

import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.services.ServiceEvent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.util.Log;

public class ProximityReceiver extends BroadcastReceiver {

   public static final String PROXIMITY_ID_INTENT_EXTRA = "ProximityID_IntentExtraKey";

   private LinkedBlockingQueue<ServiceEvent> directorEventQ;
   
   @Override
   public void onReceive(Context context, Intent intent) {
      boolean entering;
      ServiceEvent event;
      String name = intent.getStringExtra(PROXIMITY_ID_INTENT_EXTRA);
      
      entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, true);
      
      if(entering) { 
         event = new ServiceEvent(ServiceEvent.EventType.EVENT_LOCATION_ENTERED, name + "_ENTERED");
      } else {
         event = new ServiceEvent(ServiceEvent.EventType.EVENT_LOCATION_DEPARTED, name + "_DEPARTED");
      }
      Log.d("WishRunner", "Proximity received: " + name);
      try
      {
         directorEventQ.put(event);
      }
      
      catch (Exception ePut) {};
   }
   
   public void setQueue(LinkedBlockingQueue<ServiceEvent> inEventQueue)
   {
      directorEventQ = inEventQueue;
   }
}