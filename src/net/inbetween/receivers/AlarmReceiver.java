package net.inbetween.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

public class AlarmReceiver extends BroadcastReceiver
{
   PendingIntent pendingIntent;
   Context context;
   
   public AlarmReceiver(Context inContext, String action, Bundle bundle, long wakeInMsec)
   {
      IntentFilter eventFilter = new IntentFilter(action);
      
      context = inContext;
      
      context.registerReceiver((BroadcastReceiver) this, eventFilter);
      
      Intent eventIntent = new Intent(action);
      eventIntent.putExtras(bundle);
   
      pendingIntent = PendingIntent.getBroadcast(context, 0, eventIntent,  
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_CANCEL_CURRENT);
      
      AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
      
      alarmManager.set(AlarmManager.RTC_WAKEUP,  System.currentTimeMillis() + wakeInMsec, pendingIntent);      
   }
   
   public void clear()
   {
      AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
      if(pendingIntent != null) {
         alarmManager.cancel(pendingIntent);
         pendingIntent = null;
      
         context.unregisterReceiver((BroadcastReceiver) this);
      }
   }
   
   @Override
   public void onReceive(Context rcvContext, Intent rcvIntent)
   {
      try
      {
         doWork(rcvContext, rcvIntent);
      }
      catch (Exception ePut) {
      }
      return;
   }

   
   // Override this function for your work...
   public void doWork(Context context, Intent intent) {
      
   }
   
}