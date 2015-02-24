package net.inbetween.services;

import net.inbetween.webview.R;
import android.content.Context;
import android.content.Intent;

public class ServiceEvent
{
   public enum EventType
   {
      EVENT_CONNECTIVITY,
      EVENT_DISCONNECTED,
      EVENT_LOCATION_DEPARTED,
      EVENT_LOCATION_ENTERED,
      EVENT_LOCATION_NEEDPROVIDER,
      EVENT_LOCATION_NEEDWIFI,
      EVENT_LOCATION_DONE,
      EVENT_MESSAGE_REMINDER,
      EVENT_MOUTH_MUTE,
      EVENT_MOUTH_READY,
      EVENT_NEWS_VIEWED,
      EVENT_PHONE_HOME,
      EVENT_PHONE_HOME_TIMER,
      EVENT_POLICY_TIMER,
      EVENT_PRIVATE_MODE,
      EVENT_PROMPT_ANSWERED,
      EVENT_PROMPT_NOT_ANSWERED,
      EVENT_PROMPT_USER,
      EVENT_SAVE_BATTERY,
      EVENT_SERVER_CONNECTED,
      EVENT_SERVER_MSG,
      EVENT_SERVER_RECONNECT_TIMER,
      EVENT_SET_UP_DEBUG_MODE,
      EVENT_SET_DEBUG_SPEAK,
      EVENT_START_INTENT,
      EVENT_SPEECH_OVER,
      EVENT_SUMMARIZE_MESSAGES,
      EVENT_SYNTHESIZE_DONE,
      EVENT_TICKET,
      EVENT_TOGGLE_DEBUG_MODE,
      EVENT_UNKNOWN,
      EVENT_USER_READY,
      EVENT_USER_LOGGED_IN,
      EVENT_WIFI_CONNECTED,
      EVENT_WIFI_DISCONNECTED,
      EVENT_CLIENT_UP_TO_DATE,
      EVENT_CLIENT_UPDATING,
      EVENT_CLIENT_UPDATE_FAILED,
      EVENT_CLIENT_INSTALL_APK,
      EVENT_CLEINT_WEBPKG_INSTALLED,
      EVENT_CLEINT_WEBPKG_INSTALLED_IMMEDIATE,
      EVENT_CLIENT_LOCATION_SUCCESS,
      EVENT_CLIENT_LOCATION_FAIL,
      EVENT_NEWS
   }
  
   private Object eventInfo;
   private EventType event;
   
   public ServiceEvent(EventType TypeOfEvent, Object info)
   {
      event = TypeOfEvent;
      eventInfo = info;
   }
   
   public Object getInfo() { return eventInfo; };
   
   public EventType getType() { return event; };
   
   public static void broadcastServiceEvent(Context context, ServiceEvent.EventType eventType) {
	   Intent serviceEventIntent = new Intent();
	      
	   serviceEventIntent.setAction(context.getString(R.string.SERVICE_BROADCAST_EVENTS));
	   serviceEventIntent.putExtra(context.getString(R.string.KEY_SERVICE_EVENT_TYPE), eventType.name());
	      
	   context.sendBroadcast(serviceEventIntent);
   }
}
