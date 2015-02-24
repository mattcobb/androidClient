package net.inbetween.webview;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import net.inbetween.log.LogEntry;
import net.inbetween.log.LogProducer;
import net.inbetween.proximity.GeolocationProvider;
import net.inbetween.proximity.LocationHelper;
import net.inbetween.receivers.AlarmReceiver;
import net.inbetween.services.ServiceEvent;
import net.inbetween.services.WishRunner;
import net.inbetween.util.cloudAddress;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.location.Location;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class IbWebview extends Activity
{
   private static final int APP_CACHE_MAX_SIZE = 1024 * 1024 * 16;

   // TODO: these values should be read in from config
   // for now putting them here.
   private static final String LOG_TAG = "IBWEBVIEW";
   
   // TODO: delete this: (it was moved to auto update)
   private static final String downloadFolderName = "XWish";

   private static final String DELETE_VIDEO = "deleteVideo";
   
   // TODO: NOTE: this must be /data/data followed by name of the package
   // else the app will blow up. -Arimed.
   private static final String STORAGE_LOCATION_AREA = Environment.getDataDirectory() +
      "/data/net.inbetween.webview/";

   private BroadcastReceiver serviceReceiver;
   
   String tkt = null;
   private WebView webView = null;
   final Activity activity = this;

   // definitions for server end points here
   private ConnectivityManager connManager;

   protected int tcpPortalServerPort;
   protected String appBuildId = null;

   ProgressDialog loginProgressDialog;
   public static final int progressBarID = 0;
   private ProgressDialog progressBar;
   
   public static final int updateFailedDialogID = 2;
   private Dialog  updateFailedDialog;
   
   public static final int networkFailedDialogID = 3;
   private Dialog  networkFailedDialog;
   
   boolean redirect;
   boolean loadedManifest;
   
   boolean wpSkip = false;
   
   boolean firstLoginDialog = true;
   boolean pageLoaded;
   
   boolean wifiConnected;
   
   boolean inBackground = false;
   boolean updatePending = false;
   
   GeolocationProvider geoProvider;
   cloudAddress cloudAddr;
   
   JSONObject blockedList = new JSONObject();

   private boolean startInNewsPanel;

   public enum speechMode {
      // This does just summarized speech now, but full speech is still there.
      // Waiting for the new gui before creating 4 modes
      JUST_SPEAK, // 0  
      SPEAK_AND_LISTEN, // 1
      SILENT // 2
   }
   
   public void initIbConfig()
   {
      appBuildId = this.getResources().getString(R.string.app_build_id);
      tcpPortalServerPort = Integer.parseInt(this.getResources().getString(
            R.string.tcpPortalServerPort));
   }
   
   public String startUrl(boolean allowStartOfDownload){
	   cloudAddress cloudAddr = new cloudAddress(getApplicationContext());
	   boolean ready = cloudAddr.isWebPackageReady();
	   String homePage = this.getResources().getString(R.string.ibSrcFileName);
	   String loginPage =this.getResources().getString(R.string.loginPage); 
	   String ibAssetsFolderName =  this.getResources().getString(R.string.ibAssetsFolderName);
	   
	   String targetUrl = null;   
       
       if(hasTicket()) {
    	   if (ready){
              targetUrl ="file://" + this.getFilesDir() + "/"  + ibAssetsFolderName + "/" + homePage;
    	   }else{
    		   if (allowStartOfDownload){
	               targetUrl = this.getResources().getString(R.string.updateAnimation);
	              
	               // start the download
	               if(wishRunnerService!=null){   
	                    wishRunnerService.downloadUpdate(true);
	               }else{
	            	     Log.d(LOG_TAG, "getStartUrl: fatal error. unexpected state. no wishRunnerService");
	            	     // try again
	            	     nukeCookies();
	            	     targetUrl = cloudAddr.getWebPortalUrl("/" + loginPage);
	               }
    		   }else{
    			   targetUrl = cloudAddr.getWebPortalUrl("/" + loginPage);
    		   }
    	   }
        } else {
           targetUrl = cloudAddr.getWebPortalUrl("/" + loginPage);
        }
          
	   return targetUrl;
   }
   
   /*
    * Retrieves ticket from settings
    */
   public String getCachedTicket(){   
	   String currentTicket;
	   SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
	   currentTicket = settings.getString(getString(R.string.KEY_TICKET), "");
	   return currentTicket;
   }

   public void setTkt(String newTicket)
   {
      String currentTicket;
      
      SharedPreferences settings = PreferenceManager
            .getDefaultSharedPreferences(this);
      SharedPreferences.Editor editor = settings.edit();
      currentTicket = settings.getString(getString(R.string.KEY_TICKET), "");
      if(currentTicket.length() == 0 || currentTicket.compareTo(newTicket) != 0)
      {
         editor.putString(getString(R.string.KEY_TICKET), newTicket);
         editor.commit();
         tkt = newTicket;
         if(wishRunnerService != null) {
            wishRunnerService.ticketRefreshed();
         }
      }
      CookieSyncManager.getInstance().sync();
   }
   
   public String getTkt()
   {
      //return tkt;
	   SharedPreferences settings = PreferenceManager
       		.getDefaultSharedPreferences(this);
	   
	   return settings.getString(getString(R.string.KEY_TICKET), "");
   }
   
   public void setManifestLoaded(boolean manifestLoaded) {
	      
	   SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
	   SharedPreferences.Editor editor = settings.edit();
	   boolean prevManifestLoaded = settings.getBoolean(getString(R.string.KEY_MANIFEST_LOADED), false);
	   if(prevManifestLoaded != manifestLoaded) {
		   editor.putBoolean(getString(R.string.KEY_MANIFEST_LOADED), manifestLoaded);
		   editor.commit();
	   }
   }
   
   public void broadcastServiceEvent(ServiceEvent.EventType eventType)
   {
	   ServiceEvent.broadcastServiceEvent(this, eventType);
   }
   
   public boolean getManifestLoaded() {
	   SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
	   return settings.getBoolean(getString(R.string.KEY_MANIFEST_LOADED), false);
   }
   
	public void installApkv2(){
		String dFolderName =   this.getString(R.string.downloadFolderName);
	    
 	    Intent intent = new Intent(Intent.ACTION_VIEW);
		File externalStorage = Environment.getExternalStorageDirectory();
		String downloadedFile =  dFolderName+ "/" + this.getString(R.string.clientAPK);
		
 	    intent.setDataAndType(Uri.parse("file://" + externalStorage.getAbsolutePath() + '/' + downloadedFile ), "application/vnd.android.package-archive");
 	    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
 	    startActivity(intent);
	}
   
   @Override
   protected Dialog onCreateDialog(int id) {
	   switch (id) {
       
       case networkFailedDialogID:
    	   networkFailedDialog = new AlertDialog.Builder(this)
            .setTitle("Attention")
            .setMessage(R.string.DIALOG_MSG_FAILED_NETWORK)
            .setNeutralButton(   "OK",
           		             new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
               	 dismissDialog(networkFailedDialogID);
                 // kills webview, kills service and then restarts it
               	 android.os.Process.killProcess(android.os.Process.myPid()); 
                }
            })
            .create();
       	return networkFailedDialog;
       
        case updateFailedDialogID:
        	updateFailedDialog = new AlertDialog.Builder(this)
             .setTitle("Attention")
             .setMessage(R.string.DIALOG_MSG_FAILED_UPDATE)
             .setNeutralButton(   "OK",
            		             new DialogInterface.OnClickListener() {
                 public void onClick(DialogInterface dialog, int whichButton) {
                	 dismissDialog(updateFailedDialogID);
                	 // kills webview, kills service and then restarts it
                	 android.os.Process.killProcess(android.os.Process.myPid()); 
                 }
             })
             .create();
        	return updateFailedDialog;
        	
		case progressBarID:
			progressBar = new ProgressDialog(IbWebview.this);
			progressBar.setMessage("Syncing ...");
			progressBar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressBar.setCancelable(false);
			progressBar.show();
			return progressBar;
		

		default:
			return null;
       }
   }
   
   @Override
   public boolean onKeyDown(int keyCode, KeyEvent event) {
       if ((keyCode == KeyEvent.KEYCODE_BACK) ) {
           // in the future put a java to javascript bridge here
    	   // and make it to back.
    	   // currently we just turn off back button
    	   // so we do not leave the application
    	   
			URL url = null;  
			try {
				url = new URL(webView.getUrl());
				String host = url.getHost();
				
				if(host.contains(getString(R.string.authServerPrefix)) && host.contains(getString(R.string.ibDomain))){
					// this is to handle the case if tgs server returns a 404 (onError method for webview does not catch
					// 4xx and 5xx errors , just network errors.  So this at least lets the user bail outh of webview
					// and try again.
					webView.goBack(); 
				}else if(host.contains(getString(R.string.ibDomain)) || host.equals("")) {
					webView.loadUrl("javascript:exitXwishWebview()");
				} else {
					webView.goBack();
				}
				return true;
			} catch (MalformedURLException e) {
				url = null;
			}
       }
       return super.onKeyDown(keyCode, event);
   }

   /** Called when the activity is first created. */
   @Override
   public void onCreate(Bundle savedInstanceState)
   {
      // for testing
      super.onCreate(savedInstanceState);
      
      checkForStartPage();
      
      // broadcast receiver to listen to service events
      IntentFilter filter = new IntentFilter();
      filter.addAction(this.getResources().getString(R.string.SERVICE_BROADCAST_EVENTS));

      serviceReceiver = new BroadcastReceiver() {
         @Override
         public void onReceive(Context context, Intent intent)
         {
            ServiceEvent.EventType eventType = 
               ServiceEvent.EventType.valueOf(intent.getStringExtra(getString(R.string.KEY_SERVICE_EVENT_TYPE)));
            
            boolean immediate = false;
            switch(eventType) {
               	case EVENT_TICKET:
               	{
               		String currentTicket = getCachedTicket();
               		if(currentTicket==null || currentTicket.length()<=0) {
               			webView.loadUrl("javascript:ibLogoutAndGotoLoginPage()");
               			setTkt("");
               		}
               	}
               	break;
               	case EVENT_CLEINT_WEBPKG_INSTALLED_IMMEDIATE:
               		immediate = true;
               	//no break
               	case EVENT_CLEINT_WEBPKG_INSTALLED:
               		if (inBackground || immediate){
               		   webView.clearCache(false);
               		   webView.loadUrl(startUrl(false));
                 		updatePending = false;
               		}else{
               			updatePending = true;
               		}
                  break;
               	case EVENT_USER_LOGGED_IN:
               		webView.loadUrl(startUrl(true));
              	   break;
               	
               	case EVENT_WIFI_CONNECTED:
               		wifiConnected = true;
               		if(webView.getUrl().contains(getString(R.string.ibSrcFileName)) && pageLoaded) {
               			webView.loadUrl("javascript:ibBrowserStateCtx.isWifiConnected(true)");
               		}
               		break;
               	case EVENT_WIFI_DISCONNECTED:
               		wifiConnected = false;
               		if(webView.getUrl().contains(getString(R.string.ibSrcFileName)) && pageLoaded) {
               			webView.loadUrl("javascript:ibBrowserStateCtx.isWifiConnected(false)");
               		}
               		break;
               	
               	case EVENT_CLIENT_UP_TO_DATE:
               		webView.loadUrl("javascript:phoneBridgeClientUpToDate()");
               		break;
               	case EVENT_CLIENT_UPDATING:
               		webView.loadUrl("javascript:phoneBridgePleaseWait()");
                    break;
               	case EVENT_CLIENT_UPDATE_FAILED:
               		showDialog(updateFailedDialogID);
               		break;
               	case EVENT_CLIENT_INSTALL_APK:
               		installApkv2();
               		break;
               	case EVENT_CLIENT_LOCATION_SUCCESS: {
               		//Location currentLoc = wishRunnerService.ibProximityAlert.getLastLocation();
               		Location currentLoc = geoProvider.getLocation();
               		if(currentLoc == null) {
               			webView.loadUrl("javascript:android_geolocation.bridgeReportingError(2)");
               		} else {
	               		String newLocObjectString = LocationHelper.locationToJSONString(currentLoc);
	               		webView.loadUrl("javascript:android_geolocation.bridgeReportingLocation(" + newLocObjectString + ")");
               		}
               		break;
               	}
               	case EVENT_CLIENT_LOCATION_FAIL: {
               		webView.loadUrl("javascript:android_geolocation.bridgeReportingError(1)");
               		break;
               	}
               	case EVENT_NEWS: {
               	   if(inBackground) {
               	      webView.loadUrl("javascript:jumpToNewsNow()");
               	   } else {
               	      webView.loadUrl("javascript:queryBeforeJumpToNews()");
               	   }
               	   break;
               	}
               	default:
               		break;
            }
         }
      };
      registerReceiver(serviceReceiver, filter, "net.inbetween.permission.SEND_SERVICE_BROADCAST_EVENTS", null);

      cloudAddr = new cloudAddress( getApplicationContext());

      initIbConfig();
      loadedManifest = false;
      pageLoaded = false;
      
      CookieSyncManager.createInstance(IbWebview.this);
      
      this.setVolumeControlStream(AudioManager.STREAM_NOTIFICATION); //makes volume buttons adjust media volume
      
      requestWindowFeature(Window.FEATURE_NO_TITLE);
      webView = new WebView(this);
      
      webView.getSettings().setJavaScriptEnabled(true);
      webView.getSettings().setPluginsEnabled(true);
      
      connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE); //Used to tell if connected on wifi
      NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
      wifiConnected = mWifi.isConnected();

      JavaScriptInterop jsObject = new JavaScriptInterop();
      webView.addJavascriptInterface(jsObject, "ob");

      webView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);

      webView.setWebChromeClient(new WebChromeClient()
      {
    	   public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback)
    	   {
    		   callback.invoke(origin, true, false);
    	   }

         @Override
         public void onReachedMaxAppCacheSize(long spaceNeeded,
               long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater)
         {
            quotaUpdater.updateQuota(spaceNeeded * 2);
         }

         @Override
         public void onExceededDatabaseQuota(String url,
               String databaseIdentifier, long currentQuota,
               long estimatedSize, long totalUsedQuota,
               WebStorage.QuotaUpdater quotaUpdater)
         {
            quotaUpdater.updateQuota(estimatedSize * 2);
         }
      });

      // for geolocation purposes
      webView.getSettings().setGeolocationDatabasePath(STORAGE_LOCATION_AREA + "location");
      
      webView.getSettings().setDomStorageEnabled(true);

      webView.getSettings().setAppCacheMaxSize(APP_CACHE_MAX_SIZE);

      webView.getSettings().setAppCachePath( STORAGE_LOCATION_AREA + "cache");
      webView.getSettings().setAllowFileAccess(true);
      webView.getSettings().setAppCacheEnabled(true);
      webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
      //webView.getSettings().setCacheMode(WebSettings. LOAD_NO_CACHE);
      
      webView.getSettings().setDatabaseEnabled(true);
      webView.getSettings().setDomStorageEnabled(true);
      webView.getSettings().setDatabasePath(STORAGE_LOCATION_AREA + "database");
   
      webView.getSettings().setLightTouchEnabled(true);

      // TODO:  Put this in for now because Android 4.0.4 crashes
      // retrieving these from sqllite
      webView.getSettings().setSaveFormData(false);
      webView.getSettings().setSavePassword(false);
      
      
      webView.setWebViewClient(new WebViewClient()
      {
    	  class RemoveSpinnerReciever extends AlarmReceiver {
        	  public RemoveSpinnerReciever() {
        		  super(getApplicationContext(), "net.inbetween.removeLoadingPageSpinner", new Bundle(), 1000);
        	  }
        	  
        	  public void doWork(Context context, Intent intent) {
        		  if(loginProgressDialog!=null && loginProgressDialog.isShowing()) {
    	    		  loginProgressDialog.dismiss();
    	              loginProgressDialog = null;
    	              Log.d(LOG_TAG, "Timer removed spinner dialog");
    	              
    	              //TODO: In the future add a squealer here
        		  }
        	  }
          }
          RemoveSpinnerReciever removeSpinnerTimer = null;
    	  
    	 @Override
         public void onReceivedError(WebView view, int errorCode,
               String description, String failingUrl)
         {
    		// work around for geo error that causes this *(sencha)
    		if (errorCode == ERROR_UNSUPPORTED_SCHEME){
    			view.loadUrl(startUrl(true));
    			return;
    		}
    		 
    		String errMsg = "onReceivedError:  errorCode: " + errorCode + " description: " + description + "url:" + failingUrl;
    	    Log.d(LOG_TAG, errMsg ); 
    	    
            if(wishRunnerService!=null)
            {
               wishRunnerService.logger.log(LogEntry.SEV_ERROR, 1, errMsg);
            }
    	    
        	view.loadUrl( getString(R.string.errorPage));
            if(loginProgressDialog != null && loginProgressDialog.isShowing()) loginProgressDialog.dismiss();
            
            showDialog(networkFailedDialogID);
            return;
         }
         
         public void onLoadResource (WebView view, String url)
         {
           	//Added navigation here because shouldOverrideUrlLoading doesn't always get called
    		if(isBlockedUrl(url)) {
   				view.goBack();
           	}
            return;
         }
         
         // TODO: The version of the app that is going to be on Android marketplace, must not
         // have this code!
         @Override
         public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {     	 
	         if ((cloudAddr != null ) && (cloudAddr.isNonProduction() == true)){
	            handler.proceed();
	         }else{
	            handler.cancel();
	         }
         }

         @Override
         public boolean shouldOverrideUrlLoading(WebView view, String url)
         {
        	if(isBlockedUrl(url)) {
        		return true;
        	}
			
        	/*
        	view.loadUrl(url);
            return true;
            */
        	return false;
         }
         
         @Override
         public void onPageStarted(WebView view, String url, Bitmap favicon)
         {
        	if(removeSpinnerTimer != null) removeSpinnerTimer.clear();
        	 
            String urlParts[];
            
            super.onPageStarted(view, url, favicon);
            urlParts = url.split("\\?");
            
            // test/dev uses prod auth, so adding this check
            String authDomain = "", authDomainProd = "" ;
            
      	    if (cloudAddr != null ){
     	          authDomain = cloudAddr.getAuthDomain(false);
     	          authDomainProd = cloudAddr.getAuthDomain(true);
     	       }
      
            if(loginProgressDialog == null && urlParts!=null && urlParts.length>0 &&
                 (urlParts[0].toLowerCase().contains(authDomain)  || urlParts[0].toLowerCase().contains(authDomainProd)) )
            {
               loginProgressDialog = new ProgressDialog(IbWebview.this);
               if(loginProgressDialog!=null)
               {
                  if(firstLoginDialog) {
                     loginProgressDialog.setMessage("Contacting Facebook...");
                  } else {
                     loginProgressDialog.setMessage("Logging into xWish...");
                  }
                  loginProgressDialog.show();
               }
            }
            
            pageLoaded = false;
         }
         
         public void onPageFinished(WebView view, String url)
         {
            String urlParts[] = url.split("\\?");
            
            if(removeSpinnerTimer != null) removeSpinnerTimer.clear();
            
            if(loginProgressDialog!=null && loginProgressDialog.isShowing()) {
               String urlToCheck = urlParts[0].toLowerCase();
               if(urlToCheck.contains("facebook.com/login") || 
            		   urlToCheck.contains("animation.html") || 
            		   urlToCheck.contains("ib2.html")) {
	               loginProgressDialog.dismiss();
	               loginProgressDialog = null;
	               Log.d(LOG_TAG, "ib2 or facebook page loaded");
               } else {
            	   removeSpinnerTimer = new RemoveSpinnerReciever();
               }
            }
            
            pageLoaded = true;
         }
      });

      // clearing the cache in case there was an update while webview was down
      webView.clearCache(false);
      webView.loadUrl(startUrl(true));
      setContentView(webView);
      
      // keyboard control for legacy devices vs. ice cream sandwitch and later
      // took out android:windowSoftInputMode="adjustPan"  from androidManifest.xml
      if (android.os.Build.VERSION.RELEASE.startsWith("4.")){
    	  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
      }else{
    	  getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
      }
      
      LogProducer logger = null;
      if(wishRunnerService != null) {
    	  logger = wishRunnerService.logger;
      }
      geoProvider = new GeolocationProvider(this, logger);
      
      Thread updateBlockListThread = new Thread(
          new Runnable() {
        	  public void run() {
        		  updateBlockList();
        	  }
          }
      );
      updateBlockListThread.start();
   }

   @Override 
   public void onStart()
   {
      super.onStart();
      Log.d(LOG_TAG, "OnStart called");
   }
   
   public void onNewIntent(Intent newIntent) {
      Log.d(LOG_TAG, "Got new intent");
      
      checkForStartPage(newIntent);
      if(startInNewsPanel) {
         startInNewsPanel=false;
         webView.loadUrl("javascript:jumpToNewsList()");
         if(wishRunnerService!=null) {
            wishRunnerService.newsViewed();
         }
      }
   }
   	
   	public void updateBlockList() {
   		if(cloudAddr == null) cloudAddr = new cloudAddress(getApplicationContext());
	   	String url = cloudAddr.getTgsUrl(getString(R.string.blockedListPath));
 		HttpClient httpclient = new DefaultHttpClient();
 		HttpGet httpGet = new HttpGet(url);
 		
 		try {
 			HttpResponse response = httpclient.execute(httpGet);
 			HttpEntity entity = response.getEntity();

 			if (entity != null) {
 				InputStream inStream = entity.getContent();
 				BufferedReader reader = new BufferedReader(new InputStreamReader(inStream));
 				String responseString = "";
 				
 			    String line = reader.readLine();
 		    	while(line != null) {
 		    		responseString += line + "\n";
 		            line = reader.readLine();
 		        }
 		    	
 		    	inStream.close();
 				
 				JSONObject newBlockedList = new JSONObject(responseString);
 				
 				blockedList = newBlockedList;
 			}
	    } catch (Exception e) {
	    	//TODO: Log this
	    }
 	}
   
	private boolean isBlockedUrl(String url) {
		try {
			URL parsedURL = new URL(url);
			String host = parsedURL.getHost();
			int lastPeriod = host.lastIndexOf('.');
			int secondToLastPeriod = host.lastIndexOf('.', lastPeriod - 1);
			if(secondToLastPeriod > 0) {
				host = host.substring(secondToLastPeriod + 1);
			}
			
			JSONObject blockedPaths = blockedList.optJSONObject(host);
			String path = parsedURL.getPath();
			if(blockedPaths != null && blockedPaths.opt(path) != null) {
				return true;
			}
			
		} catch (Exception e) { }
		
		return false;
	}
   
   private void checkForStartPage() {
      checkForStartPage(getIntent());
   }
   
   private void checkForStartPage(Intent startIntent)
   {
      startInNewsPanel = false;
      if(startIntent==null) startIntent = getIntent();

      if(startIntent == null) return;
      
      String actionString = startIntent.getAction();
      
      if(actionString != null) {
         if(actionString.equals("net.inbetween.webview.START_IN_NEWS"))
         {
            startInNewsPanel = true;
         }

         // This comes from Google Maps, Send my location action
         if(actionString.equals("android.intent.action.SEND"))
         {
            String text = startIntent.getStringExtra("android.intent.extra.TEXT");
            String logMsg = "Location sent from google maps: " + text;
            if(wishRunnerService!=null)
            {
               wishRunnerService.logger.log(LogEntry.SEV_INFO, 1, logMsg);
            }
            // TODO: Start in Places map pre-populated to this location or ask them who they want to send the location to, etc.  
         }
      }
   }
   
   public void onResume()
   {
      super.onResume();
      inBackground = false;
      
      startOurServices();
      
      CookieSyncManager.getInstance().startSync();
      
      doBindService();
       
      if(pageLoaded && webView.getUrl().contains(getString(R.string.ibSrcFileName))) {
    	  webView.loadUrl("javascript:onResume()");
      }
   }
   
   public void onPause()
   {
	   super.onPause();
	   inBackground =  true;
	   
	   if(pageLoaded && webView.getUrl().contains(getString(R.string.ibSrcFileName))) {
		   webView.loadUrl("javascript:onPause()");
	   }
	   
	   if (updatePending){
		   webView.clearCache(false);
		   updatePending = false;  
		   broadcastServiceEvent(ServiceEvent.EventType.EVENT_USER_LOGGED_IN); // want to reload
	   }	
 		
	   
      CookieSyncManager.getInstance().sync();
	   CookieSyncManager.getInstance().stopSync();
   }
   
   public void onStop()
   {
      super.onStop();
      if(wishRunnerService!=null) {
         wishRunnerService.stopIfLoggedOut();
      }
   }
   
   class progressTracking{
		private int totalDownloaded;
		private int packageSize;
		
		public progressTracking(){
			totalDownloaded = 0;
			packageSize =0;
		}
		
		public void setPackageSize(int size){
			if (packageSize  == 0){
				packageSize = size;
			}
		}
		public String getProgress(int chunk){
			totalDownloaded += chunk;
			return (""+(int)((totalDownloaded*100)/packageSize));
		}
   }
   	
	private class DownloadVideoAsync extends AsyncTask<String, String, String> {
	   
		boolean titleSet = false;
		public static final long DELETE_VIDEO_TIME = 3000;
		
		private void launchAndDeleteVideo(final String downloadFileName) {
			//Start the video
			Intent startVideoIntent = new Intent(Intent.ACTION_VIEW);
			startVideoIntent.setDataAndType(Uri.parse(downloadFileName), "video/*");
	        startActivity(startVideoIntent);
	        
	        //Delete the video a while after it's started
	        Intent wakeupIntent = new Intent(DELETE_VIDEO);
	        PendingIntent pendingVideoIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, wakeupIntent, 0);
	        
	        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
	        alarmManager.set(AlarmManager.RTC_WAKEUP,  System.currentTimeMillis() + DELETE_VIDEO_TIME, pendingVideoIntent);
	    	
	        registerReceiver(
				new BroadcastReceiver() {
					public void onReceive(Context context, Intent intent) { 
						//Uri videoURI = intent.getData();
						
						File videoFile = new File(downloadFileName);
						videoFile.delete();
					}
				}, 
				new IntentFilter(DELETE_VIDEO),
				"net.inbetween.permission.SEND_SERVICE_BROADCAST_EVENTS",
				null
			);
		}
		
		@Override
		protected String doInBackground(String... aurl) {
			
			if(aurl[0] != null) {
				String videoLink = aurl[0];
				String downloadFilePath = downloadFile(videoLink);
				if (downloadFilePath != null) {
					launchAndDeleteVideo(downloadFilePath);
				}
			}
			
			return null;
		}
		
		protected String downloadFile(String targetFile) {
			String downloadFilePath = null;
			
			try {
			    if (targetFile == null) {
			    	Log.d(LOG_TAG, "No target url passed in to download " + targetFile);
			    	return null;
			    }
				
			    String fileName = targetFile.substring( targetFile.lastIndexOf('/')+1, targetFile.length());
			    
			    if ((fileName == null) || (fileName.length() == 0)) {
			    	Log.d(LOG_TAG, "Failed to get file name from " + targetFile);
			    	return null;
			    }
			    
			    String currentTicket = getCachedTicket();
			    String downloadedFile =  downloadFolderName + "/" + fileName;
			    
				URL url = new URL(cloudAddr.getVideoPortalUrl(targetFile));
				URLConnection conn = url.openConnection();
				
				if ((currentTicket != null) && (currentTicket.length() > 0)){                         
					conn.addRequestProperty("stkt", currentTicket);                  
				}
				
				conn.connect();

				int lenghtOfFile = conn.getContentLength();
				File externalStorage = Environment.getExternalStorageDirectory();
				String ApkPath = externalStorage.getAbsolutePath();
				
				// make sure the folder exists
				File folder = new File(ApkPath, downloadFolderName);
				if(!folder.exists()) {
					boolean success = folder.mkdir();
					if(!success) {
						Log.d(LOG_TAG, "Failed to create folder " + ApkPath + '/' + downloadFolderName);
						return null;
					}
				}
				
				InputStream input = conn.getInputStream();			
				OutputStream output = new FileOutputStream(ApkPath + '/' + downloadedFile);
		
				byte data[] = new byte[1024 * 4];
				
				progressTracking trackBar = new progressTracking();
				trackBar.setPackageSize(lenghtOfFile);
				
				int count;
				while ((count = input.read(data)) != -1) {
					
					publishProgress(trackBar.getProgress(count));
					output.write(data, 0, count);
				}
	
				output.flush();
				output.close();
				input.close();
		  	    
				downloadFilePath = ApkPath + '/' + downloadedFile;
			} catch (Exception e) {
				Log.d(LOG_TAG, "Got an exception while attempting to download " + e.toString());
				return null;
			}
			
		    return downloadFilePath;
		}
		
		protected void onProgressUpdate(String... progress) {
			
		     if (titleSet == false){
		    	titleSet = true;
		    	showDialog(progressBarID);
				progressBar.setMessage("Downloading your video...");
		     }
			 progressBar.setProgress(Integer.parseInt(progress[0]));
		}

		@Override
		protected void onPostExecute(String unused) {
			dismissDialog(progressBarID);
		}
	}

   private WishRunner wishRunnerService;
   
   private ServiceConnection wishRunnerConnection = new ServiceConnection()
   {
      public void onServiceConnected(ComponentName className, IBinder service)
      {
         // This is called when the connection with the service has been
         // established, giving us the service object we can use to
         // interact with the service. Because we have bound to an explicit
         // service that we know is running in our own process, we can
         // cast its IBinder to a concrete class and directly access it.
         wishRunnerService = ((WishRunner.LocalBinder) service).getService();
      }

      public void onServiceDisconnected(ComponentName className)
      {
         // This is called when the connection with the service has been
         // unexpectedly disconnected -- that is, its process crashed.
         // Because it is running in our same process, we should never
         // see this happen.
         wishRunnerService = null;
      }
   };

   void doBindService()
   {
      boolean bound = false;
      // Establish a connection with the service. We use an explicit
      // class name because we want a specific service implementation that
      // we know will be running in our own process (and thus won't be
      // supporting component replacement by other applications).
      while(bound == false)
      {
         bound = bindService( new Intent(getApplicationContext(), WishRunner.class),
            wishRunnerConnection, Context.BIND_AUTO_CREATE);
      }
   }

   void doUnbindService()
   {
      if (wishRunnerService != null)
      {
         // Detach our existing connection.
         unbindService(wishRunnerConnection);
         wishRunnerService = null;
      }
   }

   @Override
   protected void onDestroy()
   {
      super.onDestroy();
      doUnbindService();
      unregisterReceiver(serviceReceiver);
   } 
   
   /**
    * 
    * needed to handle redirects and other navigation. This function prevents
    * control being handed over to the browser. Useful for controlling data flow
    * from java to html5 (in future revision of this class)
    */
   public class URLIntercepter extends WebViewClient
   {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url)
      {
         view.loadUrl(url);
         return false;
      }
   }

   private void startOurServices()
   {
      WishRunner.startSelf(this);
   }
   
   private void nukeCookies() 
   {
      CookieManager cookieManager = CookieManager.getInstance();
      cookieManager.removeAllCookie();
      CookieSyncManager.getInstance().sync();
      setTkt("");
   }
 
   public boolean hasTicket() {
 	 String tkt = getTkt();
      
      if ((tkt != null) && tkt.length()>0){
     	 return true;
      }else{
     	 return false;
      }  
   }
   
   /* Bridge between JavaScript and Java */
   class JavaScriptInterop
   {
	
      public void finish()
      {
    	  IbWebview.this.finish();
      }
      
      public void removeSpinner(){
          if(loginProgressDialog!=null && loginProgressDialog.isShowing())
               { 
                  loginProgressDialog.dismiss();
                  loginProgressDialog = null;
                  Log.d(LOG_TAG, "ib2 or facebook page loaded");
               }
      }
      
      public String getEnvSuffix()
      {
    	  if (cloudAddr != null ){
    	     return  cloudAddr.getEnvSuffix();
    	  }else{
    		  return null;
    	  }
      }

      public void preLogin(){
    	  broadcastServiceEvent(ServiceEvent.EventType.EVENT_USER_LOGGED_IN);
      }
      
        	  
      public void nukeState()
      {
    	 nukeData();
         nukeCookies();
         if(wishRunnerService!=null) wishRunnerService.newsViewed();
      }
      
      public void nukeData()
      {
         webView.clearCache(true);
         IbWebview.this.deleteDatabase("webview.db");
         IbWebview.this.deleteDatabase("webviewCache.db");
      }
      
      public void nukeCookies()
      {
         CookieManager cookieManager = CookieManager.getInstance();
         cookieManager.removeAllCookie();
         CookieSyncManager.getInstance().sync();
         setTkt("");
      }
      
      public boolean webPackageCheckSkip(boolean newVal)
      {
    	 boolean origVal = wpSkip;
    	 wpSkip = newVal;
    	 return origVal;	 
      }
     
      // pass appBuildId back to page in webview
      public String getAppBuildId1()
      {
    	  return appBuildId ;
      }
      
      public void playVideo(String videoURL) {
    	  DownloadVideoAsync videoDownload = new DownloadVideoAsync();
    	  videoDownload.execute(videoURL);
      }
      
      // new native java support for checking for updates (instead of via javascript)
      public void checkForApkUpdate() {
          if(wishRunnerService!=null){   
            wishRunnerService.checkForUpdate(true, true, null);
          }
      }
      
      public void checkForWebUpdate(String versionType) {
          if(wishRunnerService!=null){   
            wishRunnerService.checkForUpdate(true, false, versionType);
          }
      }
  

      public void nukeCookiesByDomain(String domain)
      {
	   	  if(domain == null) return;
	   	  
	   	  CookieManager cookieManager = CookieManager.getInstance();
	   	  String cookie = cookieManager.getCookie(domain);
	   	  while(cookie != null)
	   	  {
	   	     cookieManager.setCookie(domain, cookie); //sets it in this session so we can remove it
	   	     cookieManager.removeSessionCookie();
	   	     cookie = cookieManager.getCookie(domain);
	   	  }
	   	  CookieSyncManager.getInstance().sync();
      }	
      
      public void nukeCookie(String name, String domain)
      {
         /*
          * TODO: Look for the name in the cookie from the domain and set it
         */
      }
      
      
      public void importTkt(String tkt)
      {
         // Log.d(LOG_TAG, tkt);
         setTkt(tkt);
      }
      
      // deprecated
      public void loadUrl(String url)
      {
    	  webView.loadUrl(url);
      }
      
      public void clearCache(boolean state)
      {
          webView.clearCache(state);
      }
      
      public String exportTkt()
      {
    	  return getTkt();
      }
      
      // Turn on the private mode for N minutes.
      // If minutes is zero, stop private mode.
      public boolean setPrivateMode(int minutes)
      {
         boolean result = false;
         if(wishRunnerService != null) {
            result = wishRunnerService.setPrivateMode(minutes);
         }
         return result;
      }
      
      public long getPrivateMode() {
    	  if(wishRunnerService == null) return -1;
    	  
    	  long tmp = wishRunnerService.getPrivateMode();
    	  return tmp;
      }

      public boolean setSpeechMode(int speechMode)
      {
         boolean result = false;
         if(wishRunnerService != null) {
           	result = true;
           	if(speechMode == IbWebview.speechMode.JUST_SPEAK.ordinal()) {
           		wishRunnerService.setSilence(false);
           		wishRunnerService.setVoiceRecognition(false);
           		wishRunnerService.setSummarizeMessages(true);
           	} else if(speechMode == IbWebview.speechMode.SPEAK_AND_LISTEN.ordinal()) {
           		wishRunnerService.setSilence(false);
           		wishRunnerService.setVoiceRecognition(true);
           		wishRunnerService.setSummarizeMessages(false);
           	} else if(speechMode == IbWebview.speechMode.SILENT.ordinal()) {
           		wishRunnerService.setSilence(true);
           		wishRunnerService.setVoiceRecognition(false);
           		wishRunnerService.setSummarizeMessages(false);
           	} else {
           		result = false;
           	}
         }
         return result;
      }
      
      public int getSpeechMode() {
         if(wishRunnerService.getVoiceRecognition()) {
        	 return IbWebview.speechMode.SPEAK_AND_LISTEN.ordinal();
         } else if(!wishRunnerService.getSilence()) {
        	 return IbWebview.speechMode.JUST_SPEAK.ordinal();
         } else {
        	 return IbWebview.speechMode.SILENT.ordinal();
         }
      }

      public boolean hasTktCookie(){
    	      return(hasTicket());
      }
      
      public void notifyManifestIsLoaded() {
    	  setManifestLoaded(true);
      }
      
      public void loadLocalFiles() { 
    	  broadcastServiceEvent(ServiceEvent.EventType.EVENT_USER_LOGGED_IN);
      }
      
      public void hideKeyBoard()
      {
    	  InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
    	  try {
    	     imm.hideSoftInputFromWindow(IbWebview.this.getWindow().getCurrentFocus().getWindowToken(), 0);
    	  } catch (Exception hideException) {
    	     Log.d(LOG_TAG, "hideSoftInputFromWindow failed. " + hideException.toString());
    	  }
      }
      
      public void servicePhoneHome() 
      {
         if(wishRunnerService!=null) {
            wishRunnerService.phoneHomeNow();
         }
      }
      
      // Start webview in the News panel
      public boolean startInNews()
      {
         boolean result = false;
         
         if(startInNewsPanel)
            //(wishRunnerService!=null && wishRunnerService.newsWaiting()))
         {
            result = true;
            startInNewsPanel = false;
            if(wishRunnerService!=null) wishRunnerService.newsViewed();
         }
         return result;
      }
      
      public void newsViewed() {
         if(wishRunnerService!=null) wishRunnerService.newsViewed();
      }
      
      public void batterySavingMode(boolean save) {
         if(wishRunnerService != null) wishRunnerService.setSaveBattery(save);
      }
      
      public boolean getBatterySavingMode() {
         if(wishRunnerService == null) return false;
         return wishRunnerService.getSaveBattery();
      }
      
      public void startAction(String action, String data) {
         if(wishRunnerService != null) wishRunnerService.startIntent(action, data);
      }
      
      public void toggleDebug() {
    	  if(wishRunnerService != null) wishRunnerService.toggleDebug();
      }
      
      public boolean debugActivated() {
         boolean result = false;
         if(wishRunnerService != null) {
    	      result = wishRunnerService.getDebugMode();
         }
         return result;
      }
      
      public void setDebugTalk(boolean newDebugTalk) {
    	  wishRunnerService.setDebugTalk(newDebugTalk);
      }
      
      public boolean debugTalkActivated() {
    	  return wishRunnerService.getDebugTalk();
      }
      
      public void changeEnvironment(String newEnvironmentName) {
    	  
    	  cloudAddress cloudAddr = new cloudAddress(getApplicationContext());
    	   
    	  //write the config file
    	  boolean configWriteSuccessful = cloudAddr.writeClientConfig(newEnvironmentName, true);
    	  if(!configWriteSuccessful) {
    		  return;
    	  }
    	  
    	  // kill webview and restart service
    	  android.os.Process.killProcess(android.os.Process.myPid()); 
      }
      
      public boolean sendAnalytics(String analyticsToSend) {
    	  return wishRunnerService.analytics.sendWV(analyticsToSend);
      }
      
      public boolean isConnectedToWifi() {
    	  return wifiConnected;
      }
      
      public boolean getCurrentLoc() {
    	  if(geoProvider == null) return false;
    	  broadcastServiceEvent(ServiceEvent.EventType.EVENT_CLIENT_LOCATION_SUCCESS);
    	  return true;
      }
      
      public boolean startGeoProvider() {
    	  if(geoProvider == null) return false;
    	  geoProvider.start();
    	  return true;
      }
      
      public boolean stopGeoProvider() {
    	  if(geoProvider == null) return false;
    	  geoProvider.stop();
    	  return true;
      }
      
      // Reminder config = { version: 1, type: none|linear|exponential }
      public boolean setReminderConfig(String reminderConf) 
      {
         JSONObject reminderConfig;
         int version;
         String reminderType;

         if(wishRunnerService == null) return(false);
         
         try {
            reminderConfig = new JSONObject(reminderConf);

            version = reminderConfig.getInt("version");
            if(version!=1) return false;
            
            reminderType = reminderConfig.getString("type");
            
            if(reminderType.equalsIgnoreCase(getString(R.string.KEY_LINEAR)) ||
               reminderType.equalsIgnoreCase(getString(R.string.KEY_EXPONENTIAL)) ||
               reminderType.equalsIgnoreCase(getString(R.string.KEY_NONE)))
            {
               wishRunnerService.setReminderType(reminderType);
            } else {
               return false;
            }
         } catch (Exception readConfigException) {
            return false;
         }
         
         return true;
      }
      // Get reminder config
      public String getReminderConfig()
      {
         JSONObject reminderJSON;
         String reminderConfig = "";
         
         try {
            reminderJSON = new JSONObject();
            if(wishRunnerService!=null) {
               reminderJSON.put("version", 1);
               reminderJSON.put("type", wishRunnerService.getReminderType());
               reminderConfig = reminderJSON.toString();
            }
         } catch (Exception configException)
         {
            return reminderConfig;
         }
         return reminderConfig;
      }
      
      public void sendToBrowser(String url) {
    	  Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    	  webView.getContext().startActivity(browserIntent);
      }
   }
}
