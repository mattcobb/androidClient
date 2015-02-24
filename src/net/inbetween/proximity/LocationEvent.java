package net.inbetween.proximity;

import android.os.PowerManager;


public class LocationEvent {
	public static enum LocEventType {
		LOC_EVENT_LOCATION_CHANGE,
		LOC_EVENT_PROVIDER_DISABLED,
		LOC_EVENT_PROVIDER_ENABLED,
		LOC_EVENT_PROVIDER_STATUS,
		LOC_EVENT_GPS_STATUS,
		LOC_EVENT_PLACES_CHANGE,
		LOC_EVENT_STATE_CHANGE,
		LOC_EVENT_STOP_RECORDING,
		LOC_EVENT_START_RECORDING,
		LOC_EVENT_STOP_TEST,
		LOC_EVENT_START_TEST,
		LOC_EVENT_HANDLE_LOCATION,
		LOC_EVENT_WIFI_STATUS,
		LOC_EVENT_CELLULAR_STATUS
    }
	
	private Object eventInfo;
	private LocEventType event;
	private PowerManager.WakeLock wakeLock;
	   
	public LocationEvent(LocEventType TypeOfEvent, Object info) {
		event = TypeOfEvent;
		eventInfo = info;
		wakeLock = null;
	}
	
   public LocationEvent(LocEventType TypeOfEvent, Object info, PowerManager.WakeLock inWakeLock) {
      event = TypeOfEvent;
      eventInfo = info;
      wakeLock = inWakeLock;
   }	
	
	public Object getInfo() { return eventInfo; };
	
   public LocEventType getType() { return event; };
   
   public PowerManager.WakeLock getWakeLock() { return wakeLock; };
}
