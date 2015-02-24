package net.inbetween.util;

public class clientConfigInfo {

   private String envSuffix = null;
   private String versionType = null;  // web package version type
   
   public clientConfigInfo(){
	   envSuffix = null;
	   versionType = null;
   }
   
   public String getVersionType(){
	   return versionType;
   }
   
   public void setVersionType(String _versionType){
	   versionType = _versionType;
   }
   
   public String getEnvSuffix(){
	   return envSuffix;
   }
   
   public void setEnvSuffix(String _envSuffix){
	   envSuffix = _envSuffix;
   }
}