package net.inbetween.TransparentActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.format.Time;

import net.inbetween.webview.R;
import net.inbetween.receivers.AlarmReceiver;
import net.inbetween.UserNotification;
import net.inbetween.services.WishRunner;

public class TransparentActivityDialog extends Activity implements
   DialogInterface.OnDismissListener
{
   public final static String Key_DialogId = "DialogId";
   public final static int DialogID_Prompt = 1;
   
   private WishRunner mBoundService;
   private boolean mIsBound = false;
   private String resultString = null;
   private AlarmReceiver dialogEndOfLife;
   
   private ServiceConnection mConnection = new ServiceConnection()
   {
      public void onServiceConnected(ComponentName className, IBinder service)
      {
         // This is called when the connection with the service has been
         // established, giving us the service object we can use to
         // interact with the service. Because we have bound to a explicit
         // service that we know is running in our own process, we can
         // cast its IBinder to a concrete class and directly access it.
         mBoundService = ((WishRunner.LocalBinder) service).getService();
      }

      public void onServiceDisconnected(ComponentName className)
      {
         // This is called when the connection with the service has been
         // unexpectedly disconnected -- that is, its process crashed.
         // Because it is running in our same process, we should never
         // see this happen.
         mBoundService = null;
      }
   };

   void doBindService()
   {
      // Establish a connection with the service. We use an explicit
      // class name because we want a specific service implementation that
      // we know will be running in our own process (and thus won't be
      // supporting component replacement by other applications).
      bindService(
         new Intent(TransparentActivityDialog.this, WishRunner.class),
         mConnection, Context.BIND_AUTO_CREATE);
      mIsBound = true;
   }

   void doUnbindService()
   {
      if (mIsBound)
      {
         // Detach our existing connection.
         unbindService(mConnection);
         mIsBound = false;
      }
   }
   
   @Override
   protected void onDestroy()
   {
      if(mBoundService!=null) {
         mBoundService.speakDebug("Dialog Destroyed");
      }
      super.onDestroy();
      doUnbindService();
   }

   protected Dialog onCreateDialog(int dialogId, Bundle dialogBundle)
   {
      Dialog dialog;
      AlertDialog.Builder alertBuilder;
      String writtenPrompt;
      
      switch (dialogBundle.getInt(Key_DialogId))
      {
         case DialogID_Prompt:
         default:
            // create the dialog
            alertBuilder = new AlertDialog.Builder(this);
            String titleText =  "Your Genie";
            Time theTime = new Time();
            
            if(dialogBundle.getLong(UserNotification.Key_Server_Sent_Time, 0) > 0)
            {
               theTime.set(dialogBundle.getLong(UserNotification.Key_Server_Sent_Time));  
            } else {
               theTime.setToNow();
            }
            alertBuilder.setTitle(titleText + "        " + theTime.format("%I:%M%p"));
            alertBuilder.setIcon(R.drawable.icon);
            writtenPrompt = dialogBundle.getString(UserNotification.Key_Prompt);
            writtenPrompt = writtenPrompt.replaceAll("<xwQuiet>", " ").replaceAll("</xwQuiet>", " ");
            
            alertBuilder.setMessage(writtenPrompt);
            alertBuilder.setCancelable(false);
            alertBuilder.setPositiveButton(dialogBundle.getString(UserNotification.Key_Positive_Answer),
               new DialogInterface.OnClickListener()
               {
                  public void onClick(DialogInterface dialog, int which)
                  {
                     resultString = UserNotification.Key_Positive_Answer;
                     if(mBoundService != null) mBoundService.speakDebug("Positive Answer");
                  }
               });                  
            
            if(dialogBundle.getString(UserNotification.Key_Negative_Answer) != null) {
               alertBuilder.setNegativeButton(dialogBundle.getString(UserNotification.Key_Negative_Answer), 
                  new DialogInterface.OnClickListener()
                  {
                     public void onClick(DialogInterface dialog, int which)
                     {
                        resultString = UserNotification.Key_Negative_Answer;
                        if(mBoundService != null) mBoundService.speakDebug("Negative Answer");
                     }
                  });
            }
            
            if (dialogBundle.getString(UserNotification.Key_Neutral_Answer) != null)
            {
               alertBuilder.setNeutralButton(dialogBundle.getString(UserNotification.Key_Neutral_Answer),
                  new DialogInterface.OnClickListener()
                  {
                     public void onClick(DialogInterface dialog, int which)
                     {
                        resultString = UserNotification.Key_Neutral_Answer;
                        if(mBoundService !=  null) mBoundService.speakDebug("Neutral Answer");
                     }
                  });
            }
           
            dialog = alertBuilder.create();
            if(dialog == null && mIsBound && mBoundService != null) {
               mBoundService.speakDebug("Could not create dialog");
            }
      }
      
      dialog.setOnDismissListener(this);
      
      return dialog;
   }
   
   @Override
   public void onCreate(Bundle savedInstanceState)
   {
      super.onCreate(savedInstanceState);

      Bundle dlgBundle = this.getIntent().getExtras();
      
      dialogEndOfLife = new AlarmReceiver(getApplicationContext(), 
         "net.inbetween.dialogEndOfLife", dlgBundle, 45000) 
      {
         @Override public void doWork(Context context, Intent intent)
         {
            clear();
            finish();
         }
      };      
      
      doBindService();
      showDialog(this.getIntent().getExtras().getInt(Key_DialogId), 
            dlgBundle);
      
      // startVoiceRecognitionActivity();
   }

   @Override
   public void onStart()
   {
      if(mBoundService!=null) {
         mBoundService.speakDebug("Dialog Started");
      }
      super.onStart();
   }  
   
   @Override
   public void onResume()
   {
      if(mBoundService!=null) {
         mBoundService.speakDebug("Dialog Resumed");
      }
      super.onResume();
   }   
   
   @Override
   public void onPause()
   {
      if(mBoundService!=null) {
         mBoundService.speakDebug("Dialog paused");
      }
      super.onPause();
   }
   
   @Override
   public void onStop()
   {
      if(mBoundService != null)
      {
         if(resultString != null) {
            mBoundService.returnUserAnswer(resultString);
            mBoundService.speakDebug("Dialog Stopped");
         } else {
            // If the dialog was dismissed without being answered, launch it again.
            // createDialog(this.getIntent().getExtras(), serviceContext);     
            mBoundService.returnNoAnswer();
            mBoundService.speakDebug("Dialog Stopped with no answer");
         }
      }
      if(dialogEndOfLife!=null) {
         dialogEndOfLife.clear();
         dialogEndOfLife = null;
      }
      super.onStop();
   }
   
   @Override
   public void onDismiss(DialogInterface dialogInterface)
   {
      // Get rid of the activity when the dialog is dismissed
      finish();
      if(mBoundService!=null) mBoundService.speakDebug("Dialog Dismissed");
   }

   /*
    * Input Parameters:
    * 
    * dialogBundle - Contains the id of the dialog and the information to
    * display in it context - The calling Context
    * 
    * this function will create a global dialog for you the dialog will appear
    * no matter which activity or screen is showing
    */
   public static void createDialog(Bundle dialogBundle, Context callingContext)
   {
      Intent launchTransparentActivityIntent = new Intent(callingContext,
            TransparentActivityDialog.class);

      launchTransparentActivityIntent.putExtras(dialogBundle);
      launchTransparentActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
      launchTransparentActivityIntent.setAction(Intent.ACTION_VIEW);

      callingContext.startActivity(launchTransparentActivityIntent);
   }
}

