package net.inbetween.sectcp;

public class tcpChannelPayload {
   private String connectionId;
   private String msg;
   public tcpChannelPayload(String _connectionId, String _msg){
	   connectionId = _connectionId;
	   msg = _msg;
   }
   
   public String getConnectionId(){
	   return connectionId;
   }
   
   public String getMsg(){
	   return msg;
   }
}
