package net.inbetween.services;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
//import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.autoUpdate.updateEvent;
import net.inbetween.autoUpdate.autoUpdate;
import net.inbetween.Action;
import net.inbetween.ActionSet;
import net.inbetween.EventStartIntent;
import net.inbetween.NetworkMessage;
import net.inbetween.Place;
import net.inbetween.PlaceHash;
import net.inbetween.PolicyActions;
import net.inbetween.UserNotification;
import net.inbetween.VoiceRecognition;
import net.inbetween.TransparentActivity.TransparentActivityDialog;
import net.inbetween.actions.PhoneHomeAction;
import net.inbetween.actions.StartTimerAction;
import net.inbetween.analytics.AnalyticsConsumer;
import net.inbetween.analytics.AnalyticsProducer;
import net.inbetween.log.LogConsumer;
import net.inbetween.log.LogEntry;
import net.inbetween.log.LogProducer;
import net.inbetween.proximity.ProximityAlert3;
import net.inbetween.receivers.ConnectivityReceiver;
import net.inbetween.receivers.EventAlarmReceiver;
import net.inbetween.receivers.PhoneState;
import net.inbetween.receivers.WakeUpReceiver;
import net.inbetween.sectcp.tcpChannelPayload;
import net.inbetween.sectcp.tcpClientV3;
import net.inbetween.sectcp.tcpLogicConnectionEvent;
import net.inbetween.services.ServiceEvent.EventType;
import net.inbetween.util.Mouth;
import net.inbetween.util.cloudAddress;
import net.inbetween.webview.R;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.Log;

public class WishRunner extends Service implements
      MediaPlayer.OnCompletionListener
{
   private tcpClientV3 tcpPortal;
   private cloudAddress cloudAddr;

   // use for checking for updates/downloaing webpackages
   LinkedBlockingQueue<updateEvent> autoUpdateQ;
   autoUpdate  autoUpdateService;
   
   private LinkedBlockingQueue<ServiceEvent> eventQ;
   // private LinkedList<Bundle> dialogQueue;
   private LinkedList<Bundle> notificationQueue;
   private Bundle activeNotificationBundle;
   private LinkedBlockingQueue<LogEntry> logQueue;
   private LogConsumer logConsumer;
   public LogProducer logger;
   private MediaPlayer mediaPlayer;

   private LinkedBlockingQueue<JSONObject> analyticsQueue;
   private AnalyticsConsumer analyticsConsumer;
   public AnalyticsProducer analytics;

   private LinkedBlockingQueue<tcpLogicConnectionEvent> logicalConnectionEventsQ;

   final int SERVICESTATE_TKT = 0x8000;
   final int SERVICESTATE_CONNECTED = 0x4000;
   final int SERVICESTATE_PROMPT_INUSE = 0x2000;
   final int SERVICESTATE_NEED_WIFI_ENABLED = 0x1000;
   final int SERVICESTATE_NO_CONNECTIVITY = 0x0800;
   final int SERVICESTATE_PRIVATE = 0x0400;
   final int SERVICESTATE_NEEDPROVIDER = 0x0200;
   final int SERVICESTATE_CONNECTING = 0x100;
   final int SERVICESTATE_NEWS_WAITING = 0x80;
   final int SERVICESTATE_TALKING = 0x40;
   final int SERVICESTATE_LISTENING = 0x20;

   final int ID_NEWS_NOTIFICATION = 2;

   private int ServiceState;

   private static String tkt;

   public static final String PROXIMITY_INTENT_ACTION = new String(
         "net.inbetween.services.PROXIMITY_ALERT_ACTION");
   
   private long MIN_PROMPT_TIME = 24 * 60 * 60 * 1000;
   private long askedForConnectivity = 0;
   private long askedForProviders = 0;

   private int policyVersion;
   private int configVersion;

   private SharedPreferences preferencesInstance;

   private PolicyActions policyActions;
   private PlaceHash placeHash;

   private StartTimerAction startTimerAction;

   private int timerSequence;
   private PhoneState phoneState;
   ConnectivityReceiver connectivityReceiver;

   private boolean silenceSpeech;
   
   public boolean isSpeechSilent()
   {
      if (silenceSpeech)
      {
         return true;
      } else if (phoneState != null && phoneState.callInProgress())
      {
         return true;
      }
      ;

      if (audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION) <= 0)
      {
         return true;
      }

      if (audioManager.isMusicActive())
      {
         return true;
      }

      return false;
   }

   private boolean voiceRecognition;
   
   private boolean summarizeMessages;
   private EventAlarmReceiver summarizeMsgAlarm;
   
   private Time phoneHomeTime;
   private Time notificationTime;

   private ProximityAlert3 ibProximityAlert;

   private Notification statusBarNotification;
   private static final int statusBarId = 8;
   private CharSequence statusBarText;
   private long disconnectedTimeStamp = -1;
   private boolean NotificationIndicators = false;
   private Context appContext;

   private PendingIntent contentIntent;

   private HashMap<String, EventAlarmReceiver> outstandingAlarms;
   public static final String timedEvent_Action = "net.inbetween.";

   private EventAlarmReceiver privateModeAlarm;

   private AlarmManager alarmManager;
   public static final String genieAwaken_Action = "net.inbetween.GenieAwaken";

   public Mouth mouth;
   private boolean debugTalk;
   private final int debugTalkValue = 42121912;
   private boolean debugMode;

   WakeUpReceiver wakeupReceiver;

   private Location currentLocation = null;

   Notification newsNotification;

   public AudioManager audioManager;

   private VoiceRecognition voiceRecognizer;

   Handler loopHandler;

   private HashMap<String, Integer> msgSenders;

   private EventAlarmReceiver msgReminderAlarm;
   int reminderCount=0;
   
   private boolean State(int testState)
   {
      return ((ServiceState & testState) == testState);
   }

   public Context getAppContext()
   {
      if (appContext == null)
      {
         appContext = getApplicationContext();
      }
      return appContext;
   }

   /**
    * Class for clients to access. Because we know this service always runs in
    * the same process as its clients, we don't need to deal with IPC.
    */
   public class LocalBinder extends Binder
   {
      public WishRunner getService()
      {
         return WishRunner.this;
      }
   }

   private String readTicket()
   {
      String newTicket;
      newTicket = prefs().getString(getString(R.string.KEY_TICKET), null);
      if (newTicket != null && newTicket.length() > 0)
      {
         ServiceState |= SERVICESTATE_TKT;
         updateStatusBarInfo();
         return (newTicket);
      } else
         return null;
   }

   private void clearTicket()
   {
      tkt = null;

      SharedPreferences.Editor editor = prefs().edit();

      // These preferences are really difficult to make work so I'm doing it
      // twice here
      editor.remove(getString(R.string.KEY_TICKET));
      editor.commit();

      editor.putString(getString(R.string.KEY_TICKET), "");
      editor.commit();

      ticketRefreshed();
   }

   private void readSilencePreference()
   {
      silenceSpeech = prefs()
            .getBoolean(getString(R.string.KEY_SILENCE), false);
   }

   private void readSummarizeMessagesPref()
   {
      summarizeMessages = prefs().getBoolean(getString(R.string.KEY_SUMMARIZE_MESSAGES), true);
   }
   
   public boolean getSilence()
   {
      return silenceSpeech;
   }

   public boolean getSummarizeMessages()
   {
      return summarizeMessages;
   }
   
   public void setSilence(boolean inSilence)
   {
      silenceSpeech = inSilence;
      SharedPreferences.Editor editor = prefs().edit();
      editor.putBoolean(getString(R.string.KEY_SILENCE), inSilence);
      editor.commit();
   }
   
   public void setSummarizeMessages(boolean inSummarize)
   {
      summarizeMessages = inSummarize;
      SharedPreferences.Editor editor = prefs().edit();
      editor.putBoolean(getString(R.string.KEY_SUMMARIZE_MESSAGES), inSummarize);
      editor.commit();      
   }
   
   // Voice recognition settings
   private void readVoiceRecognitionPreference()
   {
      voiceRecognition = prefs()
            .getBoolean(getString(R.string.KEY_VOICE_RECOGNITION), false);
   }

   public boolean getVoiceRecognition()
   {
      return voiceRecognition;
   }

   public void setVoiceRecognition(boolean inVoiceRecognition)
   {
      voiceRecognition = inVoiceRecognition;
      SharedPreferences.Editor editor = prefs().edit();
      editor.putBoolean(getString(R.string.KEY_VOICE_RECOGNITION), inVoiceRecognition);
      editor.commit();
   }   
   
   public String getTicket()
   {
      if (tkt == null || tkt.length() <= 0)
      {
         tkt = readTicket();
      }
      return tkt;
   }

   public cloudAddress getCloudAddress()
   {
      return cloudAddr;
   }

   public String getReminderType() {
      return (prefs().getString(getString(R.string.KEY_REMINDER_TYPE), getString(R.string.KEY_NONE)));
   }

   public void setReminderType(String reminderType) {
      SharedPreferences.Editor editor = prefs().edit();
      editor.putString(getString(R.string.KEY_REMINDER_TYPE), reminderType);
      editor.commit();
   }   
   
   private int getReminderFrequency() {
      return (prefs().getInt(getString(R.string.KEY_REMINDER_FREQUENCY), 10));      
   }

   private void setReminderFrequency(int frequency) {
      SharedPreferences.Editor editor = prefs().edit();
      editor.putInt(getString(R.string.KEY_REMINDER_FREQUENCY), frequency);
      editor.commit();
   }
   
   private int getReminderDuration() {
      return ( prefs().getInt(getString(R.string.KEY_REMINDER_DURATION), 60));
   }

   private void setReminderDuration(int duration) {
      SharedPreferences.Editor editor = prefs().edit();
      editor.putInt(getString(R.string.KEY_REMINDER_DURATION), duration);
      editor.commit();
   }   
   
   private int getMaxReminders()
   {
      int frequency = getReminderFrequency(); 
      if(frequency == 0) return 0;
      
      int duration = getReminderDuration();
      String reminderType = getReminderType();
      
      if(reminderType.equalsIgnoreCase(getString(R.string.KEY_NONE))) return 0;
      
      if(reminderType.equalsIgnoreCase(getString(R.string.KEY_LINEAR))) {
         int roundUp = ((duration % frequency != 0 ) ? 1 : 0);
         return( duration/frequency + roundUp);
      } else if(reminderType.equalsIgnoreCase(getString(R.string.KEY_EXPONENTIAL)))
      {
         int reminderTime = 0;
         int iReminder;
         for(iReminder=0; reminderTime < duration; iReminder++)   
         {
            reminderTime = reminderTime + (int)(Math.pow(2, iReminder)) * frequency;
         }
         return(iReminder);
      } else {
         return 0;
      }
   }   
   
   public void ticketRefreshed()
   {
      if (eventQ == null)
         return;

      try
      {
         eventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_TICKET, null));
      } catch (Exception ePut)
      {
      }
      ;
   }
   
   public void checkForUpdate(boolean immediate, boolean apk, String versionType)
   {
      if (autoUpdateQ == null)
         return;

      try
      {
         if (apk){
            autoUpdateQ.put(new updateEvent(updateEvent.UpdateEventType.CHECK_FOR_APK_UPDATE, versionType, immediate));
         }else{
            autoUpdateQ.put(new updateEvent(updateEvent.UpdateEventType.CHECK_FOR_WEB_UPDATE, versionType, immediate));
         }
      } catch (Exception ePut)
      {
          String msg = "FATAL Unable to send pcheck for update event";
          if (logger != null)
             logger.log(LogEntry.SEV_ERROR, 1, msg);
          speakDebug(msg);
      }
      ;
   }
   
   public void downloadUpdate(boolean immediate)
   {
      if (autoUpdateQ == null)
         return;

      try
      {
    
            autoUpdateQ.put(new updateEvent(updateEvent.UpdateEventType.DOWNLOAD_WEB_PKG, null, immediate));
         
      } catch (Exception ePut)
      {
          String msg = "FATAL Unable to send pcheck for update event";
          if (logger != null)
             logger.log(LogEntry.SEV_ERROR, 1, msg);
          speakDebug(msg);
      }
      ;
   } 
   

   public boolean getDebugTalk()
   {
      return debugTalk;
   }

   public void setDebugTalk(boolean newDebugTalk)
   {
      if (eventQ == null)
         return;

      try
      {
         eventQ.put(new ServiceEvent(
               ServiceEvent.EventType.EVENT_SET_DEBUG_SPEAK, new Boolean(
                     newDebugTalk)));
      } catch (Exception ePut)
      {
      }
      ;
   }

   public boolean getDebugMode()
   {
      return debugMode;
   }

   public void toggleDebug()
   {
      if (eventQ == null)
         return;

      try
      {
         eventQ.put(new ServiceEvent(
               ServiceEvent.EventType.EVENT_TOGGLE_DEBUG_MODE, null));
      } catch (Exception ePut) {};
   }

   @Override
   public void onCreate()
   {
      tkt = null;
      multipleLoginsFound = false;
   }

   @Override
   public int onStartCommand(Intent intent, int flags, int startId)
   {
      Log.d(getString(R.string.LOG_TAG), "Received start id " + startId + ": " + intent);

      appContext = getApplicationContext();
      
      debugMode = prefs().getInt(getString(R.string.DEBUG_MODE), 0) == debugTalkValue;
      debugTalk = (prefs().getInt(getString(R.string.DEBUG_TALK), 0) == debugTalkValue) && debugMode;

      eventQ = new LinkedBlockingQueue<ServiceEvent>();
      phoneState = new PhoneState(eventQ);
      audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

      notificationQueue = new LinkedList<Bundle>();
      cloudAddr = new cloudAddress(appContext);

      logQueue = new LinkedBlockingQueue<LogEntry>();
      logConsumer = new LogConsumer(10, logQueue, 30, this);
      new Thread(logConsumer).start();
      
      int severityPolicy;
      if (!debugTalk){
    	 // by default this is the squealer mode
    	 // that just reports errors to cloud
    	 // devs can turn on every severity by toggling debugTalk
    	 severityPolicy = LogProducer.POL_SEV_ERROR;
      }else{
     	  severityPolicy = LogProducer.POL_SEV_ERROR | LogProducer.POL_SEV_WARNING | LogProducer.POL_SEV_INFO;
      }
      
      // get severity level policy from local settings and add it here
      logger = new LogProducer(10, logQueue, severityPolicy);
      mouth = new Mouth(appContext, logger, eventQ, R.raw.beep);
      
      analyticsQueue = new LinkedBlockingQueue<JSONObject>();
      analyticsConsumer = new AnalyticsConsumer(this, analyticsQueue);
      analytics = new AnalyticsProducer(analyticsQueue);
      Thread analyticsThread = new Thread(analyticsConsumer);
      analyticsThread.start();
      
      autoUpdateQ = new LinkedBlockingQueue<updateEvent>();    
      autoUpdateService = new autoUpdate(this, appContext, logger, autoUpdateQ);

      audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

      policyActions = new PolicyActions();
      placeHash = new PlaceHash();
      timerSequence = 0;
      ServiceState = 0;

      readSilencePreference();
      readBatterySavingPreference();
      readVoiceRecognitionPreference();
      readSummarizeMessagesPref();
      
      alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

      phoneHomeTime = new Time();
      phoneHomeTime.setToNow();

      readPolicyVersion();
      readConfigVersion();

      activeNotificationBundle = null;

      logicalConnectionEventsQ = new LinkedBlockingQueue<tcpLogicConnectionEvent>();

      outstandingAlarms = new HashMap<String, EventAlarmReceiver>();
      summarizeMsgAlarm = null;
      msgReminderAlarm = null;
      
      checkPrivateModePreference();

      restoreConfig();
      restorePolicy();

      TelephonyManager phoneMgr = (TelephonyManager) this
            .getSystemService(Context.TELEPHONY_SERVICE);
      phoneMgr.listen(phoneState, PhoneStateListener.LISTEN_CALL_STATE);

      // Check the ticket at startup
      try
      {
         eventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_TICKET, null));
      } catch (Exception putFirst) {};

      if (!connectivityAvailable(appContext))
      {
         try
         {
            speakDebug("no networks detected");
            eventQ.put(new ServiceEvent(
                  ServiceEvent.EventType.EVENT_CONNECTIVITY, false));
         } catch (Exception connException)
         {
         }
         ;
      }

      // Start the status bar notifications
      startStatusBar();

      msgSenders = new HashMap<String, Integer>();
      
      String tcpServerName = cloudAddr.getTCPPortalDomain();

      // this never executes for production. This is to let the dev know which
      // environment he/she is going against
      if (cloudAddr.isNonProduction() == true)
      {
         speakDebug("On environment " + cloudAddr.getEnvSuffix().substring(1));
      }

      // start tcp client service
      tcpPortal = new tcpClientV3(tcpServerName,
            Integer.parseInt(getString(R.string.tcpPortalServerPort)), this, // wishrunner
            eventQ, logicalConnectionEventsQ, appContext, logger);

      logger.log(LogEntry.SEV_INFO, 1, "Service started.");

      ibProximityAlert = new ProximityAlert3(eventQ, this, logger);

      // Start the service event loop
      eventLoop();

      // We want this service to continue running until it is explicitly
      // stopped, so return sticky.
      return START_STICKY;
   }

   public void onResume()
   {
      if (logger != null)
         logger.log(LogEntry.SEV_INFO, 1, "Service resumed.");
      
      startConnectivityReceiver();
   }

   public void onPause()
   {
      if (wakeupReceiver != null)
      {
         unregisterReceiver(wakeupReceiver);
         wakeupReceiver = null;
      }

      stopConnectivityReceiver();

      if (logger != null)
         logger.log(LogEntry.SEV_INFO, 1, "Service paused.");
   }

   public void onStop()
   {
      if (logger != null)
         logger.log(LogEntry.SEV_INFO, 1, "Service stopped.");
   }

   @Override
   public void onDestroy()
   {
      mouth.close();
      stopForeground(true);
      logger.log(LogEntry.SEV_INFO, 1, "Service destroyed.");
   }

   public IBinder onBind(Intent intent)
   {
      return mBinder;
   }

   // This is the object that receives interactions from dialogs.
   private final IBinder mBinder = new LocalBinder();
   protected boolean multipleLoginsFound;
   private boolean saveBattery;
   private int voiceRetryCount;
   private int localNotificationAck = 0;
   private long notificationStartTime = 0;

   public void returnUserAnswer(String answerKey)
   {
      try
      {
         eventQ.put(new ServiceEvent(
               ServiceEvent.EventType.EVENT_PROMPT_ANSWERED, answerKey));
      } catch (Exception queueException)
      {
         String msg = "FATAL Unable to send prompt answered event";
         if (logger != null)
            logger.log(LogEntry.SEV_ERROR, 1, msg);
         speakDebug(msg);
      }
   }

   public void returnNoAnswer()
   {
      returnNoAnswer(null);
   }

   public void returnNoAnswer(String invalidResponse)
   {
      try
      {
         eventQ.put(new ServiceEvent(
               ServiceEvent.EventType.EVENT_PROMPT_NOT_ANSWERED,
               invalidResponse));
      } catch (Exception queueException)
      {
         String msg = "FATAL Unable queue prompt not answerd event";
         if (logger != null)
            logger.log(LogEntry.SEV_ERROR, 1, msg);
         speakDebug(msg);
      }
   }

   static private boolean running(Context context)
   {
      ActivityManager manager = (ActivityManager) context
            .getSystemService(ACTIVITY_SERVICE);
      for (RunningServiceInfo service : manager
            .getRunningServices(Integer.MAX_VALUE))
      {
         if (service.service.getClassName().equalsIgnoreCase(
               context.getString(R.string.SERVICE_NAME)))
         {
            return true;
         }
      }
      return false;
   }

   static public void startSelf(Context context)
   {
      if (!running(context))
      {
         Intent service = new Intent(context.getString(R.string.SERVICE_NAME));
         context.startService(service);
      }
   }

   public void stopIfLoggedOut()
   {
      if(! State(SERVICESTATE_TKT)) {
         stopSelf();
      }
   }
   
   private boolean sendVersions()
   {
      NetworkMessage msg;
      try
      {
         msg = new NetworkMessage();
         msg.putHeader("payloadSchema1.js", getPolicyVersion(),
               getConfigVersion());
         msg.putEmptyBody();

         logger.log(LogEntry.SEV_INFO, 2, "Send versions to server");

         tcpPortal.sendMessage(msg.toString());
      } catch (Exception sendException)
      {
         logger.log(LogEntry.SEV_WARNING, 1, "Failed to send versions. "
               + sendException.toString());
         speakDebug("Exception sending versions");
         return false;
      }
      return true;
   }

   private void eventLoop()
   {
      new Thread(new Runnable()
      {
         public void run()
         {
            ServiceEvent event;
            NetworkMessage msgFromServer;
            Location here;
            String policyEventName;
            ActionSet executeActions;
            ActionSet actionSet;
            int iAction;
            Action action;
            boolean phonedHome;
            executeActions = new ActionSet();
            long maxSilence = -1;
            boolean waitForProximity = false;
            boolean locationEntered = false;
            boolean locationDeparted = false;

            while (true)
            {
               try
               {
                  do
                  {
                     event = eventQ.take();

                     logger.log(LogEntry.SEV_INFO, 8, "Got Event: "
                           + event.getType().toString());

                     // Process the event
                     switch (event.getType())
                     {
                        case EVENT_PHONE_HOME:
                           executeActions.add(new PhoneHomeAction());
                        break;

                        case EVENT_LOCATION_ENTERED:
                        case EVENT_LOCATION_DEPARTED:
                           policyEventName = (String) event.getInfo();
                           if (policyEventName == null)
                              policyEventName = "";

                           speakDebug("Location fired");
                           logger.log(LogEntry.SEV_INFO, 2, "Location Event: "
                                 + policyEventName);

                           if (policyEventName != "")
                           {
                              actionSet = policyActions.get(policyEventName);
                              if (actionSet != null && actionSet.size() > 0)
                              {
                                 for (iAction = 0; iAction < actionSet.size(); iAction++)
                                 {
                                    executeActions.add(actionSet.get(iAction));
                                 }
                              } else
                              {
                                 speakDebug("No actions");
                                 logger.log(LogEntry.SEV_INFO, 2,
                                       "No actions for location");
                              }
                           } else
                           {
                              speakDebug("Location empty");
                           }
                           waitForProximity = true;
                           locationEntered = (event.getType() == EventType.EVENT_LOCATION_ENTERED);
                           locationDeparted = (event.getType() == EventType.EVENT_LOCATION_DEPARTED);
                        break;

                        case EVENT_LOCATION_DONE:
                           speakDebug("Proximity done");
                           currentLocation = (Location) event.getInfo();
                           waitForProximity = false;
                        break;

                        case EVENT_LOCATION_NEEDPROVIDER:
                        {
                           Boolean needed;
                           needed = (Boolean) event.getInfo();

                           if (needed == true)
                           {
                              logger.log(LogEntry.SEV_INFO, 2,
                                    "Location provided needed");

                              ServiceState |= SERVICESTATE_NEEDPROVIDER;
                              
                              long currentTime = System.currentTimeMillis();
                              if(askedForProviders + MIN_PROMPT_TIME < currentTime) {
	                              Bundle providerNeededBundle = createTransparentActivityDialogBundle(
	                                    getString(R.string.DIALOG_MSG_ENABLE_LOCATION_SERVICES),
	                                    getString(R.string.DIALOG_MSG_SETTINGS),
	                                    Settings.ACTION_LOCATION_SOURCE_SETTINGS,
	                                    null, null,
	                                    getString(R.string.DIALOG_MSG_CANCEL),
	                                    null, null, null);
	
	                              startNotification(providerNeededBundle);
	                              
	                              askedForProviders = currentTime;
                              }
                           } else
                           {
                              logger.log(LogEntry.SEV_INFO, 2,
                                    "Location provided restored");
                              ServiceState = ServiceState
                                    & ~SERVICESTATE_NEEDPROVIDER;
                           }
                           updateStatusBarInfo();
                        }
                        break;

                        case EVENT_LOCATION_NEEDWIFI:
                        {
                           Boolean needed = (Boolean) event.getInfo();
                           if (needed)
                           {
                              logger.log(LogEntry.SEV_INFO, 2,
                                    "Need WiFi enabled");

                              ServiceState |= SERVICESTATE_NEED_WIFI_ENABLED;
                              
                              long currentTime = System.currentTimeMillis();
                              if(askedForConnectivity + MIN_PROMPT_TIME < currentTime) {
	                              Bundle wifiNeededBundle = createTransparentActivityDialogBundle(
	                                    getString(R.string.DIALOG_MSG_NEED_WIFI),
	                                    getString(R.string.DIALOG_MSG_SETTINGS),
	                                    Settings.ACTION_WIFI_SETTINGS, null, null,
	                                    getString(R.string.DIALOG_MSG_CANCEL),
	                                    null, null, null);
	
	                              startNotification(wifiNeededBundle);
	                              
	                              askedForConnectivity = currentTime;
                              }
                           }
                           if (!needed)
                           {
                              ServiceState &= ~SERVICESTATE_NEED_WIFI_ENABLED;
                           }
                           
                           updateStatusBarInfo();
                        }
                        break;

                        case EVENT_MOUTH_MUTE:
                           silenceSpeech = true;
                        break;

                        case EVENT_MOUTH_READY:
                           popNotification();
                        break;

                        case EVENT_NEWS_VIEWED:
                           ServiceState &= ~SERVICESTATE_NEWS_WAITING;
                           NotificationManager notificationMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                           notificationMgr.cancel(ID_NEWS_NOTIFICATION);
                           newsNotification = null;
                           reminderCount=0;
                           clearMessageReminder();
                           updateStatusBarInfo();
                        break;

                        case EVENT_POLICY_TIMER:
                           policyEventName = (String) event.getInfo();
                           logger.log(
                                 LogEntry.SEV_INFO,
                                 6,
                                 "Policy timer fired: " + policyEventName == null ? ""
                                       : policyEventName);
                           if (policyEventName != null)
                           {
                              outstandingAlarms.get(policyEventName).clear();
                              outstandingAlarms.remove(policyEventName);
                              actionSet = policyActions.get(policyEventName);
                              if (actionSet != null)
                              {
                                 for (iAction = 0; iAction < actionSet.size(); iAction++)
                                 {
                                    executeActions.add(actionSet.get(iAction));
                                 }
                              }
                           }
                        break;
                        case EVENT_TOGGLE_DEBUG_MODE:
                        {
                           // Save debug mode
                           int currentVal;
                           int newValue;
                           debugMode = !debugMode;
                           debugTalk = (prefs().getInt(
                                 getString(R.string.DEBUG_TALK), 0) == debugTalkValue)
                                 && debugMode;
                           currentVal = prefs().getInt(
                                 getString(R.string.DEBUG_MODE), 0);
                           if (currentVal == 0)
                           {
                              newValue = debugTalkValue;
                           } else
                           {
                              newValue = 0;
                           }
                           SharedPreferences.Editor editor = prefs().edit();
                           editor.putInt(getString(R.string.DEBUG_MODE),
                                 newValue);
                           editor.commit();

                           break;
                        }
                        case EVENT_SET_DEBUG_SPEAK:
                        {
                           // Save debug mode
                           boolean newVal = ((Boolean) event.getInfo())
                                 .booleanValue();
                           int newTalkVal;
                           if (newVal)
                           {
                              newTalkVal = debugTalkValue;
                           } else
                           {
                              newTalkVal = 0;
                           }
                           SharedPreferences.Editor editor = prefs().edit();
                           editor.putInt(getString(R.string.DEBUG_TALK),
                                 newTalkVal);
                           editor.commit();

                           debugTalk = newVal && debugMode;
                           
                           // debugTalk also means extra logging to the cloud
                           
                           if (debugTalk){
                        	   logger.setSeverityPolicy(LogProducer.POL_SEV_ERROR | LogProducer.POL_SEV_INFO | LogProducer.POL_SEV_WARNING);
                           }else{
                           	   logger.setSeverityPolicy(LogProducer.POL_SEV_ERROR);  
                           }

                           break;
                        }
                        case EVENT_PRIVATE_MODE:
                        {
                           Object modeInfo = event.getInfo();
                           int minutes;

                           if (modeInfo instanceof Integer)
                           {
                              minutes = ((Integer) event.getInfo()).intValue();
                           } else
                           {
                              String modeString = (String) modeInfo;
                              Integer modeInteger = new Integer(modeString);
                              minutes = modeInteger.intValue();
                           }
                           
                           if (minutes > 0)
                           {
                              logger.log(
                                    LogEntry.SEV_INFO,
                                    2,
                                    "Enter private mode for "
                                          + Integer.toString(minutes));
                              enablePrivateMode(minutes);
                           } else
                           {
                              logger.log(LogEntry.SEV_INFO, 2,
                                    "Exit private mode");
                              disablePrivateMode();
                           }
                        }
                        break;

                        case EVENT_PROMPT_ANSWERED:
                           speakDebug("Prompt answered");

                           String answerKey = (String) event.getInfo();

                           logger.log(
                                 LogEntry.SEV_INFO,
                                 2,
                                 "Prompt: "
                                       + activeNotificationBundle
                                             .getString(UserNotification.Key_Prompt)
                                       + ". Answered with "
                                       + activeNotificationBundle
                                             .getString(answerKey));

                           String answerValue = activeNotificationBundle
                                 .getString(UserNotification.Key_Answer_Value);
                           if (answerValue != null)
                           {
                              // TODO: Speak the answer received
                              speak("Sending: " + answerValue);
                           }

                           sendUserResponse(answerKey);

                           activeNotificationBundle = null;
                           ServiceState &= ~(SERVICESTATE_PROMPT_INUSE
                                 | SERVICESTATE_TALKING | SERVICESTATE_LISTENING);
                           notificationStartTime = 0;
                           popNotification();
                        break;

                        case EVENT_PROMPT_NOT_ANSWERED:
                           speakDebug("Prompt not answered");
                           boolean success = false;

                           if (!activeNotificationBundle.getBoolean(
                                 UserNotification.Key_Show_Dialog, false)
                                 && State(SERVICESTATE_LISTENING))
                           {
                              String lastResult = (String) event.getInfo();
                              logger.log(LogEntry.SEV_INFO, 5,
                                    "In listening state");
                              voiceRetryCount++;
                              boolean skip = (lastResult != null && lastResult
                                    .startsWith("Next"));

                              if (voiceRetryCount < 2 && !skip)
                              {
                                 logger.log(LogEntry.SEV_INFO, 5, "retry");
                                 String retryId = "retry"
                                       + Integer.toString(voiceRetryCount++);

                                 if (lastResult != null)
                                 {
                                    if (lastResult.startsWith("ERROR"))
                                    {
                                       // String[] values = lastResult.split(":");
                                       // success = mouth.synthesizeToFile("Got error: " + values[1] + ". Try again.", retryId);
                                       success = mouth.synthesizeToFile("Could not process that. Try again.", retryId);
                                    } else
                                    {
                                       success = mouth.synthesizeToFile("Heard: " + lastResult + ". Try again.", retryId);
                                    }

                                 } else
                                 {
                                    success = mouth.synthesizeToFile("Did not understand that. Try again.", retryId);
                                 }
                              }

                              if (!success)
                              {
                                 heraldNews();
                                 if (skip)
                                 {
                                    speak("Moving on.");
                                 } else
                                 {
                                    speak("Did not understand again. Moving on.");
                                 }
                                 activeNotificationBundle = null;
                                 ServiceState &= ~(SERVICESTATE_PROMPT_INUSE
                                       | SERVICESTATE_TALKING | SERVICESTATE_LISTENING);
                                 logger.log(LogEntry.SEV_INFO, 2,
                                       "Prompt not answered");
                                 notificationStartTime = 0;
                                 popNotification();
                              }
                           } else
                           {
                              if (!activeNotificationBundle.getBoolean(
                                    UserNotification.Key_Show_Dialog, false))
                              {
                                 heraldNews();
                              }
                              activeNotificationBundle = null;
                              ServiceState &= ~(SERVICESTATE_PROMPT_INUSE
                                    | SERVICESTATE_TALKING | SERVICESTATE_LISTENING);

                              logger.log(LogEntry.SEV_INFO, 2,
                                    "Prompt not answered");
                              notificationStartTime = 0;
                              popNotification();
                           }
                        break;

                        case EVENT_PROMPT_USER:
                        {
                           startNotification((Bundle) event.getInfo());
                        }
                        break;

                        case EVENT_PHONE_HOME_TIMER:
                           Time now = new Time();
                           long sincePhoneHome;
                           String subType = (String) event.getInfo();

                           if (subType == null)
                              subType = "unknown";
                           now.setToNow();

                           logger.log(LogEntry.SEV_INFO, 4,
                                 "Phone Home Timer Event, type: " + subType);

                           sincePhoneHome = now.toMillis(false)
                                 - phoneHomeTime.toMillis(false);
                           if (sincePhoneHome >= maxSilence)
                           {
                              executeActions.add(new PhoneHomeAction());
                              setupPhoneHomeTimer(maxSilence);
                           } else
                           {
                              setupPhoneHomeTimer(maxSilence - sincePhoneHome);
                           }
                        break;

                        case EVENT_SAVE_BATTERY:
                        {
                           Boolean save = (Boolean) event.getInfo();
                           speakDebug("Save battery " + save.toString());
                           storeSaveBattery(save.booleanValue());
                           if (ibProximityAlert != null)
                              ibProximityAlert.SaveBattery(save.booleanValue());
                        }
                        break;

                        case EVENT_SERVER_CONNECTED:
                           speakDebug("Connected");

                           logger.log(LogEntry.SEV_INFO, 2,
                                 "Connected to server");
                           // ServiceState &= ~SERVICESTATE_CONNECTING;
                           ServiceState |= SERVICESTATE_CONNECTED;
                           // Tell the server what versions we have
                           if (sendVersions())
                           {
                              // phone home
                              executeActions.add(new PhoneHomeAction());
                              updateStatusBarInfo();
                           }
                        break;

                        case EVENT_START_INTENT:
                        {
                           EventStartIntent intentAction = (EventStartIntent) event
                                 .getInfo();
                           speakDebug("Start intent action "
                                 + intentAction.getAction());
                           startIntentAction(intentAction.getAction(),
                                 intentAction.getData());
                        }
                        break;

                        case EVENT_DISCONNECTED:
                        {
                           ServiceState &= ~SERVICESTATE_CONNECTED;
                           updateStatusBarInfo();
                        }
                        break;

                        case EVENT_SERVER_MSG:
                           if (event.getInfo() != null)
                           {
                              String ackId;

                              try
                              {
                                 tcpChannelPayload channelPayload = (tcpChannelPayload) event
                                       .getInfo();
                                 msgFromServer = new NetworkMessage(
                                       channelPayload.getMsg());
                                 logger.log(LogEntry.SEV_INFO, 8,
                                       "Got msg from server: " + msgFromServer);

                                 ackId = msgFromServer.getAckId();

                                 // Parse a particular message (Configuration,
                                 // Policy, etc.)
                                 if (msgFromServer.isPolicy())
                                 {
                                    // Set policy version in preferences
                                    if (!channelPayload.getConnectionId()
                                          .equalsIgnoreCase(
                                                getString(R.string.KEY_LOCAL)))
                                    {
                                       storePolicyVersion(msgFromServer
                                             .getPayloadVersion());
                                       storePolicy(channelPayload.getMsg());
                                       logger.log(LogEntry.SEV_INFO, 2,
                                             "Got policy from server");
                                       speakDebug("Got new policy");
                                    }
                                    setupPlacesAndActions(msgFromServer);

                                    // Go through the outstanding policy timers
                                    // and stop them
                                    // TODO - Clear alarms only for deleted
                                    // places
                                    for (EventAlarmReceiver alarm : outstandingAlarms
                                          .values())
                                    {
                                       alarm.clear();
                                    }
                                 } else if (msgFromServer.isConfig())
                                 {
                                    if (!channelPayload.getConnectionId()
                                          .equalsIgnoreCase(
                                                getString(R.string.KEY_LOCAL)))
                                    {
                                       storeConfigVersion(msgFromServer
                                             .getPayloadVersion());
                                       storeConfig(channelPayload.getMsg());
                                       logger.log(LogEntry.SEV_INFO, 2,
                                             "Got config from server");
                                       speakDebug("Got new config");
                                    }
                                    if (maxSilence != msgFromServer
                                          .getMaxSilence())
                                    {
                                       maxSilence = msgFromServer
                                             .getMaxSilence();
                                       setupPhoneHomeTimer(maxSilence);
                                    }
                                 } else if (msgFromServer.isNotifyUser())
                                 {
                                    Date sentDate = null;
                                    Date currentDate;
                                    long deltaDate;

                                    if (ackId == null || ackId.length() <= 0)
                                    {
                                       String nullAckMsg = "Ack ID is null";
                                       speakDebug(nullAckMsg);
                                       logger.log(LogEntry.SEV_WARNING, 5,
                                             nullAckMsg);
                                    }

                                    if (!NetworkMessage.isDuplicate(ackId))
                                    {
                                       SimpleDateFormat dateFormat = new SimpleDateFormat(
                                             "yyyy-MM-dd'T'HH:mm:ss.SSSZ");

                                       try
                                       {
                                          sentDate = dateFormat
                                                .parse(msgFromServer
                                                      .getSentTime());
                                       } catch (Exception parseException)
                                       {
                                          logger.log(LogEntry.SEV_WARNING, 10, 
                                                "Failed date parse " + parseException.toString());
                                       }
                                       deltaDate = 0;
                                       if (sentDate != null)
                                       {
                                          currentDate = new Date(System
                                                .currentTimeMillis());
                                          deltaDate = currentDate.getTime()
                                                - sentDate.getTime();
                                          deltaDate = deltaDate / 1000 / 60; // To
                                                                             // minutes
                                       }

                                       String fromSenderName = msgFromServer.getSender();
                                       String fromSenderNameStr = "";
                                       if (fromSenderName != null
                                             && fromSenderName.length() > 0)
                                       {
                                          fromSenderNameStr = "From "
                                                + msgFromServer.getSender();
                                       }
                                       Bundle bundle = createUserNotificationBundle(
                                             fromSenderNameStr
                                                   + " \n"
                                                   + msgFromServer
                                                         .getNotification(),
                                             msgFromServer.getPositiveMessage(),
                                             null,
                                             msgFromServer.getNeutralMessage(),
                                             null,
                                             msgFromServer.getNegativeMessage(),
                                             null, msgFromServer.getAckId(),
                                             msgFromServer.getSourceIbid());

                                       if (sentDate != null)
                                          bundle.putLong(
                                                UserNotification.Key_Server_Sent_Time,
                                                sentDate.getTime());

                                       if (deltaDate < 15)
                                       {
                                          speakDebug("Got notify user");
                                          if(getSummarizeMessages()) {
                                             addToMessageSummary(msgFromServer.getSender());
                                          } else {
                                             startNotification(bundle);
                                          }
                                       } else
                                       {
                                          heraldNews();
                                          logger.log(LogEntry.SEV_INFO, 2,
                                                "User notification message was stale");
                                          speakDebug("Stale user notification");
                                       }
                                    } else
                                    {
                                       String duplicateMsg = "Got duplicate message ";
                                       speakDebug(duplicateMsg);
                                       logger.log(LogEntry.SEV_WARNING, 5,
                                             duplicateMsg + ackId);
                                    }
                                 } else if (msgFromServer.isError())
                                 {
                                    speakDebug(msgFromServer
                                          .getErrorDescription().substring(
                                                0,
                                                Math.min(32, msgFromServer
                                                      .getErrorDescription()
                                                      .length())));
                                    logger.log(
                                          LogEntry.SEV_ERROR,
                                          2,
                                          "Error from server. "
                                                + msgFromServer.getErrorCode()
                                                + " "
                                                + msgFromServer
                                                      .getErrorDescription());

                                    if (msgFromServer.getErrorCode().equalsIgnoreCase("IB_ERROR_OLD_DEVICE"))
                                    {
                                       multipleLoginsFound = true;
                                       clearTicket();
                                       broadcastServiceEvent(ServiceEvent.EventType.EVENT_TICKET);

                                       Bundle multipleDevicesBundle = createTransparentActivityDialogBundle(
                                             getString(R.string.DIALOG_MSG_MULTIPLE_DEVICES),
                                             "OK",
                                             null, null, null, null, null, null, null);

                                       startNotification(multipleDevicesBundle);
                                    }
                                 } else
                                 {
                                    logger.log(LogEntry.SEV_ERROR, 2,
                                          "Unknown message from server");
                                 }

                                 // Send acknowledgment message if requested
                                 if (ackId != null && ackId.length() > 0)
                                 {
                                    try
                                    {
                                       NetworkMessage ackMsg = new NetworkMessage();
                                       ackMsg.putHeader(
                                             NetworkMessage.KEY_PAYLOAD_USER_RESPONSE,
                                             getPolicyVersion(),
                                             getConfigVersion(), ackId,
                                             msgFromServer.getSourceIbid());
                                       ackMsg.putPayloadAck();
                                       
                                       // TODO: FOR DEBUG REMOVE!!
                                       logger.log(LogEntry.SEV_INFO, 1, "Send ack: " + ackMsg.toString());
                                       // TODO: REMOVE!!
                                       
                                       tcpPortal.sendMessage(ackMsg.toString());
                                    } catch (Exception ackException)
                                    {
                                       logger.log(LogEntry.SEV_ERROR, 2,
                                             "Could not create or queue acknowledgement: "
                                                   + ackException.toString());
                                       speakDebug("Unable to ack");
                                    }
                                 }
                              } catch (Exception parseException)
                              {
                                 String debugMsg = "Exception handling server message ";
                                 logger.log(LogEntry.SEV_ERROR, 2, debugMsg
                                       + parseException.toString());
                                 speakDebug(debugMsg);
                              }
                           }
                        break;

                        case EVENT_SPEECH_OVER:
                           ServiceState &= ~SERVICESTATE_TALKING;
                           // Start listener
                           startVoiceListener();
                        break;

                        case EVENT_SYNTHESIZE_DONE:
                           // Start player
                           startPlayer((String) event.getInfo());
                        break;

                        case EVENT_TICKET:
                        {
                           boolean track = false;
                           boolean result = false;
                           int tryTrack = 0;

                           logger.log(LogEntry.SEV_INFO, 2, "Got a ticket");

                           speakDebug("Ticket event");

                           tkt = readTicket();
                           if (tkt != null)
                           {
                              ServiceState |= SERVICESTATE_TKT;

                              if (!State(SERVICESTATE_PRIVATE))
                              {
                                 track = true;

                                 try
                                 {
                                    logicalConnectionEventsQ
                                          .put(new tcpLogicConnectionEvent(
                                                tcpLogicConnectionEvent.logicalConnEvent.CONN_ON,
                                                tkt));
                                 } catch (InterruptedException e)
                                 {
                                    String errMsg = "Failed to send connection onn command to TCP, on ticket event";
                                    speakDebug(errMsg);
                                    logger.log(LogEntry.SEV_ERROR, 4, errMsg
                                          + e.toString());
                                 }
                              }
                           } else
                           {
                              ServiceState &= ~SERVICESTATE_TKT;
                              track = false;

                              try
                              {
                                 logicalConnectionEventsQ
                                       .put(new tcpLogicConnectionEvent(
                                             tcpLogicConnectionEvent.logicalConnEvent.CONN_OFF));
                              } catch (InterruptedException e)
                              {
                                 String errMsg1 = "Failed to send connection off to TCP";
                                 speakDebug(errMsg1);
                                 logger.log(LogEntry.SEV_ERROR, 4,
                                       errMsg1 + e.toString());
                              }
                           }

                           do
                           { // Some defensive coding here until we figure out
                             // what is going on
                              try
                              {
                                 if (ibProximityAlert != null)
                                    result = ibProximityAlert.Track(track);
                              } catch (Exception trackException)
                              {
                                 logger.log(LogEntry.SEV_ERROR, 1,
                                       "ibProximityAlert.Track failed: "
                                             + trackException.toString());
                                 result = false;
                              }
                              tryTrack++;
                           } while (result == true && tryTrack < 3);

                           if (result == false)
                              logger.log(LogEntry.SEV_ERROR, 1,
                                    "Could not turn on proximity.");

                           updateStatusBarInfo();

                           // For testing only!
                           // testDialogs();
                           // testUserNotify();
                           // !!!!!!!!!!!!!!!!!
                        }
                        break;

                        case EVENT_USER_READY: // Check if there is a waiting
                                               // notification
                           popNotification();
                        break;

                        case EVENT_SUMMARIZE_MESSAGES:
                        {
                           reminderCount=0;
                           heraldNews();
                           summarizeMsgAlarm = null;
                           Bundle summaryBundle = createUserNotificationBundle(
                                 summarizeMessages(), "OK", 
                                 null, null, null, null,null, null, null);
                           msgSenders.clear();
                           summaryBundle.putBoolean(getString(R.string.KEY_SUMMARIZE_MESSAGES), true);
                           startNotification(summaryBundle);
                        }
                        break;

                        case EVENT_MESSAGE_REMINDER:
                        {
                           clearMessageReminder();
                           int maxReminders = getMaxReminders();
                           if(maxReminders > 0 && reminderCount <= maxReminders)
                           {
                              Bundle summaryBundle = createUserNotificationBundle(
                                    "Genie still has a message for you.", "OK", 
                                    null, null, null, null,null, null, null);
                              summaryBundle.putBoolean(getString(R.string.KEY_SUMMARIZE_MESSAGES), true);
                              startNotification(summaryBundle);
                           } else {
                              reminderCount = 0;
                           }
                        }
                        break;                        
                        
                        default:
                        {
                           // Complain
                           String complaint = "Unknown event";
                           logger.log(LogEntry.SEV_ERROR, 1, complaint);
                           speakDebug(complaint);
                        }
                        break;
                     }

                     logger.log(LogEntry.SEV_INFO, 11, "Done with event: "
                           + event.getType().toString());
                  // Burn all the events and collect all the policy actions   
                  } while (!eventQ.isEmpty() || waitForProximity); 
                  
                  logger.log(LogEntry.SEV_INFO, 2, "Check for actions ");

                  phonedHome = false;

                  // Execute actions
                  for (iAction = 0; iAction < executeActions.size(); iAction++)
                  {
                     try
                     {
                        action = executeActions.get(iAction);
                        switch (action.getType())
                        {
                           case ACTION_PHONE_HOME:
                              if (currentLocation == null)
                              {
                                 here = ibProximityAlert.getLastLocation();
                              } else
                              {
                                 here = currentLocation;
                              }

                              if (here == null)
                                 speakDebug("No location");

                              if (!phonedHome)
                              {
                                 if (here != null)
                                 {
                                    Bundle locBundle = here.getExtras();
                                    if (locBundle == null)
                                    {
                                       locBundle = new Bundle();
                                       here.setExtras(locBundle);
                                    }
                                    locBundle.putBoolean(
                                          EventType.EVENT_LOCATION_ENTERED
                                                .name(), locationEntered);
                                    locBundle.putBoolean(
                                          EventType.EVENT_LOCATION_DEPARTED
                                                .name(), locationDeparted);
                                 }
                                 phoneHome(here);
                                 phonedHome = true;
                                 currentLocation = null;
                              }
                           break;

                           case ACTION_START_TIMER:
                              speakDebug("Start policy timer");
                              startTimerAction = (StartTimerAction) action;
                              outstandingAlarms.put(
                                    startTimerAction.getName(),
                                    startTimedEvent(
                                          startTimerAction.getSeconds(),
                                          ServiceEvent.EventType.EVENT_POLICY_TIMER,
                                          startTimerAction.getName()));
                              logger.log(LogEntry.SEV_INFO, 8,
                                    "Action: Start policy timer for "
                                          + startTimerAction.getName());
                           break;

                           default:
                              speakDebug("Action unknown");
                              logger.log(LogEntry.SEV_ERROR, 2,
                                    "Unknown action type");
                           break;
                        }
                     } catch (Exception actionException)
                     {
                        logger.log(
                              LogEntry.SEV_ERROR,
                              2,
                              "Exception processing action. "
                                    + actionException.toString());
                        speakDebug("Exception processing action "
                              + actionException.toString());
                     }
                  }
                  executeActions.clear();
               } catch (Exception eDequeue)
               {
                  Log.d(getString(R.string.LOG_TAG), "Got exception: " + eDequeue);
               }
               ;

               locationEntered = false;
               locationDeparted = false;
            } // while(true)
         }
      }).start();
   }

   protected void testDialogs()
   {
      // TEST CODE //
      try
      {
         Bundle testBundle;

         testBundle = createTransparentActivityDialogBundle(
               "Genie call 4255032246 x",
               // "From tester: genie call garbage as a test",
               "OK", null, null, null, null, null, null, null);
         eventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_PROMPT_USER,
               testBundle));

         testBundle = createTransparentActivityDialogBundle(
               "From tester: Test dialog 1", "OK", null, null, null, "Cancel",
               null, null, null);
         eventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_PROMPT_USER,
               testBundle));

         /*
          * testBundle = createTransparentActivityDialogBundle(
          * "From tester: Test dialog 2", "DidIt", null, "Maybe", null,
          * "KillIt", null, null, null); eventQ.put(new
          * ServiceEvent(ServiceEvent.EventType.EVENT_PROMPT_USER, testBundle));
          * 
          * testBundle = createTransparentActivityDialogBundle(
          * "From tester: Test dialog 3", "Sure", null, "Maybe", null, "No",
          * null, null, null); eventQ.put(new
          * ServiceEvent(ServiceEvent.EventType.EVENT_PROMPT_USER, testBundle));
          * 
          * testBundle = createTransparentActivityDialogBundle(
          * "From tester: Test dialog 4", "OK", null, null, null, "Cancel",
          * null, null, null); eventQ.put(new
          * ServiceEvent(ServiceEvent.EventType.EVENT_PROMPT_USER, testBundle));
          */
      } catch (Exception testException)
      {
         logger.log(LogEntry.SEV_ERROR, 1, "Could not create test: "
               + testException.toString());
      }
      ;
   }

   /*private void testUserNotify()
   {
      tcpChannelPayload payload;

      payload = new tcpChannelPayload(
            "1",
            "[{\"header\":[{\"ackId\":\"5de0591b-30d7-4da1-be17-928efeece8f4\",\"type\":\"homeReply\",\"sourceIbid\":\"d9a53095-173b-49ac-b5f2-b94f4e126b24\",\"payloadSchemaId\":\"notifyUserSchema.js\",\"version\":1}]},{\"toPrinciples\":[{\"selectorIndex\":1,\"self\":true,\"users\":[\"d7b47ecc-f330-46f9-bf23-c024c5b9799f\"],\"fLists\":[]}],\"placeId\":[{\"value\":\"210a7242-5b0d-46cb-8e4f-e1f847b20e17\"}],\"msgTxt\":[{\"value\":\"Matt left, you can call me genie call 4255032246\",\"sentTime\":\"2012-08-03T05:10:45.294Z\",\"st\":1333911465294}],\"actionButtons\":[{\"text\":\"OK\"}]}]");
      try
      {
         eventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_SERVER_MSG,
               payload));
      } catch (Exception putException)
      {

      }
   }*/

   private SharedPreferences prefs()
   {
      if (preferencesInstance == null)
      {
         preferencesInstance = PreferenceManager
               .getDefaultSharedPreferences(getAppContext());
      }
      return preferencesInstance;
   }

   private void storePolicyVersion(int newPolicyVersion)
   {
      try
      {
         SharedPreferences.Editor editor = prefs().edit();
         editor.putInt(getString(R.string.KEY_POLICY_VERSION), newPolicyVersion);
         editor.commit();
      } catch (Exception prefException)
      {
         logger.log(LogEntry.SEV_ERROR, 2,
               "Could not save policy version to shared prefs. "
                     + prefException.toString());
      }
      policyVersion = newPolicyVersion;
   }

   private int readPolicyVersion()
   {
      try
      {
         if (getPolicy() != null)
         {
            policyVersion = prefs().getInt(
                  getString(R.string.KEY_POLICY_VERSION), -1);
         } else
         {
            policyVersion = -1;
            storePolicyVersion(policyVersion);
         }
      } catch (Exception prefException)
      {
         logger.log(LogEntry.SEV_ERROR, 2,
               "Could not read policy version from shared prefs. "
                     + prefException.toString());
      }

      return (policyVersion);
   }

   public int getPolicyVersion()
   {
      return policyVersion;
   }

   private void storePolicy(String policyMsg)
   {
      try
      {
         SharedPreferences.Editor editor = prefs().edit();
         editor.putString(getString(R.string.KEY_POLICY_MESSAGE), policyMsg);
         editor.commit();
      } catch (Exception prefException)
      {
         logger.log(LogEntry.SEV_ERROR, 2, "Could not save policy. "
               + prefException.toString());
         storePolicyVersion(-1);
      }
   }

   private String getPolicy()
   {
      String policyMsg = null;
      try
      {
         policyMsg = prefs().getString(getString(R.string.KEY_POLICY_MESSAGE),
               null);
      } catch (Exception prefException)
      {
         logger.log(
               LogEntry.SEV_ERROR,
               2,
               "Could not read policy from shared prefs. "
                     + prefException.toString());
      }

      return (policyMsg);
   }

   private void restorePolicy()
   {
      String policy = getPolicy();
      if (policy != null)
      {
         try
         {
            tcpChannelPayload payload = new tcpChannelPayload(
                  getString(R.string.KEY_LOCAL), policy);
            eventQ.put(new ServiceEvent(
                  ServiceEvent.EventType.EVENT_SERVER_MSG, payload));
            String logMsg = "Restore policy";
            logger.log(LogEntry.SEV_INFO, 2, logMsg);
            speakDebug(logMsg);
         } catch (Exception ePut)
         {
            logger.log(LogEntry.SEV_ERROR, 1, "Unable to restore policy. "
                  + ePut.toString());
         }
         ;
      }
   }

   private void storeConfigVersion(int newConfigVersion)
   {
      try
      {
         SharedPreferences.Editor editor = prefs().edit();
         editor.putInt(getString(R.string.KEY_CONFIG_VERSION), newConfigVersion);
         editor.commit();
      } catch (Exception prefException)
      {
         logger.log(LogEntry.SEV_ERROR, 2,
               "Could not save config version to shared prefs. "
                     + prefException.toString());
      }
      configVersion = newConfigVersion;
   }

   private int readConfigVersion()
   {
      try
      {
         if (getConfig() != null)
         {
            configVersion = prefs().getInt(
                  getString(R.string.KEY_CONFIG_VERSION), -1);
         } else
         {
            configVersion = -1;
            storeConfigVersion(configVersion);
         }
      } catch (Exception prefException)
      {
         logger.log(LogEntry.SEV_ERROR, 2,
               "Could not read config version from shared prefs. "
                     + prefException.toString());
      }
      return (configVersion);
   }

   public int getConfigVersion()
   {
      return configVersion;
   }

   private void storeConfig(String configMsg)
   {
      try
      {
         SharedPreferences.Editor editor = prefs().edit();
         editor.putString(getString(R.string.KEY_CONFIG_MESSAGE), configMsg);
         editor.commit();
      } catch (Exception prefException)
      {
         logger.log(LogEntry.SEV_ERROR, 2, "Could not save config. "
               + prefException.toString());
         storeConfigVersion(-1);
      }
   }

   private String getConfig()
   {
      String configMsg = null;
      try
      {
         configMsg = prefs().getString(getString(R.string.KEY_CONFIG_MESSAGE),
               null);
      } catch (Exception prefException)
      {
         logger.log(
               LogEntry.SEV_ERROR,
               2,
               "Could not read config from shared prefs. "
                     + prefException.toString());
      }

      return (configMsg);
   }

   private void restoreConfig()
   {
      String config = getConfig();
      if (config != null)
      {
         try
         {
            tcpChannelPayload payload = new tcpChannelPayload(
                  getString(R.string.KEY_LOCAL), config);
            eventQ.put(new ServiceEvent(
                  ServiceEvent.EventType.EVENT_SERVER_MSG, payload));
            String logMsg = "Restore config";
            logger.log(LogEntry.SEV_INFO, 2, logMsg);
            speakDebug(logMsg);
         } catch (Exception ePut)
         {
            logger.log(LogEntry.SEV_ERROR, 1, "Unable to restore config. "
                  + ePut.toString());
         }
         ;
      }
   }

   private boolean setupPlacesAndActions(NetworkMessage msg)
   {
      int iPlace;
      JSONObject placeJSON;
      JSONObject placeActionsJSON;
      boolean result = false;
      Place place;

      policyActions.clear();

      // The wire protocol convention is that policy will have a places array
      // and
      // an action array that matches it (e.g. places[i] has actions actions[i])
      Place[] placeArray = new Place[msg.numPlaces()];
      for (iPlace = 0; iPlace < msg.numPlaces(); iPlace++)
      {
         placeJSON = msg.getPlace(iPlace);
         try
         {
            place = new Place(placeJSON.getDouble(NetworkMessage.KEY_LATITUDE),
                  placeJSON.getDouble(NetworkMessage.KEY_LONGITUDE),
                  (float) placeJSON.getDouble(NetworkMessage.KEY_RADIUS));

            placeActionsJSON = msg.getPlaceActions(iPlace);

            result = readPlaceActions(
                  placeActionsJSON.getJSONArray(NetworkMessage.KEY_DEPART),
                  place.getKey() + Place.DEPARTED);

            if (result)
            {
               result = readPlaceActions(
                     placeActionsJSON.getJSONArray(NetworkMessage.KEY_ENTER),
                     place.getKey() + Place.ENTERED);
               if (result)
               {
                  placeHash.put(place.getKey(), place);
               }
            }
            placeArray[iPlace] = place;
         } catch (Exception loopException)
         {
            // reject
            return false;
         }
      }
      ibProximityAlert.setPlaces(placeArray);

      return true;
   }

   // Read actions for a place
   private boolean readPlaceActions(JSONArray actionsJSONArray, String tag)
   {
      ActionSet actionSet = new ActionSet();
      ActionSet delayedActionSet;
      int index;
      JSONObject actionJSON;
      int delay = 0;

      try
      {
         for (index = 0; index < actionsJSONArray.length(); index++)
         {
            delay = 0;
            actionJSON = actionsJSONArray.getJSONObject(index);
            try
            {
               delay = actionJSON.getInt(NetworkMessage.KEY_DELAY) * 60; // Convert
                                                                         // from
                                                                         // min
                                                                         // to
                                                                         // sec
            } catch (Exception delayException)
            {
               delay = 0;
            }

            if (delay > 0)
            {
               String timerName = "TIMER_" + tag + "_"
                     + Integer.toString(timerSequence);
               timerSequence += 1;
               actionSet.add(new StartTimerAction(timerName, delay));

               // I'm cheating here because I know this delay must come with a
               // phone home and only a phone home
               delayedActionSet = new ActionSet();
               delayedActionSet.add(new PhoneHomeAction());
               policyActions.put(timerName, delayedActionSet);
            } else
            {
               // I'm cheating here because I know the only action other than
               // delay is phone home
               actionSet.add(new PhoneHomeAction());
            }
         }
         if (actionsJSONArray.length() > 0)
         {
            policyActions.put(tag, actionSet);
         }
         return true;
      } catch (Exception parseException)
      {
         logger.log(LogEntry.SEV_ERROR, 2, "Parse policy exception"
               + parseException.toString());
         speakDebug("Policy parse exception");
         return false;
      }
   }

   private EventAlarmReceiver startTimedEvent(long seconds,
         ServiceEvent.EventType eventType, String eventInfo)
   {
      Bundle eventBundle = new Bundle();

      eventBundle.putString("net.inbetween.receivers.EVENT_TYPE",
            eventType.name());
      if (eventInfo != null)
      {
         eventBundle.putString("net.inbetween.receivers.EVENT_INFO", eventInfo);
      }

      return (startTimedEvent(seconds, eventBundle));
   }

   private EventAlarmReceiver startTimedEvent(long seconds, Bundle eventBundle)
   {
      EventAlarmReceiver eventAlarmReceiver;

      String action = timedEvent_Action
            + eventBundle.getString("net.inbetween.receivers.EVENT_TYPE") + "."
            + eventBundle.getString("net.inbetween.receivers.EVENT_INFO");

      eventAlarmReceiver = new EventAlarmReceiver(appContext, action,
            eventBundle, seconds * 1000, eventQ, logger);

      logger.log(
            LogEntry.SEV_INFO,
            8,
            "Start timed event for "
                  + eventBundle.getString("net.inbetween.receivers.EVENT_TYPE")
                  + " in " + Long.toString(seconds) + "sec");

      return (eventAlarmReceiver);
   }

   public void phoneHomeNow()
   {
      if (eventQ == null)
         return;

      try
      {
         eventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_PHONE_HOME,
               null));
      } catch (Exception ePut)
      {
      }
      ;
   }

   private void phoneHome(Location loc)
   {
      try
      {
         if (State(SERVICESTATE_PRIVATE) || !State(SERVICESTATE_TKT))
         {
            return;
         }

         /*
          * if(loc!=null && loc.getProvider().equalsIgnoreCase("network")) {
          * loc.setProvider("Cell"); }
          */
         logger.log(LogEntry.SEV_INFO, 4, "Phoning home");
         NetworkMessage msg = new NetworkMessage();
         msg.putHeader(NetworkMessage.DATA_PAYLOAD_SCHEMA, policyVersion,
               configVersion);
         msg.putPayloadSchema1Body(loc);

         tcpPortal.sendMessage(msg.toString());
         phoneHomeTime.setToNow();

         speakDebug("Phone home");
      } catch (Exception newException)
      {
         speakDebug("Phone home exception");
         logger.log(
               LogEntry.SEV_ERROR,
               2,
               "Phone home failed. " + newException.getMessage() != null ? newException
                     .getMessage() : "");
         return;
      }
   }

   private void sendUserResponse(String answerKey)
   {
      String intentAction = null;
      boolean isBroadcastIntent = false;

      try
      {
         /* Get the answer's intent action */
         if (answerKey.contentEquals(UserNotification.Key_Positive_Answer))
         {
            intentAction = activeNotificationBundle
                  .getString(UserNotification.Key_Positive_Answer_Intent);
            isBroadcastIntent = activeNotificationBundle.getBoolean(
                  UserNotification.Key_Positive_Answer_Intent_Broadcasted,
                  false);
         } else if (answerKey
               .contentEquals(UserNotification.Key_Neutral_Answer))
         {
            intentAction = activeNotificationBundle
                  .getString(UserNotification.Key_Neutral_Answer_Intent);
         } else if (answerKey
               .contentEquals(UserNotification.Key_Negative_Answer))
         {
            intentAction = activeNotificationBundle
                  .getString(UserNotification.Key_Negative_Answer_Intent);
         }

         /* If the answer had an intent action, perform it */
         if (intentAction != null)
         {
            Intent answerIntent = new Intent(intentAction);

            if (intentAction.equals(Intent.ACTION_CALL))
            {
               answerIntent.setData(Uri.parse(activeNotificationBundle
                     .getString(UserNotification.Key_Intent_Data)));
            }

            if (isBroadcastIntent)
            {
               broadcastServiceEvent(ServiceEvent.EventType
                     .valueOf(activeNotificationBundle
                           .getString(getString(R.string.KEY_SERVICE_EVENT_TYPE))));
            } else
            {
               answerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
               startActivity(answerIntent);
            }
         }

         String ackId = activeNotificationBundle
               .getString(NetworkMessage.KEY_ACK_ID);

         // See if this answer should not be sent up to the director
         if (ackId == null || ackId.startsWith(getString(R.string.KEY_LOCAL)))
         {
            return;
         }

         speakDebug("Send user response");

         // Tell the server
         NetworkMessage msg = new NetworkMessage();
         msg.putHeader(NetworkMessage.KEY_PAYLOAD_USER_RESPONSE, policyVersion,
               configVersion, activeNotificationBundle
                     .getString(NetworkMessage.KEY_ACK_ID),
               activeNotificationBundle
                     .getString(NetworkMessage.KEY_SOURCE_IBID));

         msg.putPayloadUserResponse(activeNotificationBundle
               .getString(answerKey));

         tcpPortal.sendMessage(msg.toString());
      } catch (Exception newException)
      {
         logger.log(
               LogEntry.SEV_ERROR,
               2,
               "Unable to process userNotification response. "
                     + newException.toString());
         return;
      }
   }

   private void startStatusBar()
   {
      CharSequence contentTitle = getString(R.string.app_name);
      CharSequence contentText = getString(R.string.SLOGAN);

      statusBarNotification = new Notification(
            net.inbetween.webview.R.drawable.xw_icon_red, contentTitle + ": "
                  + contentText, System.currentTimeMillis());            
 
      statusBarNotification.flags |= Notification.FLAG_ONGOING_EVENT;

      Intent notificationIntent = new Intent(getBaseContext(),
            net.inbetween.webview.IbWebview.class);

      contentIntent = PendingIntent.getActivity(getApplicationContext(), 0,
            notificationIntent, Intent.FLAG_ACTIVITY_NEW_TASK);

      if (statusBarNotification != null && contentIntent != null)
      {
         // This call is deprecated in 3.0, use NotificationBuilder instead when
         // we get there
         statusBarNotification.setLatestEventInfo(getApplicationContext(),
               getString(R.string.app_name), getStatusBarInfo(), contentIntent);

         startForeground(statusBarId, statusBarNotification);
      }
   }

   private void updateStatusBarInfo()
   {
      updateStatusBarInfo(0, false);
   }

   private void updateStatusBarInfo(int inDefaults, boolean force)
   {
      int defaults = inDefaults | Notification.DEFAULT_LIGHTS;

      if (statusBarNotification != null && contentIntent != null)
      {
         CharSequence newText;

         newText = getStatusBarInfo();
         if (statusBarText == null
               || newText.toString().contentEquals(statusBarText) == false
               || force)
         {
            statusBarText = newText;

            Time currentTime = new Time();
            currentTime.setToNow();

            if (notificationTime == null
                  || (currentTime.toMillis(false)
                        - notificationTime.toMillis(false) > 30000))
            {
               if (notificationTime == null)
               {
                  notificationTime = new Time();
               }
               notificationTime.setToNow();

               if (NotificationIndicators == true)
               {
                  statusBarNotification.defaults = defaults;
               } else
               {
                  statusBarNotification.defaults = 0;
               }
            } else
            {
               statusBarNotification.defaults = 0;
            }

            // setLatestEventInfo is deprecated in 3.0, use NotificationBuilder
            // instead when we get there
            if (!statusBarText
                  .equals(getString(R.string.STATUS_MSG_DISCONNECTED)))
            {
               if(!State(SERVICESTATE_NEWS_WAITING)) {
                  statusBarNotification.setLatestEventInfo(
                        getApplicationContext(), getString(R.string.app_name),
                        statusBarText, contentIntent);
               } else {
                  Intent webviewIntent = new Intent(getBaseContext(),
                        net.inbetween.webview.IbWebview.class);
                  webviewIntent.setAction("net.inbetween.webview.START_IN_NEWS");

                  PendingIntent newsIntent = PendingIntent.getActivity(this, 0,
                        webviewIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                  statusBarNotification.setLatestEventInfo(
                        getApplicationContext(), getString(R.string.app_name),
                        statusBarText, newsIntent);                  
                  
               }
               startForeground(statusBarId, statusBarNotification);
            } else
            {
               if (disconnectedTimeStamp == 0)
               {
                  disconnectedTimeStamp = currentTime.toMillis(false);
               } else if ((((currentTime.toMillis(false) - disconnectedTimeStamp) / 1000) >= 32)
                     || disconnectedTimeStamp == -1)
               {
                  disconnectedTimeStamp = 0;
                  statusBarNotification.setLatestEventInfo(
                        getApplicationContext(), getString(R.string.app_name),
                        statusBarText, contentIntent);
                  startForeground(statusBarId, statusBarNotification);
               }
            }

            // Don't turn on the notification bells and lights until we get
            // connected once
            if (State(SERVICESTATE_CONNECTED))
            {
               NotificationIndicators = true;
            }
         }
      }
   }

   private CharSequence getStatusBarInfo()
   {
      CharSequence contentText;

      Intent notificationIntent;
      notificationIntent = new Intent(getBaseContext(),
            net.inbetween.webview.IbWebview.class);

      if (!State(SERVICESTATE_TKT))
      {
         if (multipleLoginsFound)
         {
            contentText = getString(R.string.STATUS_MSG_LOGGED_OUT)
                  + getString(R.string.STATUS_MSG_LOGGED_OUT_MULT);
            multipleLoginsFound = false;
         } else
         {
            contentText = getString(R.string.STATUS_MSG_LOGGED_OUT);
         }

         statusBarNotification.icon = net.inbetween.webview.R.drawable.xw_icon_yellow;
      } else if (State(SERVICESTATE_NEWS_WAITING)) {
         contentText = getString(R.string.DIALOG_MSG_GENIE);
         statusBarNotification.icon = net.inbetween.webview.R.drawable.news;
      } else if (State(SERVICESTATE_PRIVATE))
      {
         contentText = getString(R.string.STATUS_MSG_PRIVATE_MODE);
         statusBarNotification.icon = net.inbetween.webview.R.drawable.xw_icon_yellow;
      } else if (State(SERVICESTATE_NEED_WIFI_ENABLED))
      {
         contentText = getString(R.string.STATUS_MSG_ENABLE_WIFI);
         statusBarNotification.icon = net.inbetween.webview.R.drawable.xw_icon_red;
      } else if (State(SERVICESTATE_NO_CONNECTIVITY))
      {
         contentText = getString(R.string.STATUS_MSG_NO_NETWORK);
         statusBarNotification.icon = net.inbetween.webview.R.drawable.xw_icon_red;
      } else if (State(SERVICESTATE_NEEDPROVIDER))
      {
         contentText = getString(R.string.STATUS_MSG_NEED_PROVIDER);
         statusBarNotification.icon = net.inbetween.webview.R.drawable.xw_icon_yellow;
      } else if (!State(SERVICESTATE_CONNECTED))
      {
         contentText = getString(R.string.STATUS_MSG_DISCONNECTED);
         statusBarNotification.icon = net.inbetween.webview.R.drawable.xw_icon_red;
      } else
      {
         contentText = getString(R.string.STATUS_MSG_CONNECTED);
         statusBarNotification.icon = net.inbetween.webview.R.drawable.xw_icon;
      }

      contentIntent = PendingIntent.getActivity(getApplicationContext(), 0,
            notificationIntent, Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

      return contentText;
   }

   public boolean promptUser(String prompt, String positiveAnswer,
         String neutralAnswer, String negativeAnswer)
   {
      Bundle bundle;

      if (prompt == null || positiveAnswer == null || eventQ == null)
         return false;

      bundle = createTransparentActivityDialogBundle(prompt, positiveAnswer,
            null, neutralAnswer, null, negativeAnswer, null, null, null);

      try
      {
         eventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_PROMPT_USER,
               bundle));
         return true;
      } catch (Exception putException)
      {
         return false;
      }
   }

   private void startConnectivityReceiver()
   {
      connectivityReceiver = new ConnectivityReceiver(logicalConnectionEventsQ);
      IntentFilter filter = new IntentFilter(
            ConnectivityManager.CONNECTIVITY_ACTION);
      registerReceiver(connectivityReceiver, filter);
   }

   private void stopConnectivityReceiver()
   {
      if (connectivityReceiver != null)
      {
         unregisterReceiver(connectivityReceiver);
      }
   }

   private void setupPhoneHomeTimer(long duration)
   {
      if (wakeupReceiver == null)
      {
         IntentFilter filter = new IntentFilter(genieAwaken_Action);
         wakeupReceiver = new WakeUpReceiver(eventQ);
         registerReceiver((BroadcastReceiver) wakeupReceiver, filter);
      }

      Intent wakeupIntent = new Intent(genieAwaken_Action);

      PendingIntent pendingServiceIntent = PendingIntent.getBroadcast(
            appContext, 0, wakeupIntent, 0);

      alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()
            + duration, pendingServiceIntent);

      logger.log(LogEntry.SEV_INFO, 8, "setupPhoneHomeTimer for " + duration
            / 1000 + "secs");
   }

   /* Set private mode for N minutes. 0 minutes means turn if off */
   public boolean setPrivateMode(int minutes)
   {
      try
      {
         eventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_PRIVATE_MODE,
               (Object) minutes));
         return true;
      } catch (Exception putException)
      {
         String msg = putException.toString();
         Log.d(getString(R.string.LOG_TAG), msg);
         return false;
      }
   }

   public long getPrivateMode()
   {
      return prefs().getLong(getString(R.string.PRIVATE_MODE_END), -1);
   }

   private void enablePrivateMode(int minutes)
   {
      ServiceState = ServiceState | SERVICESTATE_PRIVATE;
      if (logger != null)
         logger.setPrivate(true);

      if (privateModeAlarm != null)
      {
         privateModeAlarm.clear();
      }

      privateModeAlarm = startTimedEvent(minutes * 60,
            ServiceEvent.EventType.EVENT_PRIVATE_MODE, Integer.toString(0));

      try
      {
         logicalConnectionEventsQ.put(new tcpLogicConnectionEvent(
               tcpLogicConnectionEvent.logicalConnEvent.CONN_OFF));
      } catch (InterruptedException e)
      {
         String errMsg1 = "Failed to send private mode to TCP";
         speakDebug(errMsg1);
         logger.log(LogEntry.SEV_ERROR, 4, errMsg1 + e.toString());
      }

      updateStatusBarInfo();

      /* Store private mode end time in case we reboot/restart */
      Time now = new Time();
      now.setToNow();
      long millis = now.toMillis(false) + (long) (minutes * 60 * 1000);
      SharedPreferences.Editor editor = prefs().edit();
      editor.putLong(getString(R.string.PRIVATE_MODE_END), millis);
      editor.commit();

      if (ibProximityAlert != null)
         ibProximityAlert.Track(false);
   }

   public void disablePrivateMode()
   {
      ServiceState = ServiceState & ~SERVICESTATE_PRIVATE;
      if (logger != null)
         logger.setPrivate(false);
      if (privateModeAlarm != null)
      {
         privateModeAlarm.clear();
         privateModeAlarm = null;
      }

      String ticket = readTicket();

      if (ticket != null)
      {
         try
         {
            logicalConnectionEventsQ.put(new tcpLogicConnectionEvent(
                  tcpLogicConnectionEvent.logicalConnEvent.CONN_ON, ticket));
         } catch (InterruptedException e)
         {
            String errMsg = "Failed to send connection on command, when in private mode";
            speakDebug(errMsg);
            logger.log(LogEntry.SEV_ERROR, 4, errMsg + e.toString());
         }
      } else
      {
         String errMsg = "No ticket. Stuck in private mode";
         speakDebug(errMsg);
         logger.log(LogEntry.SEV_ERROR, 4, errMsg);
      }
      SharedPreferences.Editor editor = prefs().edit();
      editor.putLong(getString(R.string.PRIVATE_MODE_END), 0);
      editor.commit();

      ibProximityAlert.Track(true);
   }

   public static boolean connectivityAvailable(Context cntx)
   {
      NetworkInfo[] networks;
      ConnectivityManager cMgr = (ConnectivityManager) cntx
            .getSystemService(Context.CONNECTIVITY_SERVICE);
      boolean Connectivity = false;

      // NOTE: Tried using EXTRA_NO_CONNECTIVITY, but unfortunately it sets this
      // extra to true while
      // it is switching over from one enabled network to another (like from
      // Wifi back to Mobile)
      // They should have waited until they exhausted all networks 1st, but no.
      // So, look through the list of
      // networks to see who is available.

      networks = cMgr.getAllNetworkInfo();

      if (networks != null && networks.length > 0)
      {
         for (int iNetwork = 0; iNetwork < networks.length && !Connectivity; iNetwork++)
         {
            if (networks[iNetwork].isAvailable())
            {
               Connectivity = true;
            }
         }
      }
      return Connectivity;
   }

   private Bundle createUserNotificationBundle(String prompt,
         String positiveAnswer, String positiveIntentAction,
         String neutralAnswer, String neutralIntentAction,
         String negativeAnswer, String negativeIntentAction, String ack,
         String ibid) throws Exception
   {
      try
      {
         if (prompt == null || positiveAnswer == null)
         {
            String logMsg = "User notification had no prompt or no answer";
            logger.log(LogEntry.SEV_ERROR, 1, logMsg);
            speakDebug(logMsg);
            return null;
         }

         Bundle bundle = new Bundle();

         bundle.putString(UserNotification.Key_Prompt, prompt);

         bundle.putString(UserNotification.Key_Positive_Answer, positiveAnswer);

         if (negativeAnswer != null)
         {
            bundle.putString(UserNotification.Key_Negative_Answer,
                  negativeAnswer);
         }

         if (neutralAnswer != null)
         {
            bundle.putString(UserNotification.Key_Neutral_Answer, neutralAnswer);
         }

         if (positiveIntentAction != null)
         {
            bundle.putString(UserNotification.Key_Positive_Answer_Intent,
                  positiveIntentAction);
         }

         if (neutralIntentAction != null)
         {
            bundle.putString(UserNotification.Key_Neutral_Answer_Intent,
                  neutralIntentAction);
         }

         if (negativeIntentAction != null)
         {
            bundle.putString(UserNotification.Key_Negative_Answer_Intent,
                  negativeIntentAction);
         }

         if (ack != null)
         {
            bundle.putString(UserNotification.Key_ACK, ack);
         } else
         {
            bundle.putString(
                  UserNotification.Key_ACK,
                  getString(R.string.KEY_LOCAL)
                        + Integer.toBinaryString(localNotificationAck++));
         }

         if (ibid != null)
         {
            bundle.putString(UserNotification.Key_IBID, ibid);
         }

         // This is a hack for making a phone call until we get the GUI,
         // Director and wire protocol updated
         prompt = prompt.trim();
         String callOperation = "genie call ";
         int operationStartIndex = prompt.indexOf(callOperation);
         if (operationStartIndex == -1)
         {
            callOperation = "Genie call ";
            operationStartIndex = prompt.indexOf(callOperation);
         }

         if (operationStartIndex != -1)
         {
            int phoneStartIndex;
            int phoneStopIndex;
            String phoneNumber;

            phoneStartIndex = operationStartIndex + callOperation.length();
            phoneStopIndex = prompt.indexOf(" ", phoneStartIndex);

            if (phoneStopIndex == -1)
            {
               phoneStopIndex = prompt.indexOf("\n", phoneStartIndex);
               if (phoneStopIndex == -1)
               {
                  phoneStopIndex = prompt.length();
               }
            }   
               
            phoneNumber = prompt.substring(phoneStartIndex, phoneStopIndex);
            try
            {
               if(phoneStopIndex != prompt.length())
               {
                  prompt = prompt.substring(0, phoneStopIndex)
                     + "\n"
                     + prompt.substring(phoneStopIndex + 1);
                  prompt = prompt.substring(0, operationStartIndex)
                        + "<xwQuiet>"
                        + prompt.substring(operationStartIndex, phoneStopIndex)
                        + "</xwQuiet>\n" + prompt.substring(phoneStopIndex + 1);
               } else {
                  prompt = prompt.substring(0, operationStartIndex)
                        + "<xwQuiet>"
                        + prompt.substring(operationStartIndex, phoneStopIndex)
                        +"</xwQuiet>\n";
               }
               bundle.putString(UserNotification.Key_Prompt, prompt);
            } catch (Exception phoneException) {};
               
            String genieAction = "tel:";
            bundle.putString(UserNotification.Key_Genie_Action, genieAction);

            phoneNumber = phoneNumber.trim();
            phoneNumber = PhoneNumberUtils.stripSeparators(phoneNumber);
            bundle.putString(UserNotification.Key_Intent_Data, "tel:"
                  + phoneNumber);
            bundle.putString(UserNotification.Key_Positive_Answer_Intent,
                  Intent.ACTION_CALL);

            bundle.putString(UserNotification.Key_Positive_Answer, "Call");
            bundle.putString(UserNotification.Key_Negative_Answer, "Cancel");
         }

         return bundle;
      } catch (Exception bundleException)
      {
         logger.log(
               LogEntry.SEV_ERROR,
               1,
               "createUserNotificationBundle exception: "
                     + bundleException.toString());
         throw (bundleException);
      }
   }

   private Bundle createTransparentActivityDialogBundle(String prompt,
         String positiveAnswer, String positiveIntentAction,
         String neutralAnswer, String neutralIntentAction,
         String negativeAnswer, String negativeIntentAction, String ack,
         String ibid)
   {
      try
      {
         Bundle dialogBundle = createUserNotificationBundle(prompt,
               positiveAnswer, positiveIntentAction, neutralAnswer,
               neutralIntentAction, negativeAnswer, negativeIntentAction, ack,
               ibid);

         if (dialogBundle != null)
         {
            dialogBundle.putBoolean(UserNotification.Key_Show_Dialog, true);
         }

         return dialogBundle;
      } catch (Exception createException)
      {
         logger.log(LogEntry.SEV_WARNING, 2, "Could not create dialog bundle "
               + createException);
         return null;
      }
   }

   public void speakDebug(String msg)
   {
      if (debugTalk && !isSpeechSilent())
      {
         speak(msg, 0);
      }
   }

   public void speak(String msg)
   {
      speak(msg, 2000);
   }

   public void speak(String msg, long pauseMsecAfter)
   {
      mouth.speak(msg, pauseMsecAfter);
   }

   public boolean speak(String msg, String id, long pauseMsecAfter)
   {
      mouth.speak(msg, id, pauseMsecAfter);

      return true;
   }

   public boolean speak(String msg, String id)
   {
      mouth.speak(msg, id, 2000);

      return true;
   }

   public void speakThenBeep(String msg, String id)
   {
      speak(msg + " , listening", id, 0);
      /*
       * speak(msg, 0); mouth.beep(); speak(" ", id, 0);
       */
   }

   private void checkPrivateModePreference()
   {
      long privateModeEnd = prefs().getLong(
            getString(R.string.PRIVATE_MODE_END), 0);
      if (privateModeEnd == 0)
      {
         privateModeAlarm = null;
      } else
      {
         Time now = new Time();
         now.setToNow();
         if (privateModeEnd > now.toMillis(false))
         {
            enablePrivateMode((int) ((privateModeEnd - now.toMillis(false)) / 1000 / 60));
         }
      }
   }

   // Tell webview there are messages that need reading/answering
   private void heraldNews()
   {
      try
      {
         Intent webviewIntent = new Intent(getBaseContext(),
               net.inbetween.webview.IbWebview.class);

         long lastTcpConnTime;
         long currentMillis = System.currentTimeMillis();
         if(tcpPortal != null) 
         {
            lastTcpConnTime = tcpPortal.getLastConnectionTime();
         } else {
            lastTcpConnTime = currentMillis;
         }         
         
         if (!State(SERVICESTATE_NEWS_WAITING)  ||
             ((currentMillis - lastTcpConnTime) > 60000))
         {
            ServiceState |= SERVICESTATE_NEWS_WAITING;

            newsNotification = new Notification(
                  net.inbetween.webview.R.drawable.transparent, getString(R.string.DIALOG_MSG_GENIE),
                  System.currentTimeMillis());

            webviewIntent = new Intent(getBaseContext(),
                  net.inbetween.webview.IbWebview.class);
            webviewIntent.setAction("net.inbetween.webview.START_IN_NEWS");

            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                  webviewIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            String noteMsg = null;
            if(activeNotificationBundle != null) noteMsg = activeNotificationBundle.getString(UserNotification.Key_Prompt);
            if(noteMsg == null) noteMsg = "Waiting for your response";
            
            newsNotification.setLatestEventInfo(getApplicationContext(),
                  getString(R.string.DIALOG_MSG_GENIE), noteMsg,
                  contentIntent);

            if (isSpeechSilent())
            {
               newsNotification.defaults = Notification.DEFAULT_ALL;
            } else
            {
               newsNotification.defaults = Notification.DEFAULT_LIGHTS
                     | Notification.DEFAULT_VIBRATE;
            }
            newsNotification.flags |= Notification.FLAG_AUTO_CANCEL
                  | Notification.FLAG_SHOW_LIGHTS;

            NotificationManager notificationMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            notificationMgr.notify(ID_NEWS_NOTIFICATION, newsNotification);
            
            broadcastServiceEvent(ServiceEvent.EventType.EVENT_NEWS);
            
            if(msgReminderAlarm==null && getMaxReminders()>0) {
               startMessageReminder();
            }
            updateStatusBarInfo();
         }
      } catch (Exception notifyException)
      {
         logger.log(LogEntry.SEV_WARNING, 2, "hearldNews exception: "
               + notifyException.toString());
      }
   }

   public boolean newsWaiting()
   {
      return (State(SERVICESTATE_NEWS_WAITING));
   }

   public void newsViewed()
   {
      try
      {
         eventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_NEWS_VIEWED,
               null));
      } catch (Exception queueException)
      {
         String msg = "Unable to send news read event";
         if (logger != null)
            logger.log(LogEntry.SEV_ERROR, 1, msg);
         speakDebug(msg);
      }
   }

   public boolean getSaveBattery()
   {
      return (saveBattery);
   }

   public void setSaveBattery(boolean save)
   {
      try
      {
         eventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_SAVE_BATTERY,
               new Boolean(save)));
      } catch (Exception queueException)
      {
         String msg = "Unable to send save battery event";
         if (logger != null)
            logger.log(LogEntry.SEV_ERROR, 1, msg);
         speakDebug(msg);
      }
   }

   private void readBatterySavingPreference()
   {
      saveBattery = prefs().getBoolean(getString(R.string.KEY_SAVE_BATTERY), false);
   }

   public void storeSaveBattery(boolean inSave)
   {
      saveBattery = inSave;
      SharedPreferences.Editor editor = prefs().edit();
      editor.putBoolean(getString(R.string.KEY_SAVE_BATTERY), saveBattery);
      editor.commit();
   }
   
   public void broadcastServiceEvent(ServiceEvent.EventType eventType)
   {
	   ServiceEvent.broadcastServiceEvent(this, eventType);
   }

   private void launchDialog(Bundle bundle)
   {
      ServiceState |= SERVICESTATE_PROMPT_INUSE;

      TransparentActivityDialog.createDialog(activeNotificationBundle,
            getApplicationContext());
   }

   private void startNotification(Bundle notifyBundle)
   {
      voiceRetryCount = 0;
      logger.log(LogEntry.SEV_INFO, 5, "startNotification entered");

      long currentMillis = System.currentTimeMillis();

      if (notificationStartTime == 0)
      {
         notificationStartTime = currentMillis;
      } else if ((currentMillis - notificationStartTime) > 120000) 
      {	  
         ServiceState &= ~(SERVICESTATE_LISTENING | SERVICESTATE_TALKING | SERVICESTATE_PROMPT_INUSE);
         notificationStartTime = currentMillis;
      }

      if (State(SERVICESTATE_LISTENING) || State(SERVICESTATE_TALKING)
            || State(SERVICESTATE_PROMPT_INUSE) || phoneState.callInProgress()
            || (!silenceSpeech && mouth.isPending()))
      {
         logger.log(LogEntry.SEV_INFO, 5, "notification queued");
         notificationQueue.add(notifyBundle);
         return;
      }

      if (notifyBundle.getBoolean(UserNotification.Key_Show_Dialog, false))
      {
         activeNotificationBundle = notifyBundle;
         launchDialog(notifyBundle);
      }

      if (!isSpeechSilent())
      {
         activeNotificationBundle = notifyBundle;
         ServiceState |= SERVICESTATE_TALKING;
         String spokenWord = "";
         
         if( ! activeNotificationBundle.getBoolean(getString(R.string.KEY_SUMMARIZE_MESSAGES))) {
            spokenWord = getString(R.string.DIALOG_MSG_GENIE) + ". ";
         }
         spokenWord = spokenWord + notifyBundle.getString(UserNotification.Key_Prompt);
         
         spokenWord = spokenWord.replaceAll("<xwQuiet>.*?</xwQuiet>", " ");
         
         if(getVoiceRecognition()==false)
         {
             spokenWord = spokenWord.replaceAll("<xwPrompt>.*?</xwPrompt>", " ");            		
         }
         
         spokenWord = spokenWord.replaceAll("<xwText>", " ");
         spokenWord = spokenWord.replaceAll("</xwText>", " ");
         
         spokenWord = spokenWord.replaceAll("<xwPrompt>", " ");
         spokenWord = spokenWord.replaceAll("</xwPrompt>", " ");
         spokenWord = spokenWord.replaceAll("[Xx][Ww]ish", " X wish");
         
         boolean spoke = false;

         String ackId = notifyBundle.getString(UserNotification.Key_ACK);

         if (notifyBundle.getBoolean(UserNotification.Key_Show_Dialog, false))
         {
            logger.log(LogEntry.SEV_INFO, 5,
                  "StartNotification: speak for dialog");
            speak(spokenWord);
            spoke = true;
            ServiceState &= ~SERVICESTATE_TALKING;
         } else if (notifyBundle
               .getString(UserNotification.Key_Negative_Answer) == null)
         {
            logger.log(LogEntry.SEV_INFO, 5, "Start notification: just speak");
            speak(spokenWord);
            spoke = true;
            returnNoAnswer();
         } else
         {
            logger.log(LogEntry.SEV_INFO, 5,
                  "StartNotification: synthesize file");
            spoke = mouth.synthesizeToFile(spokenWord, ackId);
         }
         if (!spoke)
            returnNoAnswer();
      } else if (notifyBundle.getBoolean(UserNotification.Key_Show_Dialog,
            false))
      {
         updateStatusBarInfo(Notification.DEFAULT_SOUND
               | Notification.DEFAULT_VIBRATE, true);
      } else
      {
         heraldNews();
      }
   }

   private void popNotification()
   {
      logger.log(LogEntry.SEV_INFO, 8, "Entered popNotification");

      if (!State(SERVICESTATE_LISTENING) && !State(SERVICESTATE_TALKING)
            && !State(SERVICESTATE_PROMPT_INUSE)
            && !phoneState.callInProgress() && !mouth.isPending()
            && !notificationQueue.isEmpty())
      {
         logger.log(LogEntry.SEV_INFO, 8, "Poped notification");
         startNotification(notificationQueue.remove());
      }
   }

   private void startVoiceListener()
   {
      logger.log(LogEntry.SEV_INFO, 10, "Enter startVoiceListener");

      try
      {
         if (getVoiceRecognition()  
               && activeNotificationBundle
                     .getString(UserNotification.Key_Negative_Answer) != null
               && !phoneState.callInProgress()
               && SpeechRecognizer.isRecognitionAvailable(getAppContext()))
         {
            logger.log(LogEntry.SEV_INFO, 8, "start voice recognizer");
            ServiceState |= SERVICESTATE_LISTENING;
            voiceRecognizer = new VoiceRecognition(this,
                  activeNotificationBundle);
            loopHandler = new Handler(Looper.getMainLooper());
            if (!loopHandler.post(voiceRecognizer))
            {
               returnNoAnswer();
            }
         } else
         {
            returnNoAnswer();
         }
      } catch (Exception voiceException)
      {
         logger.log(
               LogEntry.SEV_WARNING,
               2,
               "Could not start voice recognition: "
                     + voiceException.toString());
         returnNoAnswer();
      }
   }

   private void startPlayer(String id)
   {
      logger.log(LogEntry.SEV_INFO, 10, "Enter startPlayer");
      try
      {
         mediaPlayer = new MediaPlayer();
         mediaPlayer.reset();
         mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
         mediaPlayer.setOnCompletionListener(this);
         mediaPlayer.setDataSource(Environment.getExternalStorageDirectory()
               .getAbsolutePath() + "/" + id + ".wav");
         mediaPlayer.prepare();
         mediaPlayer.start();
      } catch (Exception mediaException)
      {
         logger.log(LogEntry.SEV_WARNING, 5, "Could not start player "
               + mediaException.toString());
         deleteWavFile(id);
         returnNoAnswer();
      }
   }

   public void startIntent(String action, String data)
   {
      try
      {
         EventStartIntent eventStartIntent = new EventStartIntent();
         eventStartIntent.setAction(action);
         eventStartIntent.setData(data);
         eventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_START_INTENT,
               eventStartIntent));
      } catch (Exception queueException)
      {
         String msg = "Unable to send start action event";
         if (logger != null)
            logger.log(LogEntry.SEV_ERROR, 1, msg);
         speakDebug(msg);
      }
   }

   private void startIntentAction(String action, String data)
   {
      if (action == null)
         action = "";

      if (action.equals("CALL"))
      {
         startCallIntent(data);
      } else
      {
         String logMsg = "Unknown intent action: " + action;
         speakDebug(logMsg);
         logger.log(LogEntry.SEV_ERROR, 1, logMsg);
      }
   }

   private void startCallIntent(String phoneNumber)
   {
      Intent answerIntent = new Intent(Intent.ACTION_CALL);
      answerIntent.setData(Uri.parse("tel:" + phoneNumber));
      answerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      getApplicationContext().startActivity(answerIntent);
   }

   @Override
   public void onCompletion(MediaPlayer inMediaPlayer)
   {
      try
      {
         String id = activeNotificationBundle
               .getString(UserNotification.Key_ACK);
         if (id != null)
            deleteWavFile(id);
         inMediaPlayer.stop();
         this.mediaPlayer = null;
      } catch (Exception mediaException)
      {
         logger.log(
               LogEntry.SEV_WARNING,
               5,
               "Could not stop media player cleanly "
                     + mediaException.toString());
      }

      try
      {
         eventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_SPEECH_OVER,
               null));
      } catch (Exception putException)
      {
         logger.log(LogEntry.SEV_ERROR, 1, "Could not send speech over event. "
               + putException.toString());
         this.stopSelf();
      }
   }

   void deleteWavFile(String id)
   {
      // File dir = getFilesDir();
      File dir = Environment.getExternalStorageDirectory();
      File file = new File(dir, id + ".wav");
      if (!file.delete())
      {
         logger.log(LogEntry.SEV_WARNING, 5, "Could not delete wav file " + id);
      }
   }
   
   void addToMessageSummary(String sender)
   {
      Integer count=1;
      
      if(sender == null)
      {
         sender = getString(R.string.XWISH_GENIE_TAG);
      }
      
      if(msgSenders.containsKey(sender))
      {
         count = msgSenders.get(sender) + 1;
      }
      
      msgSenders.put(sender, count);
      
      if(msgSenders.size()==1)
      {
         // Set alarm
         if (summarizeMsgAlarm != null)
         {
            summarizeMsgAlarm.clear();
         }

         summarizeMsgAlarm = startTimedEvent(20,
               ServiceEvent.EventType.EVENT_SUMMARIZE_MESSAGES, null);
      }
      
      return;
   }
   
   private String summarizeMessages()
   {
      String summaryMsg = "";
      String sender;
      if(msgSenders == null) return summaryMsg;
      
      String[] nameParts;
      int iCount = 0;
      boolean hasGenie = msgSenders.containsKey(getString(R.string.XWISH_GENIE_TAG));

      // Handle one from only genie/you
      if(msgSenders.size()==1)
      {
         if(hasGenie)
         {
            summaryMsg = getString(R.string.DIALOG_MSG_GENIE);
            return summaryMsg;
         } 
      }
      
      for (HashMap.Entry<String, Integer> entry : msgSenders.entrySet())
      {
         iCount+=1;
         if(iCount==1) summaryMsg = "Genie has a message from ";
         
         nameParts = entry.getKey().split(" ");
         sender = nameParts[0]; // Just use first names
         
         if(iCount == msgSenders.size() && msgSenders.size() != 1)
         {
            if(hasGenie)
            {
               if(entry.getKey() == getString(R.string.XWISH_GENIE_TAG)) {
                  summaryMsg = summaryMsg + ", and you.";
               } else {
                  summaryMsg = summaryMsg + sender + ", and you."; 
               }
            } else {
               summaryMsg = summaryMsg + "and " + sender + ".";
            }
         } else {
            if(entry.getKey()!=getString(R.string.XWISH_GENIE_TAG)) {
               summaryMsg = summaryMsg + sender + ", ";
            }
         }
      }
      return summaryMsg;
   }
   
   private void startMessageReminder()
   {
      clearMessageReminder();
      
      reminderCount+=1;
      if(reminderCount > getMaxReminders()) {
         reminderCount = 0;
         return;
      }
      int reminderTime;
      String reminderType = getReminderType();
      
      if(reminderType.equals(getString(R.string.KEY_NONE)))
      {
         return;
      } else if(reminderType.equalsIgnoreCase(getString(R.string.KEY_EXPONENTIAL)))
      {
         int factor =  (int) Math.pow(2, reminderCount-1);
         reminderTime = getReminderFrequency() * factor;
         if(reminderTime>30) reminderTime = 30; // cap at 30mins
      } else if(reminderType.equalsIgnoreCase(getString(R.string.KEY_LINEAR)))
      {
         reminderTime = getReminderFrequency();
      } else {
         return;
      }
      msgReminderAlarm = startTimedEvent(reminderTime*60,
            ServiceEvent.EventType.EVENT_MESSAGE_REMINDER, null);
   }   
   
   private void clearMessageReminder()
   {
      if(msgReminderAlarm!=null) {
         msgReminderAlarm.clear();
         msgReminderAlarm = null;
      }
   }
}
