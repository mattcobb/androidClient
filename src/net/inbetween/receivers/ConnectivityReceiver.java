package net.inbetween.receivers;

import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.sectcp.tcpLogicConnectionEvent;
import net.inbetween.services.ServiceEvent;
import net.inbetween.services.WishRunner;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class ConnectivityReceiver extends BroadcastReceiver
{
   
   private LinkedBlockingQueue<tcpLogicConnectionEvent> secureTcpEventQ;
   private boolean wifiConnected;
   
   public ConnectivityReceiver(LinkedBlockingQueue<tcpLogicConnectionEvent> in_secureTcpEventQ)
   {
	   secureTcpEventQ = in_secureTcpEventQ;
	   wifiConnected = false;
   }
   
   @Override
   public void onReceive(Context context, Intent intent)
   {
      try
      {
         secureTcpEventQ.put(
    		   new tcpLogicConnectionEvent(tcpLogicConnectionEvent.logicalConnEvent.NETWORK_STATUS, WishRunner.connectivityAvailable(context)));
      }
      catch (Exception ePut) {};
      
      //Notifiy Webview
      NetworkInfo netInfo = (NetworkInfo) ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
      if(netInfo == null || netInfo.getType() != ConnectivityManager.TYPE_WIFI || !netInfo.isConnected()) { //No active network
    	  if(wifiConnected) {
    		  wifiConnected = false;
    		  ServiceEvent.broadcastServiceEvent(context, ServiceEvent.EventType.EVENT_WIFI_DISCONNECTED);
    	  }
      } else {
		  if(!wifiConnected) {
    		  wifiConnected = true;
    		  ServiceEvent.broadcastServiceEvent(context, ServiceEvent.EventType.EVENT_WIFI_CONNECTED);
		  }
      }
      
      return;
   }
}
