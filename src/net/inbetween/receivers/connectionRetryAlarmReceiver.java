package net.inbetween.receivers;

import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.log.LogEntry;
import net.inbetween.log.LogProducer;
import net.inbetween.sectcp.tcpLogicConnectionEvent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class connectionRetryAlarmReceiver extends AlarmReceiver
{
   private LinkedBlockingQueue <tcpLogicConnectionEvent>  logicalConnectionEventsQ;
   private LogProducer logger;
     
   public connectionRetryAlarmReceiver(Context context, String action,
         Bundle bundle, long wakeInMsec, 
         LinkedBlockingQueue <tcpLogicConnectionEvent>  _logicalConnectionEventsQ, LogProducer _logger)
   {
      super(context, action, bundle, wakeInMsec);
      logicalConnectionEventsQ = _logicalConnectionEventsQ;
      logger = _logger;
   }
   
   @Override
   public void doWork(Context context, Intent intent)
   {
      try
      {
         if(logger!=null) logger.log(LogEntry.SEV_INFO, 8, "connection retry alarm fired");
         if (logicalConnectionEventsQ != null){
        	 // tell the tcp writter thread to go and check on whether an ack has been received
        	 logicalConnectionEventsQ.put(new tcpLogicConnectionEvent(
        			 tcpLogicConnectionEvent.logicalConnEvent.CONN_RETRY_ALARM));
         }
 
      }
      catch (Exception shutdownEx) {
         logger.log(LogEntry.SEV_ERROR, 2, "Could not tell connection manager to retry connnection: " + shutdownEx.toString());
      }
      return;
   }
}