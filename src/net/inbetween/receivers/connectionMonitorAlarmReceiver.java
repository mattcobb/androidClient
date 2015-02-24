package net.inbetween.receivers;

import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.log.LogEntry;
import net.inbetween.log.LogProducer;
import net.inbetween.sectcp.tcpChannelControlEvent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class connectionMonitorAlarmReceiver extends AlarmReceiver
{
   private LinkedBlockingQueue <tcpChannelControlEvent>  controlQ;
   private LogProducer logger;
   private String connectionId;
     
   public connectionMonitorAlarmReceiver(Context context, String action,
         Bundle bundle, String _connectionId, long wakeInMsec, 
         LinkedBlockingQueue <tcpChannelControlEvent>  _controlQ, LogProducer _logger)
   {
      super(context, action, bundle, wakeInMsec);
      controlQ = _controlQ;
      logger = _logger;
      connectionId = _connectionId;
   }
   
   @Override
   public void doWork(Context context, Intent intent)
   {
      try
      {
         if(logger!=null) logger.log(LogEntry.SEV_INFO, 8, "connection Monitor alarm fired");
         if (controlQ != null){
        	 // tell the tcp writter thread to go and check on whether an ack has been received
             controlQ.put(new tcpChannelControlEvent(connectionId, 
         		    tcpChannelControlEvent.controlEvent.CHECKACK));
         }
 
      }
      catch (Exception shutdownEx) {
         logger.log(LogEntry.SEV_WARNING, 2, "Could not tell tcp writer thread to check on a potentially stale socket: " + shutdownEx.toString());
      }
      return;
   }
}