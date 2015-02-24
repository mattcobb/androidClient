package net.inbetween.receivers;

import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.services.ServiceEvent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class PhoneState extends PhoneStateListener
{
   private boolean inProgress = false;
   private LinkedBlockingQueue<ServiceEvent> eventQ;
   
   public PhoneState(LinkedBlockingQueue<ServiceEvent> inEventQ)
   {
     eventQ = inEventQ;      
   }
   
   public boolean callInProgress()
   {
      return inProgress;
   }

   
   @Override
   public void onCallStateChanged(int state, String incomingNumber) {
       if(TelephonyManager.CALL_STATE_RINGING == state ||
          TelephonyManager.CALL_STATE_OFFHOOK == state)
       {
          inProgress = true;
       } else {
          inProgress = false;
          try {
             eventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_USER_READY, null));
          } catch (Exception qException) {};
       }
   }
}