package net.inbetween.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.json.JSONException;
import org.json.JSONObject;

import net.inbetween.webview.R;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

public class cloudAddress{
    private static final String TAG_ENV_SUFFIX = "envSuffix";
    private static final String TAG_VERSION_TYPE = "versionType"; // web pkg version type
    private static final String browserDownloadFolderName = "download";
    
	String envSuffix = null;
    private Context appContext = null;
    private String assetsReadyStateFileName;
    private String ibAssetsFolderName;
	
	public cloudAddress(Context _appContext)
	{
      clientConfigInfo clientConfig = null;
      
      appContext = _appContext;
      assetsReadyStateFileName =  appContext.getResources().getString(R.string.assetsReadyStateFileName);
      ibAssetsFolderName =  appContext.getResources().getString(R.string.ibAssetsFolderName);
      
      try{
         clientConfig = getClientConfig();
         if (clientConfig != null){
             String es = clientConfig.getEnvSuffix();
             if (es != null && es.length() > 0){
            	 envSuffix = '-' + es;
             }
         }
      }catch(Exception e){
    	  Log.d(appContext.getString(R.string.LOG_TAG), "no debug clientConfig available");
      }
	}
	
    public boolean isWebPackageReady(){
	     ibSupportUtil zu = new ibSupportUtil(appContext.getFilesDir());
	     String ready = null;
         try{
	        ready = zu.getFileContents(ibAssetsFolderName, assetsReadyStateFileName);
         }catch(Exception e){}
	 
	     return (ready != null) ? true: false;
    }
	
	public String getEnvSuffix(){
		return envSuffix;
	}
	
	public boolean isNonProduction(){
		if (envSuffix != null){
			return true;
		}else{
			return false;
		}
	}
	
	public String  getTCPPortalDomain(){
		String suffix = "";
		String target = null;
		String tcpPortalPrefix = appContext.getString(R.string.tcpPortalPrefix);
		String ibDomain = appContext.getString(R.string.ibDomain);
		
		if (envSuffix != null) suffix =  envSuffix;
	
		target = tcpPortalPrefix + suffix + '.' + ibDomain;
		return target;
	}
	
	// test uses prod for auth, so adding an explicit flag to control what is required
	public String  getAuthDomain(boolean prod){
		String suffix = "";
		String target = null;
		String authServerPrefix = appContext.getString(R.string.authServerPrefix);
		String ibDomain = appContext.getString(R.string.ibDomain);
		
		if (!prod){
		   if (envSuffix != null) suffix =  envSuffix;
		}
	
		target = authServerPrefix + suffix + '.' + ibDomain;
		return target;
	}
	
	public String getTgsUrl() {
		return getTgsUrl("");
	}
	
	public String getTgsUrl(String path){
		String protocol = appContext.getString(R.string.cloudProtocol);
		String domain = getAuthDomain(false);
		String port = appContext.getString(R.string.tgsServerPort);
		
		String target = protocol + "://" + domain + ":" + port + path;
		
		return target;
	}
	
	public String  getAgentLogUrl(){
		String suffix = "";
		String target = null;
		
		String protocol = appContext.getString(R.string.cloudProtocol);
		String webPortalPrefix = appContext.getString(R.string.webPortalPrefix);
		String agentLoggerPort = appContext.getString(R.string.agentLoggerPort);
		String agentLoggerPath = appContext.getString(R.string.agentLoggerPath);
		String ibDomain = appContext.getString(R.string.ibDomain);

		if (envSuffix != null) suffix =  envSuffix;
		
		target = protocol + "://" + webPortalPrefix + suffix + '.' + ibDomain + ':' +
				agentLoggerPort + agentLoggerPath;
		return (target );
	}
	
	// going forward, use this one for any path in web portal
	public String getWebPortalUrl(String path){
		String suffix = "";
		String target = null;
		
		String protocol = appContext.getString(R.string.cloudProtocol);
		String webPortalPrefix = appContext.getString(R.string.webPortalPrefix);
		String ibDomain = appContext.getString(R.string.ibDomain);
		String wepPortalPort = appContext.getString(R.string.webPortalPort);

		if (envSuffix != null) suffix =  envSuffix;
		
		target = protocol + "://" + webPortalPrefix + suffix + '.' + ibDomain + ':' + wepPortalPort  +  path;
		return (target );
	}
	
	
	public String getAnalyticsBaseUrl(){
		
		String agentLoggerPath = appContext.getString(R.string.analyticsBasePath);
		return (this.getWebPortalUrl(agentLoggerPath));
	}
	
	public String getVideoPortalUrl() {
		return getVideoPortalUrl(null);
	}
	
	public String getVideoPortalUrl(String videoFile) {
		String suffix = "";
		
		String protocol = appContext.getString(R.string.cloudProtocol);
		String webPortalPrefix = appContext.getString(R.string.webPortalPrefix);
		String ibDomain = appContext.getString(R.string.ibDomain);
		String port = appContext.getString(R.string.videoPortalPort);

		if (envSuffix != null) suffix =  envSuffix;
		
		String target = protocol + "://" + webPortalPrefix + suffix + '.' + ibDomain + ':' + port;
		if(videoFile != null) target += "/" + Uri.encode(videoFile);
		return target;
	}
	
   public boolean writeClientConfig(String targetVal, boolean env) {    
	   clientConfigInfo oldConfigInfo = null;
	   // read in the old config info if any
	   try{
		   oldConfigInfo = this.getClientConfig();
	   }catch(Exception e){}
	   
	   String  newVersionType = null;
	   String newEnvSuffix = null;
       
	   // preserve 
	   if (oldConfigInfo != null) {
		   newEnvSuffix = oldConfigInfo.getEnvSuffix();
		   newVersionType = oldConfigInfo.getVersionType();
	   }
	   
	   // and then overwrite the old value
	   if (env){
		   newEnvSuffix = targetVal;
	   }else{
		   newVersionType = targetVal;
	   }
	   
	   String configContents = "";
	   
	   if ((newEnvSuffix != null) && (newEnvSuffix.length() > 0)){
		   configContents = "\"envSuffix\":\"" + newEnvSuffix + "\"";
       }
	   
	   
	   if ((newVersionType != null) && (newVersionType.length() > 0)){
		   if (configContents.length() > 0) configContents += " , ";
		   configContents += "\"versionType\":\"" + newVersionType + "\"";
       }
	   
	   if (configContents.length() > 0) configContents = " {" + configContents +  " }";
	      
	   ibSupportUtil util  = new ibSupportUtil(appContext.getFilesDir());
	   String clientConfigFileName = appContext.getString(R.string.clientConfigFile);
	   
	   if(configContents.length() ==  0) {
		   File currentConfigFile = util.getPublicFile(browserDownloadFolderName, clientConfigFileName);
		   currentConfigFile.delete();
		   return true;
	   }

	   try {
		   return util.writePublicFileContents(browserDownloadFolderName, clientConfigFileName, configContents);
	   } catch(Exception e) {
		   return false;
	   }
   }	
	
	
	public clientConfigInfo getClientConfig() throws IOException{
		String fileContents = null;	
		
		clientConfigInfo clientConf = null;
	    
	    File homeDir= Environment.getExternalStorageDirectory();
	    
	    File packageDir = new File(homeDir, browserDownloadFolderName);
	    
		if (!packageDir.exists()){
			return null;
		}
		
		String configFileName = appContext.getString(R.string.clientConfigFile);
		
		fileContents = getAnyFileContents(packageDir, configFileName );
		
		if (fileContents != null){
	       try {
	    	   JSONObject jConf =  new JSONObject(fileContents); 
	    	   clientConf = new clientConfigInfo();
	    	   
	    	   String versionType = null, envSuffix = null;
	    	   
	    	   try{
	    	      envSuffix = jConf.getString(TAG_ENV_SUFFIX);
	    	   }catch(Exception e){};
	    	   
	    	   if (envSuffix !=null && envSuffix.length() > 0){
	    		   clientConf.setEnvSuffix(envSuffix);
	    	   }
	    	   
	    	   try{
	    	      versionType = jConf.getString(TAG_VERSION_TYPE);
	           }catch(Exception e){};
	    	   
	    	   if (versionType !=null && versionType.length() > 0){
	    		   clientConf.setVersionType(versionType);
	    	   }  
	    	   
	        } catch (JSONException e) {
	        	clientConf = null;
	        }
		}		
		return clientConf;
	}
	
	public String getAnyFileContents(File parentDir, String fileName) throws IOException {
		String fileContents = null;	

	    File targetFile = new File( parentDir, fileName);
	
		if (!targetFile.exists()){
			return null;
		}else{
	       InputStream in = null;
	       try {
	          in = new BufferedInputStream(new FileInputStream(targetFile));
	          byte data[] = new byte[1024];
	          int count = 0;
	
			  fileContents = "";
			  while ((count = in.read(data)) != -1) {
	             fileContents += new String(data, 0, count, "UTF-8");
			  }	   
	    	  if (fileContents.length() == 0) fileContents = null;   
	       } finally {
	         if (in != null) {
	    	    in.close();
	    	 }
	       }
		}
	    return fileContents;
	}
}