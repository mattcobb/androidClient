package net.inbetween.proximity;

import java.util.HashMap;
import java.util.Map;

import net.inbetween.log.LogProducer;
import net.inbetween.services.ServiceEvent;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Looper;

public class GeolocationProvider {
	private static final String GPS = LocationManager.GPS_PROVIDER;
	private static final String NETWORK = LocationManager.NETWORK_PROVIDER;
	private static final String PASSIVE = LocationManager.PASSIVE_PROVIDER;
	
	private LogProducer logger;
	private Context context;
	private LocationManager locManager;
	private WifiManager wifiManager;
	
	private Map<String, LocationListener> providerListeners;
	private LocationListener passiveListener;
	
	private boolean firstFix;
	private boolean enhanceLocations;
	
	private long MAX_LOCATION_TIME = 4 * 60 * 1000;
	private double ACCURACY_DECAY = 1.0; //1 meter per second
	
	private Location currentLocation;
	
	public GeolocationProvider(Context context, LogProducer logger) {
		this.logger = logger;
		this.context = context;
		
		locManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		
		//provider listeners are just used to turn on these providers
		providerListeners = new HashMap<String, LocationListener>();
		providerListeners.put(GPS, new LocationListener() {
			public void onLocationChanged(Location location) { } //all locations recieved through passive provider
			public void onProviderDisabled(String provider) { }
			public void onProviderEnabled(String provider) { }
			public void onStatusChanged(String provider, int status, Bundle extras) { }
		});
		
		providerListeners.put(NETWORK, new LocationListener() {
			public void onLocationChanged(Location location) { } //all locations recieved through passive provider
			public void onProviderDisabled(String provider) { }
			public void onProviderEnabled(String provider) { }
			public void onStatusChanged(String provider, int status, Bundle extras) { }
		});
		
		//
		passiveListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				handleLocation(location);
			}
			
			//empty methods to satisfy the interface
			public void onProviderDisabled(String provider) { }
			public void onProviderEnabled(String provider) { }
			public void onStatusChanged(String provider, int status, Bundle extras) { }
		};
		requestLocationUpdates(PASSIVE);
		
		enhanceLocations = false;
		firstFix = false;
	}
	
	public void destroy() {
		locManager.removeUpdates(passiveListener);
		stop();
	}
	
	private void handleLocation(Location loc) {
		if(enhanceLocations && loc.getProvider().equals(GPS)) {
			firstFix = true;
		}
		
		if(currentLocation == null) {
			setNewCurrentLoc(loc);
		} else {
			long timeDiff = loc.getTime() - currentLocation.getTime();
			double decayedAcc = ACCURACY_DECAY * ((loc.getTime() - currentLocation.getTime()) / 1000);
			if(timeDiff > MAX_LOCATION_TIME || loc.getAccuracy() <= (currentLocation.getAccuracy() + decayedAcc)) {
				setNewCurrentLoc(loc);
			}
		}
		
		if(enhanceLocations && !firstFix && loc.getProvider().equals(NETWORK) && wifiManager.isWifiEnabled()) {
			toggleNetworkListener();
		}
	}
	
	private void setNewCurrentLoc(Location location) {
		currentLocation = location;
		/*
		//Not used because going through the java bridge is too heavy
		if(sendUpLocations) {
			broadcastServiceEvent(ServiceEvent.EventType.EVENT_CLIENT_LOCATION_SUCCESS);
		}
		*/
	}
	
	private void requestLocationUpdates(String provider) {
		LocationListener locListener;
		if(provider.equals(PASSIVE)) {
			locListener = passiveListener;
		} else {
			locListener = providerListeners.get(provider);
		}
		
		locManager.requestLocationUpdates(provider, 0, 0, locListener, Looper.getMainLooper());
	}
	
	private void broadcastServiceEvent(ServiceEvent.EventType event) {
		ServiceEvent.broadcastServiceEvent(context, event);
	}
	
	private void toggleNetworkListener() {
   		locManager.removeUpdates(providerListeners.get(NETWORK));
    	requestLocationUpdates(NETWORK);
   	}
	
	public void start() {
		firstFix = false;
		
		for(String provider: providerListeners.keySet()) {
			requestLocationUpdates(provider);
		}
		
		enhanceLocations = true;
		if(currentLocation != null) {
			broadcastServiceEvent(ServiceEvent.EventType.EVENT_CLIENT_LOCATION_SUCCESS);
		}
	}
	
	public void stop() {
		firstFix = false;
		enhanceLocations = false;
		for(LocationListener locListener: providerListeners.values()) {
			locManager.removeUpdates(locListener);
		}
	}
	
	public Location getLocation() {
		return currentLocation;
	}
}
