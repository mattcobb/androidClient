package net.inbetween.sectcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
//import javax.net.ssl.TrustManager;
//import javax.net.ssl.X509TrustManager;

import net.inbetween.log.LogEntry;
import net.inbetween.log.LogProducer;
import net.inbetween.receivers.connectionMonitorAlarmReceiver;
import net.inbetween.receivers.connectionRetryAlarmReceiver;
import net.inbetween.services.ServiceEvent;
import net.inbetween.services.WishRunner;
import net.inbetween.webview.R;
import android.content.Context;
import android.os.Bundle;
import java.net.InetAddress;

public class tcpClientV3
{
   private static final String ACK_NEEDED = "1", ACK_NOT_NEEDED = "0";

   // used by the reader thread to communicate messages from the TCP portal to the Wish runner service
   private LinkedBlockingQueue<ServiceEvent> serviceQ = null;
   // used to communicate with the connectivity manager thread
   private LinkedBlockingQueue<tcpLogicConnectionEvent> logicalConnectionEventsQ = null;
   
   /*
    * internal queues created by this class
    */
   private LinkedBlockingQueue<tcpChannelControlEvent> writerControlQ = null;
   private LinkedBlockingQueue<tcpChannelControlEvent> readerControlQ = null;
   // the data Q used to send messages from the wishrunner services to the TCP portal
   private LinkedBlockingQueue<String> toServerDataQ = null;

   String hostName;
   int portNum;
   int maxTcpMsgSize;
   // Header format: version[1 byte] ack [1 byte] [value]
   // HEADER_VERSION_SIZE | HEADER_ACK_SIZE | HEADER_TOTAL_LEN_SIZE
   int HEADER_TOTAL_LEN_SIZE = 10;
   int HEADER_VERSION_SIZE = 1;
   int HEADER_ACK_SIZE = 1;
   int HEADER_PAYLOAD_SIZE = (HEADER_TOTAL_LEN_SIZE - (HEADER_VERSION_SIZE + HEADER_ACK_SIZE));

   String currentProtocolVersion = "1";
   SSLSocket sslsock = null;

   long lastSuccessfulConnectionTime = 0;
   int lastConnectionAttempt = 0;
   // once the ack req is sent out, this is the period after which the
   // connection is closed unless the server acks (in milliseconds)
   int staleSocketInterval;
   int retryInterval;
   WishRunner wRunner;
   Context context;
   LogProducer logger;
   private connectionMonitorAlarmReceiver monitorAlarm;
   private connectionRetryAlarmReceiver reconnectAlarm;
   

   
   // put this in strings:
   int TCP_RETRIES_PER_LB_PERIOD = 5;
   int TCP_RETRY_LB_PERIOD_MIN = 2;
   
   /*
    *  constructor
    */
   public tcpClientV3(String _hostName, int _portNum,
		 WishRunner _wRunner,  
         LinkedBlockingQueue<ServiceEvent> _serviceQ, 
         LinkedBlockingQueue<tcpLogicConnectionEvent> _logicalConnectionEventsQ, 
         Context _context, LogProducer _logger)
   {
	  
      hostName = _hostName;
      portNum = _portNum;
      wRunner = _wRunner;
      context = _context;
      logger = _logger;
      
      // v3: new queue used by connectivity manager
      logicalConnectionEventsQ = _logicalConnectionEventsQ;
      serviceQ = _serviceQ;
      
      // allocate all internal queues used by the tcpClient
      writerControlQ =  new LinkedBlockingQueue<tcpChannelControlEvent>(); 
      readerControlQ = new LinkedBlockingQueue<tcpChannelControlEvent>();
      toServerDataQ = new LinkedBlockingQueue<String>();
      
      staleSocketInterval =  Integer.parseInt( wRunner.getString(R.string.staleSocketInterval));
      retryInterval =  Integer.parseInt( wRunner.getString(R.string.retryInterval));
      maxTcpMsgSize =  Integer.parseInt( wRunner.getString(R.string.maxTcpMsgSize));
      sslsock = null;
      lastConnectionAttempt = 0;
      
      // start all the threads
      connectivityManager();
      reader();
      writer();
   }
   
   public long getLastConnectionTime(){
	   return lastSuccessfulConnectionTime;
   }

   /*
    *  Create a trust manager that does not validate certificate chains,
    *  TODO: remove this when we get a real certificate - DONE!
    */
   /*
   TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager()
   {
      public java.security.cert.X509Certificate[] getAcceptedIssuers()
      {
         return null;
      }
      public void checkClientTrusted(
            java.security.cert.X509Certificate[] certs, String authType)
      {}
      public void checkServerTrusted(
            java.security.cert.X509Certificate[] certs, String authType)
      {}
   } };
   */

   /*
    * sendMessage
    * This method should only be called by wish runner service
    */
   public void sendMessage(String msg) throws InterruptedException {  
	   toServerDataQ.put(msg);
       writerControlQ.put(new tcpChannelControlEvent(null, 
   		    tcpChannelControlEvent.controlEvent.SEND));   
   }

   /*
    * createConnection - a helper method used by connectivity manager
    * to establish a connection with the server
    */
   private String createConnection(String tkt)
   {
      Socket socket = null;
      try
      {
    	 InetAddress Address = null;
         try
         {
        	 
           lastConnectionAttempt = (int) (System.currentTimeMillis());
            // Socket does tcp handshake
           Address = InetAddress.getByName(hostName);
           socket = new Socket(Address, portNum);
         } catch (Exception e)
         {
      	  String errMsg = "T open socket failed";
      	  wRunner.speakDebug(errMsg);
      	    // inability to open socket is common so log level is info
            logger.log(LogEntry.SEV_INFO, 4, errMsg + e.toString() + " ;target host=" 
      	    + hostName + " get by name address: " + Address);
            throw new RuntimeException(errMsg, e);
         }

         SSLContext sslcontext = SSLContext.getInstance("TLS");
         
         // removing trustAllCerts, which means that the client will get the default cert trust/policy on the platform
         // sslcontext.init(null, trustAllCerts, null);
         sslcontext.init(null, null, null);
         SSLSocketFactory sf = sslcontext.getSocketFactory();
         
         // createSocket does SSL handshake
         sslsock = (SSLSocket) sf.createSocket(socket, socket.getInetAddress()
               .getHostName(), socket.getPort(), true);
         if(sslsock != null)
         {      
            sslsock.setUseClientMode(true);
            wRunner.speakDebug("T connected");
            // send auth token first
            
            PrintWriter out =  new PrintWriter(sslsock.getOutputStream());
            writeMsgToChannel(out, tkt, ACK_NOT_NEEDED);
            // out.close();
            wRunner.speakDebug("T sent ticket");          
            serviceQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_SERVER_CONNECTED,  null));
            
            
            return genConnectionId(); 
         }
         else {
            try
            {
               if(socket!=null) {
                  if(socket.isConnected()) {
                     socket.close();
                  }
                  socket = null;
               }
            } catch (Exception putException) { };
            String errMsg = "T failed SSL";
            wRunner.speakDebug(errMsg);
            logger.log(LogEntry.SEV_ERROR, 4, errMsg );
            
           // serviceQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_CONNECTION_SHUTDOWN_REQUEST, 
           //    new tcpChannelPayload(connectionId, errMsg)));
         }
      } catch (Exception e)
      {
         try
         {
            if (socket != null)
            {
               if(socket.isConnected())
               {
                  socket.close();
               }
               socket = null;
            }
            // just trying to clean up. don't need to report this
            // exception if it happens
         } catch (Exception e2)
         {
         }
         // connection tare down is common so log it as info
         logger.log(LogEntry.SEV_INFO, 4, e.toString());
         if (sslsock != null)
         {
            try {
               sslsock.close();
            }
            catch (Exception closeException) {};
            sslsock = null;
         }      
      }


 	  try{
 		 wRunner.speakDebug("T event disconnected");
 	     serviceQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_DISCONNECTED,  null)); 
 	  }catch(Exception e){
    	      String errMsg = "T panic 14";
  	      wRunner.speakDebug(errMsg);
  	      logger.log(LogEntry.SEV_ERROR, 4, errMsg + e.toString());
 	  }

      // failed to get a connection, so will retry logic to decide when
      // to try again
 	  
      try { 	   
   	     logicalConnectionEventsQ.put(new tcpLogicConnectionEvent(
  	         tcpLogicConnectionEvent.logicalConnEvent.CONN_RETRY));
   	  
      } catch (Exception e1) {
   	     String errMsg = "T PANIC 1";
   	     wRunner.speakDebug(errMsg);
   	     logger.log(LogEntry.SEV_ERROR, 4, errMsg + e1.toString());
      };
        
      return null;
   }
    
   private void connShutdown()
   {
	 
	   /*
      if(monitorAlarm != null) {
         monitorAlarm.clear();
         monitorAlarm = null;
      }
      */
	   
      try
      {
         if (sslsock != null)
         {
            sslsock.close();
            sslsock = null;
         }
      } catch (Exception e)
      {
         if(sslsock!=null) {
            sslsock=null;
         }
      }
   }
   
   
   private String genConnectionId(){
       Random rand = new Random();
       long num = rand.nextLong();
       return (num + "");
   }
   
   private String reconnect(String tkt){
      connShutdown();
      String connectionId = createConnection(tkt);
 	  if (connectionId != null) {
 		  try{
			  // Tell the reader and the writer that they can proceed
 			  lastSuccessfulConnectionTime = System.currentTimeMillis();
 			 
	          writerControlQ.put(new tcpChannelControlEvent(connectionId,
	       		    tcpChannelControlEvent.controlEvent.WAKEUP)); 
	          readerControlQ.put(new tcpChannelControlEvent(connectionId,
		       		    tcpChannelControlEvent.controlEvent.WAKEUP)); 

 		  }catch(Exception e){
 			  
 			  lastSuccessfulConnectionTime = 0;
 			  connShutdown();
 			  connectionId = null;

       	      String errMsg1 = "T PANIC 2";
     	      wRunner.speakDebug(errMsg1);
     	      logger.log(LogEntry.SEV_ERROR, 4, errMsg1 + e.toString());		  
 		  }
 	  }  
	  
 	  return connectionId;
   }
   
   
   /*
    * connect
    * does tcp and ssl handshakes and creates reader and writer threads for this connection
    */
   private void connectivityManager()
   {  
      new Thread(new Runnable()
      {
         public void run()
         {
            tcpLogicConnectionEvent instruction;
            String connectionId = null;
            
            boolean connectionAllowed = true;
            // ticket to authenticate the user for this connection
            String tkt = null; 
            boolean outstandingTimer = false;
            
            throttleReconnect retryRate = new throttleReconnect(TCP_RETRIES_PER_LB_PERIOD, TCP_RETRY_LB_PERIOD_MIN);
            

            while(true){
	      	       // block on control queue
	               try {
					  instruction = logicalConnectionEventsQ.take();
				   } catch (InterruptedException e) {
               	      String errMsg1 = "T PANIC 3";
             	      wRunner.speakDebug(errMsg1);
             	      logger.log(LogEntry.SEV_ERROR, 4, errMsg1 + e.toString());
             	      return;
				   }
	               
	               switch(instruction.getControlEvent()){  
	               
	                  case NETWORK_STATUS:
	                	  boolean networkAvailable = instruction.getStatus();
	                	  
	                	  if (connectionAllowed && networkAvailable && (connectionId == null)){
		                      if (tkt != null && tkt.length() > 0){
		                    	  wRunner.speakDebug("T new network");
			                      connectionId = reconnect(tkt);
			                  }  
	                	  }            	  
	                	  break;
	               
	                  case CONN_RETRY_ALARM:
	            	   outstandingTimer = false;
	            	   
	            	   // no break.  This is intentional.
	                  case SOCKET_ERROR:
	                	  if (instruction.getControlEvent() == tcpLogicConnectionEvent.logicalConnEvent.SOCKET_ERROR){
		                	  wRunner.speakDebug("T monitor socket error");
		                	  String targetConnId = instruction.getPayload();
		                	  
		                	  if ((connectionId != null) && (targetConnId != connectionId)){
		                		  break;
		                	  }else{
		                		  connectionId = null;
		                	  }
	                	  }
	                	// no break.  This is intentional.
	                  case CONN_RETRY:
	                	  if (connectionAllowed && (connectionId == null) && 
	                		   (tkt !=null) && (tkt.length() > 0) ){ 
	            
	                		  if (retryRate.itemsLeft() > 0){
	                		  
			                	  int currentTime = (int) (System.currentTimeMillis());
	
			                	  if ( (currentTime - lastConnectionAttempt) > retryInterval){
			                		  connectionId = reconnect(tkt);
			                		  retryRate.take();	                				                		  
			                	  }else{	
			                		  
			                	      if (!outstandingTimer) {  
			                	    
			                				 if(reconnectAlarm != null) {
			                					reconnectAlarm.clear();
			                				    reconnectAlarm = null;
			                				 }
		
			                				 wRunner.speakDebug("T " + retryRate.itemsLeft() + 
			                						 " tries left");
			                				 outstandingTimer = true;
			                                 reconnectAlarm = new connectionRetryAlarmReceiver(context,  
			                                    "net.inbetween.reconnectAlarm", new Bundle(), retryInterval, logicalConnectionEventsQ, logger);		                			  
		                			  }
			                	  }
	                		  }		                	  
	                	  }
	            	  
	                	  break;
	                  /*
	                  case SOCKET_ERROR:
	                	  wRunner.speakDebug("T monitor socket error");
	                	  String targetConnId = instruction.getPayload(); 
	                	  if (connectionAllowed){      		  
		                	  if ((connectionId == null) || (targetConnId == connectionId)){
		                		  
			                      if (tkt == null || tkt.length() == 0){
			                   	      String errMsg = "T no ticket";
			                 	      wRunner.speakDebug(errMsg);
			                 	      logger.log(LogEntry.SEV_ERROR, 4, errMsg);
			                      }else{
			                    	// a succesfull reconnect sends wakeups to reader/writer
			                    	 wRunner.speakDebug("T monitor reconnecting");
			                         connectionId = reconnect(tkt);
			                         
			                      }
		                	  } 
	                      }  
	                	  break;
	                	  */
	                  case CONN_ON:
	                	  connectionAllowed = true;
	                      tkt = instruction.getPayload();
	                      
	                      if (tkt == null || tkt.length() == 0){
	                   	      String errMsg = "T on without ticket.  fatal";
	                 	      wRunner.speakDebug(errMsg);
	                 	      logger.log(LogEntry.SEV_ERROR, 4, errMsg);
	                      }else{ 
	                         connectionId = reconnect(tkt);
	                      }

	                	  break;
	                  case CONN_OFF:
	                	  connectionAllowed = false;
	                	  tkt = null;
	                	  // flush messages off the toServerDataQ is set to true
	                	  try{
	        	          writerControlQ.put(new tcpChannelControlEvent(connectionId,
	        		       		    tcpChannelControlEvent.controlEvent.DRAIN_DATA_QUEUE)); 
	                	  }catch(Exception e){
	                   	      String errMsg1 = "T panic 4";
	                 	      wRunner.speakDebug(errMsg1);
	                 	      logger.log(LogEntry.SEV_ERROR, 4, errMsg1 + e.toString());
	                		  
	                	  }
	                	  
	                	  connShutdown();
	                	  connectionId = null;  
	                	  break;               
	               }
            }          
         }    
         ////////
      }).start();
   }

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////   
   private void reader()
   {
      new Thread(new Runnable()
      {
         public void run()
         {       	 
        	 while(true){ 
        		String connectionId = null;
        		tcpChannelControlEvent instruction;
        		 
	 	        try{
	 	      	   // block on control queue
	 	           instruction = readerControlQ.take();
	 	           connectionId = instruction.getConnectionId();
	 	        }catch(Exception e){
               	   String errMsg1 = "T panic 5";
             	   wRunner.speakDebug(errMsg1);
             	   logger.log(LogEntry.SEV_ERROR, 4, errMsg1 + e.toString());
             	   return;
	 	        }
                   
                switch(instruction.getControlEvent()){
                   case WAKEUP:
                      readerInternal(connectionId);
 	                  break;
 	                 
	               default:
            	      String errMsg = "T panic 6";
            	      wRunner.speakDebug(errMsg);
            	      logger.log(LogEntry.SEV_ERROR, 4, errMsg);
                	  break;
                }        
        	 }
         }
      }).start();
   }
     
/*
 * reader
 * blocks on socket read.  Marshall's messages sent by the TCP portal.
 * Puts these incoming messages on the serviceQ.
 */
 private void readerInternal(String connectionId)
 {
    char[] msgHeader = new char[HEADER_TOTAL_LEN_SIZE];
    int currentMsgBuffSize = 8192;
    char[] msg = new char[currentMsgBuffSize];
    int len = 0, msgLen = 0;
    BufferedReader in = null;
    boolean running = true;

    try
    {
       int offset;
       try
       {
          // blocks on the socket read
          in = new BufferedReader(new InputStreamReader(sslsock
                .getInputStream()));
       } catch (Exception e)
       {
           String errMsg = "T reader socket open fail";
           wRunner.speakDebug(errMsg);
           logger.log(LogEntry.SEV_INFO, 4, errMsg + e.toString());
           throw new RuntimeException(errMsg, e);
       }

       while (running)
       {
          offset = 0;
          do
          {
             try
             {
                len = in.read(msgHeader, offset,
                      (HEADER_TOTAL_LEN_SIZE - offset));
             } catch (Exception e)
             {
                String errMsg = "T reader socket read fail";
                wRunner.speakDebug(errMsg);
                throw new RuntimeException(errMsg, e); 
             }

             if (len == -1)
             {
                String errMsg = "T reader socket closed";
            	wRunner.speakDebug(errMsg);
                throw new RuntimeException(errMsg);
             }
             offset += len;
          } while (offset < HEADER_TOTAL_LEN_SIZE);

          try
          {
             // TODO: take a look at version
             msgLen = Integer.parseInt(String.valueOf(msgHeader,
                   (HEADER_VERSION_SIZE + HEADER_ACK_SIZE),
                   HEADER_PAYLOAD_SIZE), 10);
          } catch (Exception e)
          {
        	 String errMsg = "T invalid server message length";
        	 logger.log(LogEntry.SEV_ERROR, 4, errMsg + e.toString());
             wRunner.speakDebug(errMsg);
             throw new RuntimeException(errMsg, e);
          }

          if (msgLen > maxTcpMsgSize)
          {
             String errMsg = "T server message exceeds maximum";
             logger.log(LogEntry.SEV_ERROR, 4, errMsg );
             wRunner.speakDebug(errMsg);
             throw new RuntimeException(errMsg  + msgLen);
          }

          if (msgLen > currentMsgBuffSize)
          {
             msg = new char[msgLen];
             currentMsgBuffSize = msgLen;
          }

          offset = 0;
          do
          {
             try
             {
                len = in.read(msg, offset, (msgLen - offset));
             } catch (Exception e)
             {
                String errMsg = "T reader body socket read fail.";
                wRunner.speakDebug(errMsg);
                throw new RuntimeException(errMsg, e);
             }
             if (len == -1)
             {
            	String errMsg = "T reader body socket closed";
            	wRunner.speakDebug(errMsg);
                throw new RuntimeException(errMsg);
             }
             offset += len;
          } while (offset < msgLen);

          try
          {
             if(len!=0)
             {
                serviceQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_SERVER_MSG, 
                   new tcpChannelPayload(connectionId , new String(msg, 0, offset))
                ));
                
                String errMsg = "T received ";
                wRunner.speakDebug(errMsg);
                
             }else{ // a header with an empty body means its an ack 
                 String errMsg = "T received ack";
                 wRunner.speakDebug(errMsg);
                 writerControlQ.put(new tcpChannelControlEvent(connectionId, 
             		    tcpChannelControlEvent.controlEvent.DEQUEUE));
            	 
                 writerControlQ.put(new tcpChannelControlEvent(connectionId, 
             		    tcpChannelControlEvent.controlEvent.SEND)); 
             }

          } catch (Exception e)
          {	  
           	 String errMsg = "T PANIC 6";
          	 wRunner.speakDebug(errMsg);
          	 logger.log(LogEntry.SEV_ERROR, 4, errMsg + e.toString());
             throw new RuntimeException( errMsg, e);
          }
       } // end of the while loop
    } catch (Exception e)
    {
       // clean up and re-throw
       try
       {
          if (in != null)
          {
             in.close();
          }
       } catch (Exception e1) { };
       
       
       logger.log(LogEntry.SEV_WARNING, 4, e.toString());

       try { 
    	  wRunner.speakDebug("T reader socket error");
    	  logicalConnectionEventsQ.put(new tcpLogicConnectionEvent(
   	         tcpLogicConnectionEvent.logicalConnEvent.SOCKET_ERROR, connectionId));
    	  
       } catch (Exception e1) {
    	   String errMsg = "T PANIC 7";
    	   wRunner.speakDebug(errMsg);
    	   logger.log(LogEntry.SEV_ERROR, 4, errMsg + e1.toString());
       };
    }
 }

   
   /*
    * writeMsgToChannel
    * Helper method used by writer() thread to write out any type of
    * message to the TCP portal.
    */
   private void writeMsgToChannel(PrintWriter stream, String msg, String requestAck ){
	   String pad = "";
       
       if (msg.length() > 0)
       {
          String l = Integer.toString(msg.length());

          if (l.length() > HEADER_PAYLOAD_SIZE)
          {
             logger.log(LogEntry.SEV_ERROR, 9, "Packet too large");
             msg = null;
             throw new RuntimeException(
                   "tcpClient.writer: max size of allowed msg from server exceeded. Got msg of size: "
                         + l);
          }
          for (int i = 0; i < (HEADER_PAYLOAD_SIZE - l.length()); i++)
          {
             pad += "0";
          }

          stream.print(currentProtocolVersion + requestAck + pad + l + msg);
          stream.flush();
       }
   }
   
   /*
    * writer
    * writer thread used to communicate with TCP portal.  Uses 2 channels: the
    * data channel 1) the toServer queue, that it never blocks on and 2) the 
    * writerControlQ, that it blocks on.  The writer's job is just to follow instructions
    * sent to it on the writerControlQ.
    */
   private void writer()
   {
      new Thread(new Runnable()
      {
         public void run()
         {
            PrintWriter out = null;
            String msg = null;
            tcpChannelControlEvent  instruction = null;
            
            // setup initial states, for ack and timer
            int waitingForAck = 0;
            boolean outstandingTimer = false;
            String connectionId = null;
 
               while (true)
               {     
            	  tcpChannelControlEvent.controlEvent ev = tcpChannelControlEvent.controlEvent.UNDEFINED;
            			  
                  try {
                	// blocks on its control Q
					instruction = writerControlQ.take();
					ev = instruction.getControlEvent();
				  } catch (InterruptedException e2) {
            	      String errMsg = "T Panic 8";
            	      wRunner.speakDebug(errMsg);
            	      logger.log(LogEntry.SEV_ERROR, 4, errMsg + e2.toString());
            	      return;
				  }

                  switch(ev){
               
                      case WAKEUP:
                    	  // gets a new write stream and then falls through to send
                    	  connectionId = instruction.getConnectionId();
                	      waitingForAck = 0;
                	      if (sslsock !=null){
                	    	 try{
                	    	    if (out != null) out.close();
                	    	 }catch(Exception e){ out = null;}
                	    	 
                	    	 try{
                	            out = new PrintWriter(sslsock.getOutputStream());
                	    	 }catch(Exception e){
                	    		 out = null;
                        	     String errMsg = "T writer open fail";
                       	         wRunner.speakDebug(errMsg);
                       	         logger.log(LogEntry.SEV_INFO, 4, errMsg);
                                 
                       	         try{
                                    logicalConnectionEventsQ.put(new tcpLogicConnectionEvent(
                               	         tcpLogicConnectionEvent.logicalConnEvent.SOCKET_ERROR, connectionId));
                       	         }catch(Exception e2){
                              	      String errMsg1 = "T Panic 9";
                            	      wRunner.speakDebug(errMsg1);
                            	      logger.log(LogEntry.SEV_ERROR, 4, errMsg1 + e2.toString());
                            	      return;
                       	         }
                	         }
                	      }              	      
                	      // NOTE: break statument should not be here
	                  case SEND:
	                	  if (sslsock == null || out == null){
                             try {
								logicalConnectionEventsQ.put(new tcpLogicConnectionEvent(
									         tcpLogicConnectionEvent.logicalConnEvent.SOCKET_ERROR, connectionId));
						 	 } catch (InterruptedException e) {
					  	         String errMsg = "T Panic 10";
                       	         wRunner.speakDebug(errMsg);
                       	         logger.log(LogEntry.SEV_ERROR, 4, errMsg);
							 }  
                             continue;
	                	  }
	                	  
	                	  // nothing to do if currently waiting on an ack
	                	  if (waitingForAck !=0 ){
	                		  wRunner.speakDebug("T writer waiting for ack");
	                		  continue;
	                	  }
	                	  
                		  //gets a msg, but still leaves it on queue
                		  msg = toServerDataQ.peek();
                		  if (msg != null){
                			  
                			 try
                			 {
                			     waitingForAck = (int) (System.currentTimeMillis());  
                			     writeMsgToChannel(out, msg, ACK_NEEDED); 
                			     wRunner.speakDebug("T sent");
                			 }catch(Exception e){
	                              try
	                              {

	                                 waitingForAck = 0;
	                                 
	                        	     String errMsg = "T write fail";
	                       	         wRunner.speakDebug(errMsg);
	                       	         logger.log(LogEntry.SEV_INFO, 4, errMsg);
	                                 
	                    	    	 try{
	                     	    	    if (out != null) out.close();
	                     	            out = null;
	                     	    	 }catch(Exception e3){ out = null;}
	                       	         
	                                 logicalConnectionEventsQ.put(new tcpLogicConnectionEvent(
	                               	         tcpLogicConnectionEvent.logicalConnEvent.SOCKET_ERROR, connectionId));
	                                 
	                              } catch (Exception e1) {
	                       	         String errMsg = "T PANIC 11";
	                       	         wRunner.speakDebug(errMsg);
	                       	         logger.log(LogEntry.SEV_ERROR, 4, errMsg);
	                              }
                			 }               			  
                			              			  
                			  if (!outstandingTimer) {      
                				  outstandingTimer = true;
                				  if(monitorAlarm != null) {
                				     monitorAlarm.clear();
                				     monitorAlarm = null;
                				  }
                			    wRunner.speakDebug("T set alarm");
                                monitorAlarm = new connectionMonitorAlarmReceiver(context,  
                                  "net.inbetween.MonitorAlarm", new Bundle(), connectionId, staleSocketInterval , writerControlQ, logger);
                			  }
                		  }	                	  
	                	  break;
	                  case DEQUEUE:              	 
	                	  waitingForAck = 0;
	                	  //removes a message without blocking
	                	  toServerDataQ.poll();
	                	  break;
	                  case DRAIN_DATA_QUEUE:
	                	  waitingForAck = 0;
	                	  toServerDataQ.clear();
	                	  break;
	                  case  CHECKACK:
	                	  wRunner.speakDebug("T Check Ack");
	                	  outstandingTimer = false;
	                	  // if not waiting on an ack then all is well
	                	  if (waitingForAck == 0) continue;
	                	  
	                	  int currentTime = (int) (System.currentTimeMillis());

	                	  if ( (currentTime - waitingForAck) > staleSocketInterval){
	                		  // send message up, that the connection is stale 
	                		  waitingForAck = 0;
	                		  try{
	                			 wRunner.speakDebug("T writer timer expired. No ack");
	                             logicalConnectionEventsQ.put(new tcpLogicConnectionEvent(
	                     	         tcpLogicConnectionEvent.logicalConnEvent.SOCKET_ERROR, connectionId));
	                		  }catch(Exception e){
                          	      String errMsg1 = "T PANIC 12";
                        	      wRunner.speakDebug(errMsg1);
                        	      logger.log(LogEntry.SEV_ERROR, 4, errMsg1 + e.toString());
                        	      return;
	                		  }
	                	  }else{
	                		  outstandingTimer = true;
	                		  int timeRemaining = staleSocketInterval - (currentTime - waitingForAck);
	                		  
	                		  if(monitorAlarm != null) {
	                		     monitorAlarm.clear();
	                		     monitorAlarm = null;
	                		  }
	                		  wRunner.speakDebug("T set alarm 2");
	                		  monitorAlarm = new connectionMonitorAlarmReceiver(context,  
	                                  "net.inbetween.SECTCP_TIMEOUT", new Bundle(), connectionId, timeRemaining, writerControlQ, logger);
	                	  }
	                	  	                	  
	                	  break;
	                  default:
	            	      String errMsg = "T unknown control event";
	            	      wRunner.speakDebug(errMsg);
	            	      logger.log(LogEntry.SEV_ERROR, 4, errMsg);
	                	  break;
                  }
               }
         }
      }).start();
   }
}