package net.inbetween.sectcp;



public class tcpLogicConnectionEvent {
	
	   public enum logicalConnEvent
	   {
		   CONN_ON,
		   CONN_OFF,
		   CONN_RETRY,
		   CONN_RETRY_ALARM,
		   SOCKET_ERROR,
		   NETWORK_STATUS
	   }

	   private logicalConnEvent event;
	   private String payload;
	   private boolean status;
	   
		
	   public tcpLogicConnectionEvent(logicalConnEvent _event){
		   event = _event;
	   }
	   public tcpLogicConnectionEvent(logicalConnEvent _event, String _payload){
		   event = _event;
		   payload = _payload;
	   }
	   
	   public tcpLogicConnectionEvent(logicalConnEvent _event, boolean _status){
		   event = _event;
		   status = _status;
	   }
	   
	   public String getPayload() { return payload; };
	   
	   public logicalConnEvent getControlEvent() { return event; };
	   
	   public boolean getStatus() { return status; };


}
