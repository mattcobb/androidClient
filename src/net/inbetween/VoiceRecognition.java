package net.inbetween;

import java.util.ArrayList;
import java.util.HashSet;

import net.inbetween.log.LogEntry;
import net.inbetween.services.WishRunner;
import net.inbetween.webview.R;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

public class VoiceRecognition implements RecognitionListener, Runnable, TextToSpeech.OnUtteranceCompletedListener
{
   private SpeechRecognizer recognizer;
   private WishRunner wishRunner;
   private Bundle notifyBundle;
   private HashSet<String> positiveResponses;
   private HashSet<String> negativeResponses;
   private HashSet<String> skipResponses;
   private Intent intent;
   private String lastResult;
   
   public VoiceRecognition(WishRunner inWishRunner, Bundle inNotifyBundle)
   {
      // TODO Auto-generated constructor stub
      wishRunner = inWishRunner;
      notifyBundle = inNotifyBundle;
      
      positiveResponses = new HashSet<String>();
      positiveResponses.add("okay");
      positiveResponses.add("ok");
      positiveResponses.add("yes");
      positiveResponses.add("send");
      positiveResponses.add("yeah");
      positiveResponses.add("yea");
      positiveResponses.add("allow");
      positiveResponses.add(notifyBundle.getString(UserNotification.Key_Positive_Answer));
      
      negativeResponses = new HashSet<String>();
      negativeResponses.add("cancel");
      negativeResponses.add("private");      
      negativeResponses.add("stop");
      negativeResponses.add("don't");
      negativeResponses.add("no");
      negativeResponses.add(notifyBundle.getString(UserNotification.Key_Negative_Answer));
      
      skipResponses = new HashSet<String>();
      skipResponses.add("skip");
      skipResponses.add("next");
      skipResponses.add("ignore");
      
      lastResult = null;
   }

   @Override
   public void run()
   {
      try {
         wishRunner.logger.log(LogEntry.SEV_INFO, 1, "Entered VoiceRecognition.run()");
         recognizer = SpeechRecognizer.createSpeechRecognizer(wishRunner.getAppContext());
         recognizer.setRecognitionListener((RecognitionListener) this);
         
         intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); 
         intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
         intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, wishRunner.getAppContext().getString(R.string.SERVICE_NAME));
         intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
         intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, new Long(300L));
         intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, new Long(300L));
         startListening();
      } catch (Exception runException) {
         wishRunner.logger.log(LogEntry.SEV_ERROR, 1, "Could not create speech recognizer: " + runException.toString());
         wishRunner.returnNoAnswer();         
      }
   }

   private void startListening() 
   {
      recognizer.startListening(intent);
   }

   @Override
   public void onResults(Bundle results)
   {
      Boolean positiveMatch = false;
      Boolean negativeMatch = false;
      Boolean skipMatch = false;
      ArrayList<String> recognized;
            
      recognized = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
      recognized = scrub(recognized);
      wishRunner.logger.log(LogEntry.SEV_INFO, 10, "Speech matches: " + recognized.toString());
      
      // Match negative responses
      negativeMatch = exactMatch(recognized, negativeResponses);
      if(! negativeMatch)
      {
         // Match positive responses
         positiveMatch = exactMatch(recognized, positiveResponses);
         if(!positiveMatch) {
            skipMatch = exactMatch(recognized, skipResponses);
         }
      }

      if(negativeMatch) {
         notifyBundle.putString(UserNotification.Key_Answer_Value, "Cancel");
         returnAnswer(UserNotification.Key_Negative_Answer);
      } 
      else if(positiveMatch) {
         notifyBundle.putString(UserNotification.Key_Answer_Value, "Okay");
         returnAnswer(UserNotification.Key_Positive_Answer);
      }
      else if(skipMatch) {
         lastResult = "Next";
         returnNoAnswer();
      }
      else {  // retry
         if(lastResult!=null) {
            lastResult = recognized.get(0);
         }
         returnNoAnswer();
      }
    }   

   private ArrayList<String> scrub(ArrayList<String> dirtyMatches) 
   {
      ArrayList<String> matches = dirtyMatches;
      if(matches!=null)
      {
         ArrayList<String> scrubbedMatches = new ArrayList<String>();
         String scrubbedMatch;
         for(String match : matches) 
         {
            scrubbedMatch = match.replaceAll("\\.", "");
            scrubbedMatches.add(scrubbedMatch.toLowerCase());
         }
         matches = scrubbedMatches;
      }      
      return matches;
   }
   
   private boolean exactMatch(ArrayList<String> matches, HashSet<String> responses)
   {
      boolean found = false;
      
      for(String match : matches)
      {
         found = responses.contains(match);
         if(found) 
         {
            return found;
         }
      }
      
      return found;
   }

   private void returnNoAnswer()
   {
      wishRunner.logger.log(LogEntry.SEV_INFO, 1, "Respose not understand");
      // send no answer
      wishRunner.returnNoAnswer(lastResult);     
      recognizer.destroy();
   }

   private void returnAnswer(String answer)
   {
      // send no answer
      wishRunner.returnUserAnswer(answer);     
      recognizer.destroy();
   }   
   
   @Override
   public void onUtteranceCompleted(String utteranceId)
   {
       wishRunner.mouth.setDefaultUtterenceCompletedListener();
       startListening();
   }  
   
   @Override
   public void onError(int error)
   {
      String errMsg;

      switch (error) {
         case SpeechRecognizer.ERROR_AUDIO:
            errMsg = "SpeechRecognizer.ERROR_AUDIO";
            lastResult = "ERROR:Recording error";
         break;
         
         case SpeechRecognizer.ERROR_CLIENT:
            errMsg = "SpeechRecognizer.ERROR_CLIENT";
            lastResult = "ERROR:Voice client error";
         break;

         case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
            errMsg = "SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS";
         break;

         case SpeechRecognizer.ERROR_NETWORK:
            errMsg = "SpeechRecognizer.ERROR_NETWORK";
            lastResult = "ERROR:Network error";
         break;

         case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
            errMsg = "SpeechRecognizer.ERROR_NETWORK_TIMEOUT";
            lastResult = "ERROR:Network timeout";
         break;
         
         case SpeechRecognizer.ERROR_NO_MATCH:
            errMsg = "SpeechRecognizer.ERROR_NO_MATCH";
         break;
         
         case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
            errMsg = "SpeechRecognizer.ERROR_RECOGNIZER_BUSY";
            lastResult = "ERROR:Recognizer service is busy";
         break;
         
         case SpeechRecognizer.ERROR_SERVER:
            errMsg = "SpeechRecognizer.ERROR_SERVER";
            lastResult = "ERROR:Recognizer error";
         break;
         
         case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
            errMsg = "SpeechRecognizer.ERROR_SPEECH_TIMEOUT";
         break;
      
         default: errMsg = "unknown " + Integer.toString(error);
         lastResult = "ERROR:Unknown";
         break;
      }
      
      wishRunner.logger.log(LogEntry.SEV_WARNING, 1, "Speech error "+errMsg);

      returnNoAnswer();
   }
   
   @Override
   public void onReadyForSpeech(Bundle params)
   {
      //wishRunner.logger.log(LogEntry.SEV_INFO, 1, "Entered onReadyForSpeech");
   }
   
   @Override
   public void onBeginningOfSpeech()
   {
   }

   @Override
   public void onBufferReceived(byte[] buffer)
   {
   }

   @Override
   public void onEndOfSpeech()
   {
      //wishRunner.mouth.speak("hmm!", null, 0);
      final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100);
      tg.startTone(ToneGenerator.TONE_DTMF_4, 50);
      tg.startTone(ToneGenerator.TONE_DTMF_A, 150);
   }

   @Override
   public void onEvent(int eventType, Bundle params)
   {
   }

   @Override
   public void onPartialResults(Bundle partialResults)
   {
   }

   @Override
   public void onRmsChanged(float rmsdB)
   {
   }
}
