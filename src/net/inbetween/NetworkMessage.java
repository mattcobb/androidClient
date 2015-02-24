package net.inbetween;

import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.services.ServiceEvent.EventType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.location.Location;
import android.os.Bundle;
import android.text.format.Time;
import android.util.Log;

public class NetworkMessage extends JSONArray 
{
   public static final String KEY_VERSION = "version";
   public static final String KEY_TYPE = "type";
   
   public static final String KEY_PAYLOAD_SCHEMAID = "payloadSchemaId";
   public static final String KEY_PAYLOAD_VER = "payloadVersion";
   public static final String KEY_PAYLOAD_POLICY = "clientPolicySchema.js";
   public static final String KEY_PAYLOAD_CONFIG = "clientConfigSchema.js";
   public static final String KEY_PAYLOAD_NOTIFY_USER = "notifyUserSchema.js";
   public static final String KEY_PAYLOAD_USER_RESPONSE = "userResponseSchema.js";
   public static final String KEY_PAYLOAD_ERROR = "errorSchema.js";
   
   public static final String KEY_POLICY_VER = "clientPolicyVersion";
   public static final String KEY_CONFIG_VER = "clientConfigVersion";
   public static final String KEY_HEADER = "header";
   public static final String KEY_EMPTY = "empty";
   public static final String KEY_PLACES = "places";
   public static final String KEY_ACTIONS  = "actions";
   public static final String KEY_PHONE_HOME = "phoneHome";
   public static final String KEY_DELAY = "delay";
   public static final String KEY_LATITUDE = "lat";
   public static final String KEY_LONGITUDE = "long";
   public static final String KEY_RADIUS = "rad";
   public static final String KEY_DEPART = "depart";
   public static final String KEY_ENTER = "enter";
   public static final String KEY_ALTITUDE = "alt";
   public static final String KEY_ACCURACY = "acuracy";
   public static final String KEY_LOCATION = "loc";
   // changed it to _android as an override for max silence period
   // since android does not have push yet and needs to use a shorter phone home 
   // period (in constrast to iphone)
   public static final String KEY_MAX_SILENCE_PERIOD = "maxSilencePeriod";
   public static final String KEY_MAX_SILENCE_PERIOD_OVERRIDE = "maxSilencePeriod_android";
   public static final String KEY_MINUTES = "minutes";
   public static final String KEY_SOURCE_IBID = "sourceIbid";
   public static final String KEY_DESTINATION_IBID = "destinationIbid";
   public static final String KEY_ACK_ID = "ackId";
   public static final String KEY_MSG_TXT = "msgTxt";
   public static final String KEY_VALUE = "value";
   public static final String KEY_ACTION_BUTTONS = "actionButtons";
   public static final String KEY_TEXT = "text";
   public static final String KEY_USER_ANSWER = "answer";
   public static final String KEY_RESPONSE_TYPE = "responseType";
   public static final String KEY_USER_REP = "userRep";
   public static final String KEY_ACTIONS_SELECTED = "actionsSelected";
   public static final String KEY_SENTTIME = "sentTime";
   public static final String KEY_SENDER = "sender";
   public static final String KEY_ERROR = "error";
   public static final String KEY_ERROR_CODE = "code";
   public static final String KEY_ERROR_BODY = "body";
   public static final String KEY_ERROR_DESCRIPTION = "desc";
   public static final String KEY_ACK = "ack";
   public static final String KEY_TIMESTAMP = "timeStamp";
   public static final String KEY_PROVIDER = "provider";
   public static final String KEY_PHONE_HOME_REASON = "phReason";
   public static final String KEY_REASON_ENTER_DEPART = "entDep";
   public static final String KEY_REASON_ENTER = "enter";
   public static final String KEY_REASON_DEPART = "depart";
   public static final String KEY_REASON_OTHER = "other";
   
   public static final String KEY_DEVICE_SETTING_TYPE = "type";
   public static final String KEY_DEVICE_SETTING_MODEL = "model";
   public static final String KEY_DEVICE_SETTING_OS = "os";
   public static final String KEY_DEVICE = "device";
     
   public static final String KEY_CLIENT_BUILD_ID = "buildId";
   public static final String KEY_CLIENT_PACKAGE_ID = "packageId";
   public static final String KEY_CLIENT_VERSION_TYPE = "versionType";
   public static final String KEY_CLIENT_VERSIONS = "versions";
      
   public static final String DATA_CLIENT_VERSION_SCHEMA = "clientVersionSchema.js";
   public static final String DATA_PAYLOAD_SCHEMA = "payloadSchema1.js";
   
   public static final int INDEX_HEADER = 0;
   public static final int INDEX_BODY = 1;

   private JSONObject hdrObjectArray;
   private JSONArray hdrArray;
   private JSONObject hdr;
   
   private JSONObject bodyObjectArray;
   private JSONArray placesArray;
   private JSONArray actionsArray;
   


   private String notification;
   private String sentTime;
   private String sender;
   private String positiveActionText;
   private int positiveDelay;
   public int getPositiveDelay()
   {
      return positiveDelay;
   }

   private String negativeActionText;
   private int negativeDelay;
   public int getNegativeDelay()
   {
      return negativeDelay;
   }

   private String neutralActionText;
   private int neutralDelay;

   public int getNeutralDelay()
   {
      return neutralDelay;
   }
   public String getPositiveMessage()
   {
      return positiveActionText;
   }
   public String getNegativeMessage()
   {
      return negativeActionText;
   }

   public String getNeutralMessage()
   {
      return neutralActionText;
   }
   
   public String getNotification()
   {
      return notification;
   }
   
   public String getSentTime()
   {
      return sentTime;
   }
   
   public String getSender()
   {
      return sender;
   }
   
   long maxSilence = -1;
   
   public String getSourceIbid()
   {
      try 
      {
         return(hdr.getString(NetworkMessage.KEY_SOURCE_IBID));
      }
      catch(Exception jsonException)
      {
         return null;
      }
   }

   public String getAckId()
   {
      try 
      {
         return(hdr.getString(NetworkMessage.KEY_ACK_ID));
      }
      catch(Exception jsonException)
      {
         return null;
      }
   }
   
   public long getMaxSilence()
   {
      return maxSilence;
   }

   public NetworkMessage() throws JSONException
   {
      super();
   }
   
   private String errorCode;
   public String getErrorCode()
   {
      return errorCode;
   }

   private String errorDescription;
   public String getErrorDescription()
   {
      return errorDescription;
   }
   
   private JSONObject errorObjectBody;
   public JSONObject errorObjectBody()
   {
      return errorObjectBody;
   }

   public NetworkMessage(String source) throws Exception
   {
      super(source);
      this.hdrObjectArray  = this.getJSONObject(NetworkMessage.INDEX_HEADER);
      this.hdrArray = hdrObjectArray.getJSONArray(NetworkMessage.KEY_HEADER);
      this.hdr = hdrArray.getJSONObject(0);

      if(getHeaderVersion() == 1)
      {
         if(getSchema().equals(NetworkMessage.KEY_PAYLOAD_POLICY))
         {
            parsePolicy();
         }
         else if(getSchema().equals(NetworkMessage.KEY_PAYLOAD_CONFIG))
         {
            parseConfig();
         }
         else if(getSchema().equals(NetworkMessage.KEY_PAYLOAD_NOTIFY_USER))
         {
            parseUserNotify();
         }
         else if(getSchema().equals(NetworkMessage.KEY_PAYLOAD_ERROR))
         {
            parseError();
         }
      }
   }
   
   public int getHeaderVersion() throws Exception
   {
      try 
      {
         return(hdr.getInt(NetworkMessage.KEY_VERSION));
      }
      catch(Exception jsonException)
      {
         Log.d("WishRunner", "Header version parse error. " + jsonException.getMessage());
         throw jsonException;
      }
   }
   
   public void putHeader(String schema, int policyVersion, int configVersion) throws Exception
   {
      this.putHeader(schema, policyVersion, configVersion, null, null);
   }
   
   public void putHeader(String schema, int policyVersion, int configVersion,
         String ackId, String ibid) throws Exception
   {
      JSONObject hdr;
      JSONArray hdrArray;
      JSONObject hdrArrayObject;
      
      try {
         hdr = new JSONObject();
         hdr.put(KEY_VERSION, 1);
         hdr.put(KEY_TYPE, "foneHome");
         hdr.put(KEY_PAYLOAD_SCHEMAID, schema);
         if(policyVersion > -1) hdr.put(KEY_POLICY_VER, policyVersion);
         if(configVersion > -1) hdr.put(KEY_CONFIG_VER, configVersion);
         if(ackId != null) hdr.put(KEY_ACK_ID, ackId);
         if(ibid != null) hdr.put(KEY_DESTINATION_IBID, ibid);
         
         hdrArray = new JSONArray();
         hdrArray.put(0, hdr);
         
         hdrArrayObject = new JSONObject();
         hdrArrayObject.put(KEY_HEADER, hdrArray);
         
         this.put(0, hdrArrayObject);
      }
      catch(Exception e1) { 
         throw e1;
      };
   }
   
   // TODO: come back here
   private JSONArray createClientVersionInfo(String buildId, String packageId, String versionType) {
	      try { 
	         JSONObject settings= new JSONObject();	
	         
	         if (buildId != null && (buildId.length() > 0)){
	            settings.put(KEY_CLIENT_BUILD_ID, buildId);
	         }
	         
	         if ((packageId != null) && (packageId.length() > 0)){
	            settings.put(KEY_CLIENT_PACKAGE_ID, packageId);
	         }
	         
	         if ((versionType != null) && (versionType.length() > 0)){
	            settings.put(KEY_CLIENT_VERSION_TYPE, versionType);
	         }
	         JSONArray settingsArray = new JSONArray();
	         settingsArray.put(0, settings);

	         return settingsArray;
	         
	      } catch (Exception timeException) {
	         return null;
	      }
	}
   
   public void putClientVersionSchemaBody(String buildId,
		   String packageId, String versionType) throws Exception
   {
	      JSONObject bodyObject = new JSONObject();
	      try {
	         bodyObject.put(KEY_CLIENT_VERSIONS, 
	        		 createClientVersionInfo(buildId, packageId, versionType));
	         this.put(1, bodyObject);
	      } catch(Exception e1) {
	         throw e1;
	      };	   
   }
   
   public void putPayloadSchema1Body(Location location) throws Exception
   {
      JSONObject loc;
      JSONArray locArray;
      JSONObject bodyObject = new JSONObject();
      Bundle locationBundle;
      
      try {
         loc = new JSONObject();
         if(location != null)
         {
            loc.put(NetworkMessage.KEY_LATITUDE, location.getLatitude());
            loc.put(NetworkMessage.KEY_LONGITUDE, location.getLongitude());
            loc.put(NetworkMessage.KEY_ALTITUDE, location.getAltitude());
            loc.put(NetworkMessage.KEY_ACCURACY, location.getAccuracy());
            loc.put(NetworkMessage.KEY_PROVIDER, location.getProvider());
            
            String reason;
            boolean entered = false; 
            boolean departed = false;
            locationBundle = location.getExtras();
            if(locationBundle != null) {

               entered = locationBundle.getBoolean(EventType.EVENT_LOCATION_ENTERED.name(), false);
               departed = locationBundle.getBoolean(EventType.EVENT_LOCATION_DEPARTED.name(), false);
            }
            if(entered && departed) {
               reason = NetworkMessage.KEY_REASON_ENTER_DEPART;
            } else if(entered) {
               reason = NetworkMessage.KEY_REASON_ENTER;
            } else if(departed) {
               reason = NetworkMessage.KEY_REASON_DEPART;
            } else {
               reason = NetworkMessage.KEY_REASON_OTHER;
            }
            loc.put(NetworkMessage.KEY_PHONE_HOME_REASON, reason);
            
            locArray = new JSONArray();
            locArray.put(0, loc);
            
            bodyObject.put(KEY_LOCATION, locArray);
         }
         bodyObject.put(KEY_TIMESTAMP, createTimeStamp());
         bodyObject.put(KEY_DEVICE, createDeviceSettings());
         
         this.put(1, bodyObject);
      } catch(Exception e1) {
         throw e1;
      };
   }   

   public void putPayloadUserResponse(String response) throws Exception
   {
      JSONObject responseTypeJsonObject;
      JSONObject userRepJsonObject;
      JSONArray responseTypeJsonArray;
      JSONArray userRepJsonArray;
      JSONObject bodyObject;
      
      try {
         responseTypeJsonObject = new JSONObject();
         responseTypeJsonObject.put(NetworkMessage.KEY_TYPE, NetworkMessage.KEY_USER_REP);
         responseTypeJsonArray = new JSONArray();
         responseTypeJsonArray.put(0, responseTypeJsonObject);
         
         bodyObject = new JSONObject();
         bodyObject.put(KEY_RESPONSE_TYPE, responseTypeJsonArray);

         userRepJsonObject = new JSONObject();
         userRepJsonObject.put(NetworkMessage.KEY_TEXT, response);
         userRepJsonArray = new JSONArray();
         userRepJsonArray.put(0, userRepJsonObject);
         bodyObject.put(KEY_ACTIONS_SELECTED, userRepJsonArray);
         
         bodyObject.put(KEY_TIMESTAMP, createTimeStamp());
         
         this.put(1, bodyObject);
      } catch(Exception e1) {
         throw e1;
      };
   }   

   public void putPayloadAck() throws Exception
   {
      JSONObject responseTypeJsonObject;
      JSONArray responseTypeJsonArray;
      JSONObject bodyObject;
      
      try {
         responseTypeJsonObject = new JSONObject();
         responseTypeJsonObject.put(NetworkMessage.KEY_TYPE, NetworkMessage.KEY_ACK);
         responseTypeJsonArray = new JSONArray();
         responseTypeJsonArray.put(0, responseTypeJsonObject);
         
         bodyObject = new JSONObject();
         bodyObject.put(KEY_RESPONSE_TYPE, responseTypeJsonArray);

         bodyObject.put(KEY_TIMESTAMP, createTimeStamp());
         
         this.put(1, bodyObject);
      } catch(Exception e1) {
         throw e1;
      };
   } 
   
   public boolean isPolicy()
   {
      return(getSchema().equals(NetworkMessage.KEY_PAYLOAD_POLICY));
   }

   public boolean isConfig()
   {
      return(getSchema().equals(NetworkMessage.KEY_PAYLOAD_CONFIG));
   }

   public boolean isNotifyUser()
   {
      return(getSchema().equals(NetworkMessage.KEY_PAYLOAD_NOTIFY_USER));
   }

   public boolean isError()
   {
      return(getSchema().equals(NetworkMessage.KEY_PAYLOAD_ERROR));
   }   
   
   public String getSchema()
   {
      try {
         return(this.hdr.getString(NetworkMessage.KEY_PAYLOAD_SCHEMAID));
      }
      catch(Exception schemaException)
      {
         return(null);
      }
   }
   
   public int getPayloadVersion() 
   {
      try
      {
         return(hdr.getInt(NetworkMessage.KEY_PAYLOAD_VER));
      }
      catch (Exception e)
      {
         return(-1);
      }
   }
   
   public void putEmptyBody()
   {
      JSONObject body;
      
      try {
         body = new JSONObject();
         body.put(KEY_TIMESTAMP, createTimeStamp());
         this.put(1, body);
      }
      catch(Exception e1) {};
   }
   
   private void parsePolicy() throws Exception
   {  
      try 
      {
         bodyObjectArray  = this.getJSONObject(NetworkMessage.INDEX_BODY);
         placesArray = bodyObjectArray.getJSONArray(NetworkMessage.KEY_PLACES);
         actionsArray = bodyObjectArray.getJSONArray(NetworkMessage.KEY_ACTIONS);
      }
      catch (Exception jsonException) {
         throw jsonException;
      };
   }
   
   private void parseConfig() throws Exception
   {
      JSONArray maxSilenceArray = null;
      JSONObject maxSilenceJsonObject;
      
      try {
         bodyObjectArray  = this.getJSONObject(NetworkMessage.INDEX_BODY);
         try{
            maxSilenceArray = bodyObjectArray.getJSONArray(NetworkMessage.KEY_MAX_SILENCE_PERIOD_OVERRIDE);
         }catch(Exception e){}
         
         // for backward compat
         if (maxSilenceArray == null){
        	 maxSilenceArray = bodyObjectArray.getJSONArray(NetworkMessage.KEY_MAX_SILENCE_PERIOD);
         }
         
         maxSilenceJsonObject = maxSilenceArray.getJSONObject(0);
         maxSilence = (long) maxSilenceJsonObject.getDouble(NetworkMessage.KEY_MINUTES) * 60000;  // msec
      } 
      catch (Exception jsonException) {
         throw jsonException;
      }
   }

   private void parseUserNotify() throws Exception
   {
      JSONArray msgTxtJsonArrayObject;
      JSONObject msgTxtJsonObject;
      JSONArray actionButtonJsonArrayObject;
      JSONObject actionButtonJsonObject;
      
      try {
         bodyObjectArray  = this.getJSONObject(NetworkMessage.INDEX_BODY);
         
         msgTxtJsonArrayObject = bodyObjectArray.getJSONArray(NetworkMessage.KEY_MSG_TXT);
         msgTxtJsonObject = msgTxtJsonArrayObject.getJSONObject(0);
         notification = msgTxtJsonObject.getString(NetworkMessage.KEY_VALUE);
         sentTime = msgTxtJsonObject.getString(NetworkMessage.KEY_SENTTIME);
         if(sentTime!=null && sentTime.charAt(sentTime.length()-1) == 'Z') {
            sentTime = sentTime.substring(0, sentTime.length()-2) + "+000"; // Replace Z with +000
         }
         try {
            sender = msgTxtJsonObject.getString(NetworkMessage.KEY_SENDER);
         } catch (Exception senderException) {
        	   sender = null;
         }
         
         actionButtonJsonArrayObject = bodyObjectArray.getJSONArray(NetworkMessage.KEY_ACTION_BUTTONS);
         actionButtonJsonObject = actionButtonJsonArrayObject.getJSONObject(0);
         positiveActionText = actionButtonJsonObject.getString(NetworkMessage.KEY_TEXT);
         positiveDelay = actionButtonJsonObject.optInt(NetworkMessage.KEY_DELAY, 0);
         
         if(actionButtonJsonArrayObject.length() >= 2) {
            actionButtonJsonObject = actionButtonJsonArrayObject.getJSONObject(1);
            negativeActionText = actionButtonJsonObject.getString(NetworkMessage.KEY_TEXT);
            negativeDelay = actionButtonJsonObject.optInt(NetworkMessage.KEY_DELAY, 0);
         }
         
         if(actionButtonJsonArrayObject.length() == 3) {
            actionButtonJsonObject = actionButtonJsonArrayObject.getJSONObject(2);
            neutralActionText = actionButtonJsonObject.getString(NetworkMessage.KEY_TEXT);
            neutralDelay = actionButtonJsonObject.optInt(NetworkMessage.KEY_DELAY, 0);
         }
      } 
      catch (Exception jsonException) {
         throw jsonException;
      }
   }

   private void parseError() throws Exception
   {
      JSONArray errorJsonArrayObject;
      JSONObject errorJsonObject;
      
      try {
         bodyObjectArray  = this.getJSONObject(NetworkMessage.INDEX_BODY);
         
         errorJsonArrayObject = bodyObjectArray.getJSONArray(NetworkMessage.KEY_ERROR);
         
         errorJsonObject = errorJsonArrayObject.getJSONObject(0);
         errorCode = errorJsonObject.getString(NetworkMessage.KEY_ERROR_CODE);
         

         // body and description is optional
         try{
         errorObjectBody = errorJsonObject.getJSONObject(NetworkMessage.KEY_ERROR_BODY);
         }catch(Exception e){
        	 errorObjectBody = null; 
         }
         
         try{
            errorDescription = errorJsonObject.getString(NetworkMessage.KEY_ERROR_DESCRIPTION);
         }catch(Exception e){
        	 errorDescription = null;
         }
         
      } 
      catch (Exception jsonException) {
         throw jsonException;
      }
   }
   
   public int numPlaces()
   {
      if(placesArray != null) return placesArray.length();
      else return 0;
   }
   
   public JSONObject getPlace(int iPlace)
   {
      if(placesArray != null)
      {
         try {
            return(placesArray.getJSONObject(iPlace));
         }
         catch (Exception getException) {
            return null;
         }
      }
      else return null;
   }

   public int numActions()
   {
      if(placesArray != null) return placesArray.length();
      else return 0;
   }
   
   public JSONObject getPlaceActions(int iPlace)
   {
      if(actionsArray != null)
      {
         try {
            return(actionsArray.getJSONObject(iPlace));
         }
         catch (Exception getException) {
            return null;
         }
      }
      else return null;
   }
   
   public int actionsLength()
   {
      if(actionsArray != null) return actionsArray.length();
      else return 0;
   }
   
   private JSONArray createTimeStamp() {
      try {
         Time timeStamp = new Time();
         timeStamp.setToNow();
         timeStamp.timezone = "UTC";
         
         JSONObject timestampValue = new JSONObject();
         
         timestampValue.put(NetworkMessage.KEY_VALUE, timeStamp.format3339(false));
         JSONArray timestampArray = new JSONArray();
         timestampArray.put(0, timestampValue);

         return timestampArray;
         
      } catch (Exception timeException) {
         return null;
      }
   }
   
   private JSONArray createDeviceSettings() {
	      try { 
	         JSONObject settings= new JSONObject();	
	         
	         settings.put(KEY_DEVICE_SETTING_MODEL, android.os.Build.MODEL);
	         settings.put(KEY_DEVICE_SETTING_OS, android.os.Build.VERSION.RELEASE);
	         JSONArray settingsArray = new JSONArray();
	         settingsArray.put(0, settings);

	         return settingsArray;
	         
	      } catch (Exception timeException) {
	         return null;
	      }
	}
   
   
   
   
   private static LinkedBlockingQueue<String> ackQueue;
   
   public static boolean isDuplicate(String ackId) 
   {
      boolean duplicate = false;
      
      try {
         if(ackQueue==null) {
            ackQueue = new LinkedBlockingQueue<String>();
         }
         
         if(ackId != null && ackId.length()>0) {
            duplicate = ackQueue.contains(ackId);
            
            if(!duplicate) {
               if(ackQueue.size() > 100)
               {
                  try {
                    ackQueue.poll();
                  } catch (Exception takeException) {};
               }
               ackQueue.add(ackId);
            }
         }
      } catch (Exception dupException) {};
      
      return duplicate;
   }
}

/*
[{\"header\": [{\"version\": 1, \"type\": \"foneHome\", \"payloadSchemaId\": \"emptySchema.js\", \"payloadVersion\": 0}]}, {\"empty\": []}]");
*/