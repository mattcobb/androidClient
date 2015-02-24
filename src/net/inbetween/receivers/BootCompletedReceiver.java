package net.inbetween.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootCompletedReceiver extends BroadcastReceiver
{
   @Override
   public void onReceive(Context context, Intent intent)
   {
      if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED))
      {
         Intent serviceIntent = new Intent();
         serviceIntent.setAction("net.inbetween.services.WishRunner");
         context.startService(serviceIntent);
      }
   }
}