package net.inbetween.autoUpdate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import net.inbetween.NetworkMessage;
import net.inbetween.log.LogEntry;
import net.inbetween.log.LogProducer;
import net.inbetween.receivers.autoUpdateCheckReceiver;
import net.inbetween.receivers.connectionMonitorAlarmReceiver;
import net.inbetween.services.ServiceEvent;
import net.inbetween.services.WishRunner;
import net.inbetween.util.clientConfigInfo;
import net.inbetween.util.cloudAddress;
import net.inbetween.util.ibSupportUtil;
import net.inbetween.webview.R;

public class autoUpdate {
   private static final int XWISH_RET_CODE_OP_PERFORMED = 1;
   private static final int XWISH_RET_CODE_OP_NOT_PERFORMED = 0;
   private static final int XWISH_RET_CODE_OP_ERR = -1;
	
   private static final String defaultProdZipFileName = "ib.zip";
   private static final String zipFilePrefix = "ib_";
   private static final String apkFileName = "buildFileName";
   private static final String webPackageFileName = "packageFileName";
   
   private static final String assetsVersionFileName = "webPackageVersion.txt";

   private static final String zipFileDescriptor = "zipFileDescriptor.txt";
   private String downloadFolderName;
   private String assetsReadyStateFileName;
   private String ibAssetsFolderName;
   private String LOG_TAG;
   private LinkedBlockingQueue<updateEvent> updateQ = null;
	
   WishRunner wRunner;
   Context context;
   LogProducer logger;
   autoUpdateCheckReceiver updateCheckAlarm = null;
   int INITIAL_UPDATE_CHECK_DELAY;
   int IB_PFIELD_BODY_CHECK_FOR_UPDATE_PERIOD;
   
   public autoUpdate(
		 WishRunner _wRunner,  
         Context _context, 
         LogProducer _logger,
         LinkedBlockingQueue<updateEvent> _autoUpdateQ)
   {
      wRunner = _wRunner;
      context = _context;
      logger = _logger;
      
      // for now use locally allocated queue
      updateQ =  _autoUpdateQ;
      
      downloadFolderName =  context.getResources().getString(R.string.downloadFolderName);
      assetsReadyStateFileName =  context.getResources().getString(R.string.assetsReadyStateFileName);
      ibAssetsFolderName =  context.getResources().getString(R.string.ibAssetsFolderName);
      
      LOG_TAG =  context.getResources().getString(R.string.LOG_TAG);
      
      INITIAL_UPDATE_CHECK_DELAY  =  Integer.parseInt( wRunner.getString(R.string.INITIAL_UPDATE_CHECK_DELAY));
  	  IB_PFIELD_BODY_CHECK_FOR_UPDATE_PERIOD = Integer.parseInt( wRunner.getString(R.string.IB_PFIELD_BODY_CHECK_FOR_UPDATE_PERIOD));
  
  	  updateCheckAlarm = new autoUpdateCheckReceiver(context, "net.inbetween.updateCheckAlarm", new Bundle(), (INITIAL_UPDATE_CHECK_DELAY * 1000), updateQ, logger); 
      
      updateRunner();
   }
   
   private void updateRunner()
   {
      new Thread(new Runnable()
      {
         public void run()
         {   
        	autoUpdateSupport aupSupport = new autoUpdateSupport();

        	while(true){ 
        		Object instructionInfo = null;
        		updateEvent instruction;
        		boolean immidiateUpdate;
      		 
	 	        try{
	 	      	   // block on control queue
	 	          instruction = updateQ.take();
	 	          instructionInfo =  instruction.getInfo();
	 	          immidiateUpdate = instruction.getImmidiateUpdate();
	 	        }catch(Exception e){
               	   String errMsg1 = "T panic 5";
             	   wRunner.speakDebug(errMsg1);
             	   logger.log(LogEntry.SEV_ERROR, 4, errMsg1 + e.toString());
             	   return;
	 	        }
                
  	            String targetFile = null, targetUrl = null;
  	            updateEvent.UpdateEventType targetEvent;
  	            
	 	        String tkt = aupSupport.getTkt();
	 	        boolean ApkUpdate =false;
	 	        
                switch(instruction.getType()){
                   case CHECK_FOR_APK_UPDATE:
                	   ApkUpdate = true; 
                	   // no break
                   case CHECK_FOR_WEB_UPDATE:
                   { 
                	     cloudAddress cloudAddr = new cloudAddress(context);
                	     
                		 String vType = null;
                		   
                	     try{
                			   clientConfigInfo confInfo = cloudAddr.getClientConfig();
                			   vType = confInfo.getVersionType();
                		 }catch(Exception e){}
                	     
                         String newVersionType = (String) instructionInfo;
                         
                         if (newVersionType != null){
                        	 vType = newVersionType;
                         }
                	   
                	     if (!ApkUpdate){ 
                             if (updateCheckAlarm != null){
                            	 updateCheckAlarm.clear();
                            	 updateCheckAlarm = null;
                             }
                             wRunner.speakDebug("update set alarm");
                             updateCheckAlarm = new autoUpdateCheckReceiver(context, "net.inbetween.updateCheckAlarm", new Bundle(), (IB_PFIELD_BODY_CHECK_FOR_UPDATE_PERIOD * 1000), updateQ, logger);                       
                	     }
                	                 	                  	   
	                	  NetworkMessage msgFromServer = null;
	                	  JSONObject updateResult = null;
	                	  String apkFile = null,  webPkgFile = null;
	                	  
	                	  String pkgVersion = aupSupport.getPkgVersionToReportToCloud();
	                	  String appBuildId =  context.getResources().getString(R.string.app_build_id);
	                	  
	                	  if (tkt == null || tkt.length() == 0){
	                		  wRunner.speakDebug("No ticket. Not checking for upate");
	                		  break;
	                	  }
	                
	                	  String updateMsg = aupSupport.buildCheckForUpdateMsg(appBuildId, pkgVersion, vType);
	                	  if (updateMsg != null) msgFromServer = aupSupport.checkForUpdate(tkt, updateMsg);
	                	  if (msgFromServer != null) updateResult =  msgFromServer.errorObjectBody();
	                	  
	                	  if (updateResult == null){ 
	                		  if (immidiateUpdate){
	                			  wRunner.broadcastServiceEvent(ServiceEvent.EventType.EVENT_CLIENT_UPDATE_FAILED);
	                		  }
	                		  break;
	                	  }
	                		
	        	          try{
	        	             apkFile = updateResult.getString(apkFileName);
	        	          }catch(Exception e){
	        	         	 apkFile = null;
	        	          }
	        	         
	        	          try{
	        	             webPkgFile = updateResult.getString(webPackageFileName);
	        	          }catch(Exception e){
	        	         	 webPkgFile = null;
	        	          }
	                	                    	     
	            	      // notify usr / option available in debug mode only
	            	    	          
	            	      if (ApkUpdate){
	            	    	  targetFile = apkFile;
	            	    	  targetEvent = updateEvent.UpdateEventType.DOWNLOAD_APK;  
	            	      }else{
	            	     	  targetFile = webPkgFile;
	            	    	  targetEvent = updateEvent.UpdateEventType.DOWNLOAD_WEB_PKG;   
	            	      }
	            	      
	            	      if (vType != null){
	            	    	  cloudAddr.writeClientConfig(vType, false);
	            	      }
	            	                 	      
	        	    	  if (targetFile != null){
	        	    		  if (immidiateUpdate) wRunner.broadcastServiceEvent(ServiceEvent.EventType.EVENT_CLIENT_UPDATING);
		            	      try
		            	      {
		            	    	  targetUrl = cloudAddr.getWebPortalUrl("/client/" + targetFile);
		            	    	  logger.log(LogEntry.SEV_INFO, 4, "CHECK_FOR_WEB_UPDATE: update needed. Will request " + targetUrl);
		            	          updateQ.put(new updateEvent(targetEvent, targetUrl, immidiateUpdate));
		            	      } catch (Exception ePut)
		            	      {
		            	    	  wRunner.speakDebug("checkForUpdate unexpected throw on auto Update Q");
		            	      }
	        	    	  }else{
	        	    		  if (immidiateUpdate) wRunner.broadcastServiceEvent(ServiceEvent.EventType.EVENT_CLIENT_UP_TO_DATE);
	        	    	  }          	      
                   }
 	                  break;
 	                  
                   case DOWNLOAD_APK:
                	  ApkUpdate = true; 
                	   // no break
  	                  
                   case DOWNLOAD_WEB_PKG:
                   {
            	  	  if (tkt == null || tkt.length() == 0){
                		  wRunner.speakDebug("No ticket. Not checking for upate");
                		  break;
                	  }
            	  	  targetUrl = (String) instructionInfo;
            	  	  
            	  	  // means first time, so we were going to dowload the zip file directly
            	  	  if (targetUrl == null){
            	  		cloudAddress cloudAddr = new cloudAddress(context);
          	    	    targetUrl = cloudAddr.getWebPortalUrl("/client/" + defaultProdZipFileName);
            	  	  }
            	      boolean downloadSuccess = aupSupport.downloadPKG(targetUrl, tkt, ApkUpdate);
            	      
            	      if (!downloadSuccess){
	            		  if (immidiateUpdate){
	            			  wRunner.broadcastServiceEvent(ServiceEvent.EventType.EVENT_CLIENT_UPDATE_FAILED);
	            		  }
            	      }else{
            	    	  
                	      if (ApkUpdate){
                	    	  targetEvent = updateEvent.UpdateEventType.INSTALL_APK;  
                	      }else{
                	    	  targetEvent = updateEvent.UpdateEventType.INSTALL_WEB_PKG;   
                	      }
                	      try{
                	          updateQ.put(new updateEvent(targetEvent, null, immidiateUpdate));
                	      } catch (Exception ePut)
	            	      {
	            	    	  wRunner.speakDebug("download unexpected throw on auto Update Q");
	            	      }         	    	  
            	      }
                       
   	                  break;  
                   }   
                   case INSTALL_APK:
                   {
	                	   // always exptected to be immidiate (as this option is only requested in debug mode)
	                	   // consumers are expected to get the apk from google store
	                       if (immidiateUpdate){
	                    	   wRunner.broadcastServiceEvent(ServiceEvent.EventType.EVENT_CLIENT_INSTALL_APK);
	                       }
                   }
    	              break;   
    	             
                   case INSTALL_WEB_PKG:
                   { 
	                	   // TODO: right now it install imediatly, (if the webview is up), it does not take into
	                	   // account of trying to wait until the webview is in the background so as not to interrupt
	                	   // the user. It should take it into account.  If the webview is not up whent the broadcast event
	                	   // is sent, then the user should see the updated bits.
	                	   aupSupport.applyAnyUpdateFromPendingPkg(immidiateUpdate);
                   }
    	              break;     
 	                 
	               default:
	               { 
	            	      String errMsg = "T panic 6";
	            	      wRunner.speakDebug(errMsg);
	            	      logger.log(LogEntry.SEV_ERROR, 4, errMsg);
	               }
                	  break;
                }        
        	 }
         }
      }).start();
   }
   
   class autoUpdateSupport {
	   
	   public String getPkgVersionToReportToCloud(){ 
		   String  pendingVersion = this.getCurrentlyPendingPkgVersion();
		   String  currentVersion = this.getCurrentlyInstalledPkgVersion();
		   boolean currentPackageReady = this.isWebPackageReady();
		   
		   if (pendingVersion != null) return pendingVersion;
		   
		   if ((currentVersion != null)  && currentPackageReady){
			   return currentVersion;
		   }else{
			   return "";
		   }
		}  
	   
	   public boolean fileExists(String filePath){
		File fn = new File(filePath);
		   return fn.exists();	
	   }
	   
	   public String getFullZipFileDescriptor(){
  		   File externalStorage = Environment.getExternalStorageDirectory();
		   return externalStorage.getAbsolutePath() + '/' + downloadFolderName + '/' + zipFileDescriptor;
	   }
	   
	   public String getFullZipFileName(){
  		   File externalStorage = Environment.getExternalStorageDirectory();
		   return externalStorage.getAbsolutePath() + '/' + downloadFolderName + '/' + defaultProdZipFileName;
	   } 
	   
	   public void deleteWebPackageReadyFile(){
		     ibSupportUtil zu = new ibSupportUtil(context.getFilesDir());
             zu.removeFile(ibAssetsFolderName, assetsReadyStateFileName);	   
	   }
	   
	   public void deleteZipFiles(){
		   String zipFile = this.getFullZipFileName();
		   String descriptorFile = this.getFullZipFileDescriptor();
		   
		   File zf = new File(zipFile);
		   if (zf.exists()){
			   zf.delete();
		   }
		   
		   File df = new File(descriptorFile);
		   
		   if (df.exists()){
			   df.delete();
		   }  
	   }
	   
	   public boolean zipPkgExists(){
		   String zipFile = this.getFullZipFileName();
		   
		   return this.fileExists(zipFile);
	   }
	   
	   public String getCurrentlyPendingPkgVersion(){
		   String versionFile = this.getFullZipFileDescriptor();
		   
		   if (!this.fileExists(versionFile)) return null;
		   
		   // make sure there is a corresponding zip file
		   if (!this.zipPkgExists()){
			   // descriptor file present and zip file not present is an error case so just delete the pending pkg
			   this.deleteZipFiles();
			   return null;
		   }
		   
		   // get the version value out of the version file
           ibSupportUtil zu = new ibSupportUtil(context.getFilesDir());
	
		   String version = null;
		   try{
		      version = zu.getFileContents(versionFile);
		   }catch(Exception e)  {
		  	    String errMsg = "get current pending version failed " ;
	 	        wRunner.speakDebug(errMsg);
		  	    logger.log(LogEntry.SEV_ERROR, 4, ( errMsg + e.toString()));
		   }

		   // strip carriege return if its there, as it causes the js bridge to blow up.
		   if (version != null){
		      version = zu.stripCR(version);
		   }
		   return version;
	   }
	   
	   public String getCurrentlyInstalledPkgVersion(){
		  	  String pkgVersion = null;
		  	  
		  	  try{
			     ibSupportUtil zu = new ibSupportUtil(context.getFilesDir());
			     
			     // check if the web folder is in a ready state
			     if (this.isWebPackageReady()){
			        pkgVersion = zu.getFileContents(ibAssetsFolderName, assetsVersionFileName);
			        if (pkgVersion != null){
			           // strip carriege return if its there, as it causes the js bridge to blow up.
			           pkgVersion = zu.stripCR(pkgVersion);
			        }
			     }
		  	  }catch(Exception e){
		  		    String errStr = "getAppBuildId: Got an exception while attempting to getPackageVersionFile ";
		   	       wRunner.speakDebug(errStr);
		  	       logger.log(LogEntry.SEV_ERROR, 4, ( errStr + e.toString()) );
		  	  }
	
	        return pkgVersion;
	   }
	   
	   // check if the web folder is in a ready state
	   public boolean isWebPackageReady(){
		     cloudAddress cloudAddr = new cloudAddress(context);
		     return cloudAddr.isWebPackageReady();
	   }
	   
	   public boolean isTherePendingPkgToInstall(){
		   String  pendingVersion = this.getCurrentlyPendingPkgVersion();
		   String  currentVersion = this.getCurrentlyInstalledPkgVersion();
		   boolean currentPackageReady = this.isWebPackageReady();
		    
		   boolean zipPkgExists = this.zipPkgExists(); 
		    
		   Log.d(LOG_TAG,("isTherePendingPkgToInstall: pendingVersion " +  pendingVersion + " currentVersion " + currentVersion));
		   
		    if (!zipPkgExists){
		        return false;
		    }else{
		        if (currentVersion == null || !currentPackageReady){
		            return true;
		        }else{
		            if (pendingVersion == null){
		                return true;
		            }else if (pendingVersion.compareTo(currentVersion) == 0){
		                return false;
		            }else{
		                return true;
		            }
		        }  
		    }
	   }
	   
	   public void applyAnyUpdateFromPendingPkg(boolean immidiateUpdate){

		   int result = XWISH_RET_CODE_OP_NOT_PERFORMED;
		   
		   if (this.isTherePendingPkgToInstall()){
			   result = this.installPackage();
			   this.deleteZipFiles();
		   }else{
			   Log.d(LOG_TAG, "ApplyAnyUpdateFromPendingPkg: Nothing is available to install");
		   }
		    
		    if (result == XWISH_RET_CODE_OP_PERFORMED){
		    	 Log.d(LOG_TAG,"ApplyAnyUpdateFromPendingPkg: installed pending package");
		        
		    	  if (immidiateUpdate){
		    	      wRunner.broadcastServiceEvent(ServiceEvent.EventType.EVENT_CLEINT_WEBPKG_INSTALLED_IMMEDIATE);
		    	  }else{
		    	      wRunner.broadcastServiceEvent(ServiceEvent.EventType.EVENT_CLEINT_WEBPKG_INSTALLED);
		    	  }
		        // this reloads the home page into webview
	
		    }else if (result == XWISH_RET_CODE_OP_ERR  && immidiateUpdate){
		    	 wRunner.broadcastServiceEvent(ServiceEvent.EventType.EVENT_CLIENT_UPDATE_FAILED);          
		    }   
	   }
	   
	   /*
	    * install web package
	    * if a zip package exists it applies it in place to the target directory
	    * returns:  XWISH_RET_CODE_OP_PERFORMED ,  XWISH_RET_CODE_OP_NOT_PERFORMED or XWISH_RET_CODE_OP_ERR 
	    */
	   
	   protected int installPackage(){
		   String zipFileName =  this.getFullZipFileName();
		   	   
		   if ( this.fileExists(zipFileName)){
			   Log.d(LOG_TAG, "installPackage: " + zipFileName  + " exists.  About to unzip it." );
		    }else{
		    	 Log.d(LOG_TAG, "installPackage:  " + zipFileName  + "  does not exists, so nothing to install, returning.");
		        return XWISH_RET_CODE_OP_NOT_PERFORMED;
		    }		   
		   // changing files in package so need to delete webpackage ready state
		   this.deleteWebPackageReadyFile();
		   	   
	  	    try{    		  	    	
				unzip(zipFileName);
				
				ibSupportUtil util  = new ibSupportUtil(context.getFilesDir());
				if (!util.writeFileContents(ibAssetsFolderName, assetsReadyStateFileName, "done")){
		  	        logger.log(LogEntry.SEV_ERROR, 4, "installWebPackage: Unable to write ready state file to unziped folder ");
		  	    	return XWISH_RET_CODE_OP_ERR ;
				}
				
				// add a file to the unzipped folder to indicate that it was unzipped successfully
			   
	  	    }catch(Exception e){
	  	        logger.log(LogEntry.SEV_ERROR, 4, "installWebPackage: Got an exception while attempting to unzip " + e.toString());
	  	    	return XWISH_RET_CODE_OP_ERR ;
	  	    }
		   
		   // not renaming assets folder, since removal of cache pages works   
		   return XWISH_RET_CODE_OP_PERFORMED;
	   }
	   
	   // pkgName cab be of the form ib_guid.zip or ib.zip
	   // returns null in case of ib.zip
	   public String getPkgGuidFromName(String pkgName){
		  if ((pkgName == null) || (pkgName.lastIndexOf( zipFilePrefix) == -1) || (pkgName.lastIndexOf(".zip") == -1)){
			  return null;
		  }
		  
		  String guid = pkgName.substring((pkgName.lastIndexOf( zipFilePrefix) + zipFilePrefix.length()), (pkgName.lastIndexOf(".zip") ));
		  return guid;
	   }
	   
	   public  String getTkt()
	   {
		   SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		   return settings.getString(context.getString(R.string.KEY_TICKET), "");
	   }
	   
	   
		protected boolean downloadPKG(String targetUrl, String currentTicket, boolean ApkUpdate) {
			int count;
			String pkgGuid = null;
			File zipFileDesc = null;
	
			try {						
			    if (targetUrl == null){
			     	logger.log(LogEntry.SEV_ERROR, 4, "downloadPKG: No target url passed in to download");
			    	return false;
			    }
								
			    String fileName = targetUrl.substring( targetUrl.lastIndexOf('/')+1, targetUrl.length());
			    
			    if ((fileName == null) || (fileName.length() == 0)){
			    	 logger.log(LogEntry.SEV_ERROR, 4, "doInBackgroundInternal: Failed to get file name " + targetUrl);
			    	return false;
			    }
			    
			    
			    if (!ApkUpdate){
			    	pkgGuid = this.getPkgGuidFromName(fileName);
			    	fileName = defaultProdZipFileName;
			    }
			  	 
			    // TODO: verify that pkgGuid is a correct value
			    logger.log(LogEntry.SEV_INFO, 4, "downloadPKG: targetUrl: " + targetUrl + " pkgGuid: " + pkgGuid);
			    
			    String downloadedFile =  downloadFolderName + "/" + fileName;
			   
				URL url = new URL(targetUrl);
				URLConnection conn = url.openConnection();		
				
	            if ((currentTicket != null) && (currentTicket.length() > 0)){                         
	               conn.addRequestProperty("stkt", currentTicket);                  
	            }
	                         			
				conn.connect();
				
				File externalStorage = Environment.getExternalStorageDirectory();
				String filePath = externalStorage.getAbsolutePath();
				
				// make sure the folder exists
				File folder = new File(filePath + '/' + downloadFolderName);
				if(!folder.exists()){
					boolean success = folder.mkdir();
					if (!success){
				    	logger.log(LogEntry.SEV_ERROR, 4, "doInBackgroundInternal: Failed to create folder " + downloadFolderName);
					    return false;
					}
				}
					
				//InputStream input = new BufferedInputStream(url.openStream());
				InputStream input = conn.getInputStream();			
				OutputStream output = new FileOutputStream(filePath + '/' + downloadedFile);
				
				logger.log(LogEntry.SEV_INFO, 4, "downloadPKG: location of downloded file: " + (filePath + '/' + downloadedFile));
		        
				// this is to write out the file that specifies the (guid, which is the version) of the zip package
				if (pkgGuid != null){
				    zipFileDesc = new File( folder, zipFileDescriptor);
				}
				
				byte data[] = new byte[1024];
	
				while ((count = input.read(data)) != -1) {
					output.write(data, 0, count);
				}
				
				output.flush();
				output.close();
				input.close();
		  	    		  	    
			} catch (Exception e) {
		   	    logger.log(LogEntry.SEV_ERROR, 4, ("Got an exception while attempting to download " + e.toString()));
				return false;
			}
			
			// write out the pkg guid file 
			if (zipFileDesc != null){
				ibSupportUtil util  = new ibSupportUtil(context.getFilesDir());
				
				try{
					if (!util.writeToFile(zipFileDesc, pkgGuid)){
			  	        logger.log(LogEntry.SEV_ERROR, 4, "downloadPKG: Unable to write pkg guid to zipFileDescriptor.txt ");
			  	    	return false;
					}
				}catch(Exception e){
					logger.log(LogEntry.SEV_ERROR, 4, "downloadPKG: Unable to write pkg guid to zipFileDescriptor.txt! " + e.toString());
					return false;
				}
			}
			
		    return true;
		}
		
		/*
		 * unzips archive into internal storage.  Supports archives with folder structures
		 * of any deapth. The unziped package ends up stored at:
		 *   /data/data/net.inbetween.webview/files/{package name}
		 */
		public void unzip(String archiveName) throws IOException {
		    
		       ZipInputStream zin = new ZipInputStream(new FileInputStream(archiveName));
		       ZipEntry ze = null;
		       byte[] buffer = new byte[1024];
		       int length =0;
		       
		     
		       while ((ze = zin.getNextEntry()) != null) { 
		          String targetName = ze.getName();
		       
		          if(!ze.isDirectory()) { 
		             String [] pathParts  = targetName.split("/");
		             FileOutputStream fout = null;
		        
		             if (pathParts.length == 0){
		               	 zin.closeEntry();
		               	 Log.d(LOG_TAG,("unsuported file structure: " + targetName ));
		                 throw new RuntimeException( "unsuported file structure: " + targetName);   	 
		             } else if (pathParts.length == 1){
		       	       fout = context.openFileOutput(pathParts[0], Context.MODE_PRIVATE);
		             }else {
		            	    File parentPath = context.getFilesDir();
		            	    File dir = null;
		            	    
		            	    for (int i =0; i < (pathParts.length -1); i++){
					           	dir = new File(parentPath, pathParts[i]); 
					           	
					           	if (!dir.exists()){
					           		dir.mkdirs();
					           	}else if (!dir.isDirectory()){
					           		dir.delete();
					           		dir = new File(parentPath, pathParts[i]);
					           		dir.mkdirs();
					           	}
					           	
					           	parentPath = dir;
		            	    }
			
			       	        File fileInDir = new File(dir, pathParts[pathParts.length -1]); 
			       	        
			       	        
			       	        fout = new FileOutputStream(fileInDir);
		             }
		         
		             while ((length = zin.read(buffer))>0) {
		         	     fout.write(buffer, 0, length);	         	     
		             }
		         
		             zin.closeEntry();
		             if (fout != null){ 
		            	 fout.flush();
		            	 fout.close();
		             
		             }
		          } 
		     }
		     zin.close();
		 } 
	
		protected NetworkMessage checkForUpdate(String ticket, String checkForUpdateMsgPayload) {
			cloudAddress cloudAddr = new cloudAddress(context);
			String cloudMsgPath = context.getString(R.string.cloudMsgPath);
			String updateCheckUrl = cloudAddr.getWebPortalUrl(cloudMsgPath);
			
		    DefaultHttpClient httpClient;
		    HttpContext httpContext;
	        HttpParams httpParams = new BasicHttpParams();
	        
	        int httpTimeout =  Integer.parseInt( wRunner.getString(R.string.httpTimeout));
	        
	        HttpConnectionParams.setConnectionTimeout(httpParams, httpTimeout);
	        HttpConnectionParams.setSoTimeout(httpParams, httpTimeout);
	        httpClient = new DefaultHttpClient(httpParams);       
	        httpContext = new BasicHttpContext();
	        
	        HttpPost httpPost =  new HttpPost(updateCheckUrl);
	        httpPost.setHeader("stkt", ticket);
	        httpPost.setHeader("Content-Type", "application/json");
	        httpPost.setHeader("Accept", "application/json");
			
	        StringEntity se;
	        try {
	        se = new StringEntity(checkForUpdateMsgPayload);
	        }catch (Exception e) {
		  	    String errMsg = "check for update failed building post message. " ;
	 	        wRunner.speakDebug(errMsg);
		  	    logger.log(LogEntry.SEV_ERROR, 4, ( errMsg + e.toString()) );
	        	return null;
	        }
	        
	        httpPost.setEntity(se);
	        
	        HttpResponse response = null;
	    
	        try {
	        	String errMsg = null;
	            response = httpClient.execute(httpPost, httpContext);
	            if (response != null) {
	               String clientVersionRepStr = EntityUtils.toString(response.getEntity());  
	               Log.d("wishRunner", "response from web portal " + clientVersionRepStr);
	               NetworkMessage msgFromServer = new NetworkMessage(clientVersionRepStr);
	                
	               if (msgFromServer.isError()){
	            	   if (msgFromServer.getErrorCode().equalsIgnoreCase("IB_SUCCESS")){
	            		   
	            		   return msgFromServer;
	            	   }else{
	            		   errMsg = "check for returned error from server: " + msgFromServer.getErrorCode();
	            	   }
	               }else{
	            	   errMsg = "check for update returned unexpected message ";
	               }
	            }else{
	            	errMsg = "check for update http response failed " ;
	            }
	            
	            if (errMsg != null){
	       	       wRunner.speakDebug(errMsg);
	      	       logger.log(LogEntry.SEV_ERROR, 4, errMsg );
	            }
	        }catch (Exception e) 
	        {
	  	       String errMsg = "check for update exception error. " ;
	  	       wRunner.speakDebug(errMsg);
	  	       logger.log(LogEntry.SEV_ERROR, 4, ( errMsg + e.toString()) );
	        } 
	        
	        return null;   
		}
		
	    public String buildCheckForUpdateMsg(String buildId, String packageId, String versionType)
		   {
		      try
		      {
		         NetworkMessage msg = new NetworkMessage();
		         msg.putHeader(NetworkMessage.DATA_CLIENT_VERSION_SCHEMA, -1, -1);
		         msg.putClientVersionSchemaBody(buildId, packageId, versionType);
	
		         return msg.toString();
	
		      } catch (Exception newException)
		      {
		         wRunner.speakDebug("build check for update message exception");
		         logger.log(
		               LogEntry.SEV_ERROR,
		               2,
		               "Build check for update message failed. " + newException.getMessage() != null ? newException
		                     .getMessage() : "");
		         return null;
		      }
		   }
   }
}