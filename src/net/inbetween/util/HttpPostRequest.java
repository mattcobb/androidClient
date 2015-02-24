package net.inbetween.util;

import java.io.File;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import android.util.Log;

public class HttpPostRequest{

    private DefaultHttpClient httpClient;
    private HttpContext httpContext;
    private String ret;

    private HttpResponse response = null;
    private HttpPost httpPost = null;
    private String ticket;
    private String appName;

    public HttpPostRequest(String inTicket, String inAppName){
        HttpParams httpParams = new BasicHttpParams();

        HttpConnectionParams.setConnectionTimeout(httpParams, 30000);
        HttpConnectionParams.setSoTimeout(httpParams, 30000);
        httpClient = new DefaultHttpClient(httpParams);       
        httpContext = new BasicHttpContext();
        ticket = inTicket;
        appName = inAppName;
    }

    public void abort() {
        try {
            if (httpClient != null) {
                // TODO Log it
                httpPost.abort();
            }
        } catch (Exception e) {
            Log.d(appName, "Http post aborted: "+e.toString()); 
        }
    }

    public boolean sendPost(String url, String fullFileName) {
        return sendPost(url, fullFileName, null);
    }

    public boolean sendPost(String url, String postFileName, String contentType) {
        File postFile = new File(postFileName);
        boolean result = false;
        
        ret = null;

        httpClient.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.RFC_2109);

        httpPost = new HttpPost(url);
        
        response = null;

        FileEntity fileEntity = null;        

        httpPost.setHeader("User-Agent", appName);
        httpPost.setHeader("Accept", "text/html,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5");
        httpPost.setHeader("stkt", ticket);

        if (contentType == null) {
           contentType = "text/plain; charset=\"UTF-8\"";
        }
        httpPost.setHeader("Content-Type", contentType);
        
        fileEntity = new FileEntity(postFile, contentType);
        //fileEntity.setChunked(true);
        httpPost.setEntity(fileEntity);
        
        try {
            response = httpClient.execute(httpPost, httpContext);

            if (response != null) {
                ret = EntityUtils.toString(response.getEntity());
                if(ret.indexOf("IB_SUCCESS")>0) {
                   result = true;
                } else { result = false; }
            }
        } catch (Exception e) {
            Log.d(appName, "HttpPostRequest: " + e);
            result = false;
        }
        return result;
    }
    
    public boolean sendContentPost(String url, String postContents) {
    	return sendContentPost(url, postContents, null);
    }
    
    public boolean sendContentPost(String url, String postContents, String contentType) {
    	StringEntity entity = null;
    	
    	try {
    		entity = new StringEntity(postContents);
    	} catch(Exception e) {
    		//TODO: log some this
    		return false;
    	}
    	
    	return send(url, entity, contentType);
    }
    
    private boolean send(String url, AbstractHttpEntity entity, String contentType) {
    	
    	boolean result = false;
        
        ret = null;

        httpClient.getParams().setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.RFC_2109);

        httpPost = new HttpPost(url);
        
        response = null;  

        httpPost.setHeader("User-Agent", appName);
        httpPost.setHeader("Accept", "text/html,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5");
        httpPost.setHeader("stkt", ticket);

        if (contentType == null) {
           contentType = "text/plain; charset=\"UTF-8\"";
        }
        httpPost.setHeader("Content-Type", contentType);
        httpPost.setEntity(entity);
        
        try {
            response = httpClient.execute(httpPost, httpContext);
            
            if (response != null) {
                ret = EntityUtils.toString(response.getEntity());
                result = (ret.indexOf("IB_SUCCESS") > 0);
            }
        } catch (Exception e) {
            Log.d(appName, "HttpPostRequest: " + e);
            result = false;
        }
        return result;
    }
}
