package net.inbetween.util;
import android.app.Activity;
import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ibSupportUtil{

  Activity activity;
  File homeDir;
  public ibSupportUtil(File _homeDir){
     homeDir = _homeDir;  
  }

  public boolean writeFileContents(String packageName, String fileName, String fileContents) throws IOException {
    
    File packageDir = new File(homeDir, packageName);
    
	if (!packageDir.exists()){
		return false;
	}

    File fileInDir = new File( packageDir, fileName);
    return writeToFile(fileInDir, fileContents);
 }
  
  public String stripCR(String str){
     // strip carriege return if its there, as it causes the js bridge to blow up.
     if ((str.length() >1) && (str.lastIndexOf("\n") == (str.length() -1) )){
	    str = str.substring(0, (str.length() -1));
     }
     return str;
  }
  
	public String getFileContents(String versionFileStr) throws IOException {    
	    File versionFile = new File(versionFileStr);
        return getFileContentsInternal(versionFile);
	}
  
	// returns the contents of the file residing in iternal storage in folder packageName
	public String getFileContents(String packageName, String fileName) throws IOException {    
	    File packageDir = new File(homeDir, packageName);
	    
		if (!packageDir.exists()){
			return null;
		}
	
	    File versionFile = new File( packageDir, fileName);
        return getFileContentsInternal(versionFile);
	}
	
	
	
	public void removeFile(String packageName, String fileName){    
	    File packageDir = new File(homeDir, packageName);
	    
		if (!packageDir.exists()){
			return;
		}
	
	    File versionFile = new File( packageDir, fileName);
	    
		if (!versionFile.exists()){
			return;
		}
		
		versionFile.delete();
	}
	
	
	
	private String getFileContentsInternal(File versionFile) throws IOException {
		String fileContents = null;		
	
		if (!versionFile.exists()){
			return null;
		}else{
	       InputStream in = null;
	       try {
	          in = new BufferedInputStream(new FileInputStream(versionFile));
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
	
	
	
	
	
	
	
	
	public boolean writeToFile(File file, String fileContents) throws IOException {
		if (!file.getParentFile().exists() || (fileContents == null)){
			return false;
		}
		
	    FileOutputStream fout = new FileOutputStream(file);
	    
	    byte [] buffer = fileContents.getBytes();
	    fout.write(buffer, 0, buffer.length);
	    if (fout != null) fout.close();

	    return true;
	}
	
	public boolean writePublicFileContents(String packageName, String fileName, String fileContents) throws IOException {
		
	    File fileInDir = getPublicFile(packageName, fileName);
	    if (fileInDir == null){
			return false;
		}
	    
	    return writeToFile(fileInDir, fileContents);
	}
	
	public File getPublicFile(String directory, String name) {
		File publicDir = Environment.getExternalStorageDirectory();
		publicDir = new File(publicDir, directory);
		if(!publicDir.exists()) {
			return null;
		}
		
		return new File(publicDir, name);
	}
}


