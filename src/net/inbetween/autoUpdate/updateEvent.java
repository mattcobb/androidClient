package net.inbetween.autoUpdate;

public class  updateEvent
{
   public enum UpdateEventType
   { 
	  CHECK_FOR_APK_UPDATE,
      CHECK_FOR_WEB_UPDATE,
      DOWNLOAD_APK,
      DOWNLOAD_WEB_PKG,
      INSTALL_APK,
      INSTALL_WEB_PKG   
   }
  
   private Object eventInfo;
   private UpdateEventType event;
   private boolean immidiateUpdate;
   
   public updateEvent(UpdateEventType _event, Object _eventInfo, boolean _immidiateUpdate)
   {
      event = _event;
      eventInfo = _eventInfo;
      immidiateUpdate = _immidiateUpdate;
   }
   
   public boolean getImmidiateUpdate() { return immidiateUpdate;}
   public UpdateEventType getType() { return event; }

	public Object getInfo() {
		return eventInfo;
	};
}
