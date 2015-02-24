package net.inbetween.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.log.LogEntry;
import net.inbetween.log.LogProducer;
import net.inbetween.services.ServiceEvent;
import android.content.Context;
import android.media.AudioManager;
import android.os.Environment;
import android.speech.tts.TextToSpeech;

public class Mouth implements TextToSpeech.OnInitListener,
      TextToSpeech.OnUtteranceCompletedListener
{
   private Context context;
   private LogProducer logger;
   private ArrayList<String> initialMsgs;
   private int beepId;
   private String localDir;
   
   private enum State
   {
      ERROR, PENDING, INITIALIZED
   };

   private State state;
   LinkedBlockingQueue<ServiceEvent> serviceQ;

   public Mouth(Context context_in, LogProducer logger_in,
         LinkedBlockingQueue<ServiceEvent> serviceQ_in,
         int beepId_in)
   {
      logger = logger_in;
      state = State.PENDING;
      context = context_in;
      initialMsgs = new ArrayList<String>();
      tts = new TextToSpeech(context, this);
      serviceQ = serviceQ_in;
      beepId = beepId_in;
      localDir = Environment.getExternalStorageDirectory().getAbsolutePath();
      return;
   }

   public boolean isReady()
   {
      return(state==State.INITIALIZED);
   }
   
   private TextToSpeech tts;

   // Implements TextToSpeech.OnInitListener
   public void onInit(int status)
   {
      int result;
      
      // status can be either TextToSpeech.SUCCESS or TextToSpeech.ERROR.
      if (status == TextToSpeech.SUCCESS)
      {
         result = tts.setOnUtteranceCompletedListener(this);
         if (result == TextToSpeech.ERROR)
         {
            // Language data is missing or the language is not supported.
            logger.log(LogEntry.SEV_WARNING, 2, "Mouth onInit can not setup listener.");
            state = State.ERROR;
            mouthIsMute();
         } else
         {
            int rc;
            
            state = State.INITIALIZED;
            for (int iMsg = 0; iMsg < initialMsgs.size(); iMsg++)
            {
               tts.speak(initialMsgs.get(iMsg), TextToSpeech.QUEUE_ADD, null);
            }
            initialMsgs.clear();
            rc = tts.addEarcon("{beep}", "net.inbetween.webview", beepId);
            if(rc==TextToSpeech.ERROR) {
               logger.log(LogEntry.SEV_ERROR, 1, "Could not add earcon");
            }
            mouthIsReady();
         }
      } else
      {
         // Initialization failed.
         state = State.ERROR;
         mouthIsMute();
         logger.log(LogEntry.SEV_WARNING, 2,
               "Could not initialize TextToSpeech.");
      }
   }

   public boolean setOnUtteranceCompletedListener(TextToSpeech.OnUtteranceCompletedListener listener)
   {
      int result;
      result = tts.setOnUtteranceCompletedListener(listener);
      if(result==TextToSpeech.ERROR) {
         tts.setOnUtteranceCompletedListener(this);
         return false;
      } else
      {
         return true;
      }
   }
   
   public void setDefaultUtterenceCompletedListener()
   {
      tts.setOnUtteranceCompletedListener(this);
   }
   
   private void mouthIsReady()
   {
      try
      {
         serviceQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_MOUTH_READY,
               null));
      } catch (Exception putException)
      {
         logger.log(
               LogEntry.SEV_ERROR,
               1,
               "Could not send mouth ready event. "
                     + putException.toString());
      }
   }   
   
   private void mouthIsMute()
   {
      try
      {
         serviceQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_MOUTH_MUTE,
               null));
      } catch (Exception putException)
      {
         logger.log(
               LogEntry.SEV_ERROR,
               1,
               "Could not send utterance complete event. "
                     + putException.toString());
      }
   }

   public boolean isMute() 
   {
      return(state==State.ERROR);
   }

   public boolean isPending() 
   {
      return(state==State.PENDING);
   }
   
   public void speak(String message)
   {
      speak(message, null, 0);
   }

   public void speak(String message, long pauseMsecAfter)
   {
      speak(message, null, pauseMsecAfter);
   }

   public void speak(String message, String id, long pauseMsecAfter)
   {
      if (message == null)
         return;

      if (state == State.PENDING)
      {
         initialMsgs.add(message);
      } else if (state == State.INITIALIZED)
      {
         HashMap<String, String> ttsParams = new HashMap<String, String>();

         ttsParams.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
               String.valueOf(AudioManager.STREAM_NOTIFICATION));
         if (id != null)
         {
            ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id);
         }
         tts.speak(message, TextToSpeech.QUEUE_ADD, ttsParams);

         if(pauseMsecAfter>0)
         {
            ttsParams.clear();
            tts.playSilence(pauseMsecAfter, TextToSpeech.QUEUE_ADD, ttsParams);
         }
      }
   }

   public void close()
   {
      tts.stop();
      tts.shutdown();
      tts = null;
      state = State.PENDING;
   }

   @Override
   public void onUtteranceCompleted(String utteranceId)
   {
      try
      {
         ServiceEvent event = new ServiceEvent(ServiceEvent.EventType.EVENT_SYNTHESIZE_DONE, utteranceId); 
         serviceQ.put(event);
      } catch (Exception putException)
      {
         logger.log(
               LogEntry.SEV_ERROR,
               1,
               "Could not send utterance complete event. "
                     + putException.toString());
      }
   }

   public void playSilence(long silenceMillis)
   {
      tts.playSilence(200, TextToSpeech.QUEUE_ADD, null);
   }
   
   public void beep()
   {
      int rc;
      HashMap<String, String> ttsParams = new HashMap<String, String>();
      
      ttsParams.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
            String.valueOf(AudioManager.STREAM_NOTIFICATION));
      rc = tts.playEarcon("{beep}", TextToSpeech.QUEUE_ADD, ttsParams);
      if(rc==TextToSpeech.ERROR)
      {
         logger.log(LogEntry.SEV_ERROR, 1, "Could not play beep");
      }
   }
   
   public boolean synthesizeToFile(String msg, String id)
   {
      boolean result = false;
      
      HashMap<String, String> ttsParams = new HashMap<String, String>();

      if (id != null)
      {
         ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id);
         if(TextToSpeech.ERROR != tts.synthesizeToFile(msg, ttsParams, localDir + "/" + id + ".wav"))
         {
            result = true;
         }
      } 

      return result;
   }
}