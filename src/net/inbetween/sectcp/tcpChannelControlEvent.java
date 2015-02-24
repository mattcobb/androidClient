package net.inbetween.sectcp;

public class tcpChannelControlEvent {
   public enum controlEvent
   {
	   SEND,
	   WAKEUP,
	   DEQUEUE,
	   DRAIN_DATA_QUEUE,
	   CHECKACK,
	   UNDEFINED
	  
   }

   private controlEvent event;
   private String connectionId;
	
   public tcpChannelControlEvent(String _connectionId,  controlEvent _event){
	   event = _event;
	   connectionId = _connectionId;
   }
   
   public String getConnectionId() { return connectionId; };
   
   public controlEvent getControlEvent() { return event; };
}

