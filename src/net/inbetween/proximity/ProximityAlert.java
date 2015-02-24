package net.inbetween.proximity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import net.inbetween.Place;
import net.inbetween.TimedLeakyBucket;
import net.inbetween.log.LogEntry;
import net.inbetween.log.LogProducer;
import net.inbetween.services.ServiceEvent;
import net.inbetween.services.WishRunner;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class ProximityAlert {
	public enum LocQuality { BAD, OK, GOOD }
	
	//Used by location Playback tool MockLocationGenerator class
	public static final String GPS_STATUS_IF = "net.inbetween.proximity.GPS_STATUS"; //Intent filter value
	public static final String GPS_STATUS = "gpsStatus";
	public static final String GPS_START = "gpsStart";
	public static final String GPS_STOP = "gpsStop";
	
	private final String GPS;
	private final String NETWORK;
	private final String WIFI;
	private final String PASSIVE;
	
	boolean testingMode;
	
	private LocationManager locManager;
	private WifiManager wifiManager;
	
	private Location lastLocation;
	private int LOC_STALE_TIME = 2 * 60 * 1000;
	
	private Map<String, Float> departFactor;
	private float NETWORK_ENTER_FACTOR = 1.5f;
	
	private float WIFI_MAX_DIST = 70;
	
	private List<Place> myPlaces;
	private Map<Place, Integer> placeNum;
	
	//private Location badNetworkLoc;
	//private int MAX_BAD_LOC_TIME = 5 * 60 * 1000;
	private double overlapRatio = .9;
	private double chanceOnPlaceDistanceGrowth = 2;
	
	private int MAX_GPS_OK_LOC_TIME = 7 * 1000;
	private int GPS_BUCKET_MAX = 2;
	private TimedLeakyBucket gpsBurstLB;
	private Location okGPSLoc;
	
	private int GPS_START_BUCKET_TIME = 10 * 60 * 1000;
	private int GPS_START_BUCKET_SIZE = 2;
	private TimedLeakyBucket gpsStartLB;
	
	private boolean GPS_ON;
	private int GPS_MAX_TIME = 60 * 1000;
	private int GPS_SLEEP_TIME = 60 * 1000;
	private long gpsStartTime;
	private long gpsStopTime;
	private boolean hadFirstGPS;
	
	private float MIN_GPS_ACC = 5;
	private float MAX_LOC_ACC = 500;
	private long BAD_LOC_TIME_TO_GPS = 60 * 1000;
	private long MAX_BAD_LOC_TIME_TO_GPS = 4 * 60 * 1000;
	
	private Set<String> disabledProviders;
	
	private double ON_PLACE_PERCENT = .9;
	GPSOnFunctions gpsOnFunc;
	
	private boolean askedForProviders;
	private boolean askedForWifi;
	
	private LinkedBlockingQueue<ServiceEvent> servicesEventQ;

	private boolean allowedToTrack = false;
	
	Map<String, LocationListener> providerListeners;
	LocationListener passiveListener;
	
	private Handler mHandler;
	private long maybeDepartedTimer;
	private long maybeEnterTimer;
	
	private float MAX_CELL_DEPART_FACTOR = .5f;
	
	//private Map<String, Float> goodAccuracy;
	//private Map<String, Float> okAccuracy;
	
	private List<Location> uniqueNetworkDepartLocs;
	private int enoughNetworkDepartLocs = 5;
	
	WishRunner wRunner;
	LogProducer logProd;
	
	BroadcastReceiver wifiBR;
	boolean listeningOnWifi;
	
	public ProximityAlert(LinkedBlockingQueue<ServiceEvent> servicesEventQ, WishRunner wRunner,
			LogProducer inLogger) {

		mHandler = new Handler(Looper.getMainLooper());
	   
		this.wRunner = wRunner;
		this.servicesEventQ = servicesEventQ;
		Context wrContext = wRunner.getAppContext();
		locManager = (LocationManager) wrContext.getSystemService(Context.LOCATION_SERVICE);
		wifiManager = (WifiManager) wrContext.getSystemService(Context.WIFI_SERVICE);
		
		GPS = LocationManager.GPS_PROVIDER;
		NETWORK = LocationManager.NETWORK_PROVIDER;
		WIFI = "WiFi";
		PASSIVE = LocationManager.PASSIVE_PROVIDER;
		
		GPS_ON = false;
		hadFirstGPS = false;
		
		lastLocation = null;
		
		//badNetworkLoc = null;
		
		askedForProviders = false;
		askedForWifi = false;
		
		myPlaces = new ArrayList<Place>();
		placeNum = new HashMap<Place, Integer>();
		
		departFactor = new HashMap<String, Float>();
		departFactor.put(GPS, new Float(1.5));
		departFactor.put(WIFI, new Float(1.2));
		departFactor.put(NETWORK, new Float(1.1));
		
		logProd = inLogger;
		
		//Set up graph parameters
		gpsOnFunc = new GPSOnFunctions(wRunner, inLogger);
		gpsOnFunc.setStretch(GPSOnFunctions.ENTER, .7);
		gpsOnFunc.setGrowth(GPSOnFunctions.ENTER, 1.25);
		gpsOnFunc.setShift(GPSOnFunctions.ENTER, 0);
		
		gpsOnFunc.setStretch(GPSOnFunctions.DEPART, .2);
		gpsOnFunc.setGrowth(GPSOnFunctions.DEPART, 1);
		gpsOnFunc.setShift(GPSOnFunctions.DEPART, .7);
		
		uniqueNetworkDepartLocs = new ArrayList<Location>();
		
		//Set up quality limits
		/*
		goodAccuracy = new HashMap<String, Float>();
		goodAccuracy.put(GPS, new Float(4));
		goodAccuracy.put(WIFI, new Float(35));
		goodAccuracy.put(NETWORK, new Float(75));
		
		okAccuracy = new HashMap<String, Float>();
		okAccuracy.put(GPS, new Float(15));
		okAccuracy.put(WIFI, new Float(55));
		okAccuracy.put(NETWORK, new Float(100));
		*/
		
		//Set up listeners
		providerListeners = new HashMap<String, LocationListener>();
		providerListeners.put(GPS, new LocationListener() {
			public void onLocationChanged(Location location) { } //all locations recieved through passive provider
			public void onProviderDisabled(String provider) {
				startTracking();
			}
			public void onProviderEnabled(String provider) {
				startTracking();
			}
			
			public void onStatusChanged(String provider, int status, Bundle extras) { }
		});
		
		providerListeners.put(NETWORK, new LocationListener() {
			public void onLocationChanged(Location location) { } //all locations recieved through passive provider
			public void onProviderDisabled(String provider) {
				startTracking();
			}
			
			public void onProviderEnabled(String provider) {
				startTracking();
			}
			
			public void onStatusChanged(String provider, int status, Bundle extras) { }
		});
		
		passiveListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				handleLocation(location);
			}
			
			//empty methods to satisfy the interface
			public void onProviderDisabled(String provider) { }
			public void onProviderEnabled(String provider) { }
			public void onStatusChanged(String provider, int status, Bundle extras) { }
		};
		
		//Set up WIFI BroadcastReceiver
		wifiBR = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) { 
				final String action = intent.getAction();
				int intentWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
				//if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
				if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
					if(intentWifiState == WifiManager.WIFI_STATE_ENABLED) {
						startTracking();
					} else if (intentWifiState == WifiManager.WIFI_STATE_DISABLED) {
						startTracking();
					}
				}
			}
		};
		
		gpsBurstLB = new TimedLeakyBucket(GPS_BUCKET_MAX, MAX_GPS_OK_LOC_TIME);
		gpsStartLB = new TimedLeakyBucket(GPS_START_BUCKET_SIZE, GPS_START_BUCKET_TIME);
		
		listeningOnWifi = false;
		maybeDepartedTimer = -1;
		maybeEnterTimer = -1;
		
		startTracking();
	}
	
	public Location getLastLocation() {
		return lastLocation;
	}
	
   public boolean Track(final boolean track) {
      return mHandler.post(new Runnable() {
         public void run() {
            allowedToTrack = track;
            if(allowedToTrack) {
               startTracking();
            } else {
               stopTracking();
            }
         }
      });
   }
   	
   	public void toggleNetworkListener() {
   		locManager.removeUpdates(providerListeners.get(NETWORK));
    	requestLocationUpdates(NETWORK);
   	}
      
	public boolean setPlaces(final Place[] newPlaces) {
		return mHandler.post(new Runnable() {
			public void run() {
				List<Place> newPlaceList = new ArrayList<Place>(newPlaces.length);
				for(int i = 0; i < newPlaces.length; i++) {
					Place myPlace = newPlaces[i];
					int pi = myPlaces.indexOf(myPlace);
					if(pi >= 0) {
		    			myPlace = myPlaces.remove(pi);
					}
					
					newPlaceList.add(myPlace);
					placeNum.put(myPlace, new Integer(i));
				}
				
				//remove unneeded places
				for(int i = 0; i < myPlaces.size(); i++) {
					placeNum.remove(myPlaces.get(i));
				}
				
				myPlaces = newPlaceList;
			}
		});
	}
	
	private ChanceOnPlaceResult verifyOnAndDistToPlaces(Location loc) {

	   logProd.log(LogEntry.SEV_INFO, 9, "Proximity: " + LocationHelper.locationToJSONString(loc));
		wRunner.speakDebug(loc.getProvider());
		
		long timeDiff = System.currentTimeMillis() - loc.getTime();
		if(timeDiff > 1000) {
			String msg = "Proximity: " + (timeDiff / 1000) + " second delay";
			logProd.log(LogEntry.SEV_INFO, 6, msg);
			wRunner.speakDebug(msg);
		}
		
		ChanceOnPlaceResult copr = new ChanceOnPlaceResult();
		double minDistance = Double.MAX_VALUE;
		int placeNum = -1;
		boolean placeDelta = false;
		String provider = loc.getProvider();
		boolean startNetworkMaybeDepart = false;
		boolean networkDeparted = false;
		boolean stopNetworkMaybeEnter = true;
		
		for(int i = 0; i < myPlaces.size(); i++) {
			Place p = myPlaces.get(i);
			float[] result = new float[1];
			Location.distanceBetween(p.getLatitude(), p.getLongitude(),
					loc.getLatitude(), loc.getLongitude(), result);
			float distance = result[0];
			
			double chanceOnPlace = percentOverlap(distance, p, loc);
			
			if(p.onLocation()) {
				if(provider.equals(NETWORK)) {
					if((distance - p.getRadius()) > (loc.getAccuracy() * NETWORK_ENTER_FACTOR)) {
						addUniqueBadLoc(loc);
					}
					
					if (uniqueNetworkDepartLocs.size() >= enoughNetworkDepartLocs && chanceOnPlace <= 0) {
						String msg = loc.getProvider() + " departed place " + i + " with network";
						logProd.log(LogEntry.SEV_INFO, 2, "Proximity: " + msg);
						wRunner.speakDebug(msg);
						departPlace(p);
						placeDelta = true;
						uniqueNetworkDepartLocs.clear();
						networkDeparted = true;
					} else {
						startNetworkMaybeDepart = true;
					}
					
				} else if(chanceOnPlace <= 0) {
					String msg = loc.getProvider() + " departed place " + i;
					logProd.log(LogEntry.SEV_INFO, 2, "Proximity: " + msg);
					wRunner.speakDebug(msg);
					departPlace(p);
					placeDelta = true;
				}
			} else {
				if(chanceOnPlace >= ON_PLACE_PERCENT) { //on place
					String msg = loc.getProvider() + " entered place " + i + ". " + ((int) (100 * chanceOnPlace)) + "% confidence";
				    logProd.log(LogEntry.SEV_INFO, 2, "Proximity: " + msg);
					wRunner.speakDebug(msg);
					enterPlace(p);
					placeDelta = true;
				} else if(provider.equals(NETWORK)) {
					if(maybeEnterTimer < 0) {
						if(chanceOnPlace > 0) {
							maybeEnterTimer = loc.getTime();
							stopNetworkMaybeEnter = false;
						}
					} else {
						if((distance - p.getRadius()) < (loc.getAccuracy() * NETWORK_ENTER_FACTOR)) {
							stopNetworkMaybeEnter = false;
						}
					}
				}
			}
			
			/*
			//min distance can be negative if you're found on a location but not verified on the spot
			if(!p.onLocation()) {
				if(chanceOnPlace > maxChanceNearPlace) maxChanceNearPlace = chanceOnPlace;
			}
			*/
			
			if(maybeEnterTimer >= 0 && stopNetworkMaybeEnter) maybeEnterTimer = -1;
			
			double distToPlace = distance - p.getRadius();
			double chanceOnPlaceDistance = getChanceOnPlaceDistance(distToPlace, loc.getAccuracy());
			double gpsChanceOnPlace = (chanceOnPlace * overlapRatio) + (chanceOnPlaceDistance * (1- overlapRatio));
			if(p.onLocation()) {
				if(provider.equals(WIFI)) {
					double chanceNotOnPlace = 1 - gpsChanceOnPlace;
					
					if(chanceOnPlace < ON_PLACE_PERCENT && copr.depart < chanceNotOnPlace && !provider.equals(NETWORK)) {
						copr.depart = chanceNotOnPlace;
					}
				}
			} else {
				if(chanceOnPlace < ON_PLACE_PERCENT  && copr.enter < gpsChanceOnPlace) {
					copr.enter = gpsChanceOnPlace;
				}
				
				if(distToPlace < minDistance) {
					minDistance = distToPlace;
					placeNum = i;
				}
			}
		}
		
		if(startNetworkMaybeDepart) {
			if(maybeDepartedTimer < 0) {
				String msg = "Start network toggle";
				wRunner.speakDebug(msg);
				logProd.log(LogEntry.SEV_INFO, 8, "Proximity: " + msg);
				maybeDepartedTimer = loc.getTime();
				toggleNetworkListener();
			}
		} else {
			if(networkDeparted) {
				maybeDepartedTimer = -1;
			}
		}
		
		if(placeDelta) {
		   doneWithPlaces(loc);
		}
		
		if(placeNum >= 0 && minDistance < 3000) {
			String distString = "";
			if(minDistance > 1000) {
				minDistance /= 100;
				distString = ((int) (minDistance / 10)) + "." + ((int) (minDistance % 10)) + " km";
			} else {
				distString = (int) minDistance + " m";
			}
			wRunner.speakDebug(distString + " to place " + placeNum);
		}
		
		return copr;
	}
	
	private double getChanceOnPlaceDistance(double dist, double acc) {
		if(dist < 0) return 1;
		return Math.min(1, Math.pow((acc / dist), chanceOnPlaceDistanceGrowth));
	}
	
	private void addUniqueBadLoc(Location loc) {
		List<Location> newUniqueBadLocs = new ArrayList<Location>();
		long now = System.currentTimeMillis();
		boolean uniqueLoc = true;
		for(int i = 0; i < uniqueNetworkDepartLocs.size(); i++) {
			Location oldBadLoc = uniqueNetworkDepartLocs.get(i);
			if(oldBadLoc.getTime() + MAX_BAD_LOC_TIME_TO_GPS > now) {
				newUniqueBadLocs.add(oldBadLoc);
			}
			
			uniqueLoc &= !LocationHelper.isSimilarLoc(oldBadLoc, loc);
		}
		
		if(uniqueLoc) {
			newUniqueBadLocs.add(loc);
		}
		
		uniqueNetworkDepartLocs = newUniqueBadLocs;
	}
	
	private void handleLocation(Location location) {
		Location loc = normalizeLocation(location);
		String provider = loc.getProvider();
		if(!provider.equals(NETWORK)) {
			if(maybeDepartedTimer > 0) {
				maybeDepartedTimer = -1;
				String msg = "Remove network maybe depart";
				wRunner.speakDebug(msg);
				logProd.log(LogEntry.SEV_INFO, 7, "Proximity: " + msg);
			}
			
			if(maybeEnterTimer > 0) {
				maybeEnterTimer = -1;
				String msg = "Remove network maybe enter";
				wRunner.speakDebug(msg);
				logProd.log(LogEntry.SEV_INFO, 7, "Proximity: " +  msg);
			}
			
			uniqueNetworkDepartLocs.clear();
		}
		
		//long locTime = location.getTime();
		long locTime = System.currentTimeMillis();
		if(provider.equals(GPS)) {
			if(GPS_ON && !hadFirstGPS) hadFirstGPS = true;
			
			/*
			 * Hold on to best result, send best when leaky bucket can send a new value
			 * on stop GPS, fire stored location
			 */
			
			if(okGPSLoc == null || loc.getAccuracy() <= okGPSLoc.getAccuracy()) {
				okGPSLoc = loc;
			}
			
			if(gpsBurstLB.takeToken()) {
				sendOkGPSLoc();
			}
			
		} else {
			//set new last location
			setLocation(loc);
			boolean toggle = GPS_ON && !hadFirstGPS; //ensure toggling while GPS is on
			
			if(0 < maybeDepartedTimer && maybeDepartedTimer < locTime) {
				if((maybeDepartedTimer + BAD_LOC_TIME_TO_GPS) < locTime) {
					String msg = "maybe depart started gps";
					wRunner.speakDebug(msg);
					logProd.log(LogEntry.SEV_INFO, 7, "Proximity: " + msg);
					handleGPS(locTime);
				} else {
					toggle = true;
				}
			}
			handleAdequateLoc(loc);
			
			if(toggle) toggleNetworkListener();
		}
		
		if((0 < maybeEnterTimer && maybeEnterTimer < locTime) && !GPS_ON) {
			if(maybeEnterTimer + MAX_BAD_LOC_TIME_TO_GPS < locTime) {
				String msg = "bad network started gps";
				wRunner.speakDebug(msg);
				logProd.log(LogEntry.SEV_INFO, 7, "Proximity: " + msg);
				handleGPS(locTime);
			}
		}
		
		if (GPS_ON && gpsStartTime + GPS_MAX_TIME < locTime) {
			stopGPS(true);
		}
		
	}
	
	private void sendOkGPSLoc() {
		Location locToSend = okGPSLoc;
		okGPSLoc = null;
		setLocation(locToSend);
		handleAdequateLoc(locToSend);
	}
	
	private boolean setLocation(Location location) {
		if(lastLocation == null || lastLocation.getAccuracy() >= location.getAccuracy()
				|| lastLocation.getTime() + LOC_STALE_TIME < location.getTime()) {
			setLocationHelper(location);
			return true;
		} else {
			return false;
		}
	}
	
	//TODO: remove this function once server can handle very inaccurate locations
	private void setLocationHelper(Location location) {
		if(location.getAccuracy() <= MAX_LOC_ACC) {
			lastLocation = location;
		} else {
			lastLocation = null;
		}
	}
	
	private void handleAdequateLoc(Location loc) {
		//badNetworkLoc = null;
		ChanceOnPlaceResult copr = verifyOnAndDistToPlaces(loc);
		
		if(GPS_ON || gpsOnFunc.isCloseEnough(copr)) {
			//handleGPS(loc.getTime());
			handleGPS(System.currentTimeMillis());
		} else { //ensures that the gps stays off
			stopGPS();
		}
	}
	
	/**
	 * 
	 * 
	 * TODO: replace with bell curve
	 * 
	 * 
	 */
	
	private double percentOverlap(float dist, Place p, Location loc) {
		
		float placeRad = p.getRadius();
		//if(p.onLocation()) placeRad *= departFactor.get(loc.getProvider());
		float locAcc = loc.getAccuracy();
		if(p.onLocation()) {
			locAcc *= departFactor.get(loc.getProvider());
		} else {
			if(loc.getProvider().equals(NETWORK)) {
				locAcc *= NETWORK_ENTER_FACTOR;
			}
		}
		
		//no coverage
		if(dist > (placeRad + locAcc)) return 0;
		//full fully in the place
		if(placeRad >= (dist + locAcc)) return 1;
		//place is fully in accuracy
		if(locAcc >= (dist + placeRad)) return ((placeRad * placeRad) / (locAcc * locAcc));
		
		/*
		//prevents division by 0;
		if(dist == 0) {
			if(placeRad > locAcc) {
				return 1;
			} else {
				return ((placeRad * placeRad) / (locAcc * locAcc));
			}
		}
		*/
		
		float r1; //larger radius
		float r2; //smaller radius
		if(placeRad > locAcc) {
			r1 = placeRad;
			r2 = locAcc;
		} else {
			r1 = locAcc;
			r2 = placeRad;
		}
		
		double d1 = ((dist * dist) - (r2 * r2) + (r1 * r1)) / (2 * dist);
		double d2 = dist - d1;
		double height = Math.sqrt((r1 * r1) - (d1 * d1));
		
		//area of large circle lens
		double a1 = (r1 * r1) * Math.acos(d1 / r1) - (d1 * height);
		
		//area of small circle lens
		double a2 = (r2 * r2) * Math.acos(d2 / r2) - (d2 * height);
		/*
		if(d1 > dist) { //smaller circle has most of it's area in the large circle
			a2 = (Math.PI * r2 * r2) - a2;
		}
		*/
		
		double overlapArea = a1 + a2;
		double locArea = Math.PI * (locAcc * locAcc);
		
		return overlapArea / locArea;
	}

	private void departPlace(Place p) {
		stopGPS();
		p.setOnLocation(false);
		String info = p.getKey() + Place.DEPARTED;
		servicesEventQ.add(new ServiceEvent(ServiceEvent.EventType.EVENT_LOCATION_DEPARTED, info));
	}
	
	private void enterPlace(Place p) {
		p.setOnLocation(true);
		String info = p.getKey() + Place.ENTERED;
		servicesEventQ.add(new ServiceEvent(ServiceEvent.EventType.EVENT_LOCATION_ENTERED, info));
	}

   private void doneWithPlaces(Location loc) {
      servicesEventQ.add(new ServiceEvent(ServiceEvent.EventType.EVENT_LOCATION_DONE, loc));
   }	
	
	private void handleGPS(long time) {
		if((gpsStopTime + GPS_SLEEP_TIME) <= time) { //not in sleep time
			//if (!GPS_ON || (gpsStartTime + GPS_MAX_TIME > time)) { //not too long
				startGPS();
			//} else {
			//	stopGPS(true);
			//}
		} else {
			stopGPS();
		}
	}
	
	public boolean forceStartGPS() {
		return startGPS(true);
	}
	
	private boolean startGPS() { return startGPS(false); }
	private boolean startGPS(boolean forced) {
		if(!GPS_ON) {
			String talkMsg = (forced ? "forced " : "") + "starting GPS";
			wRunner.speakDebug(talkMsg);
			logProd.log(LogEntry.SEV_INFO, 5, "Proximity: " + talkMsg);
			
			if(!forced && !gpsStartLB.takeToken()) {
				String msg = "bucket empty";
				wRunner.speakDebug(msg);
				logProd.log(LogEntry.SEV_INFO, 8, "Proximity: " +  msg);
				return false;
			} else {
				String msg = "bucket " + gpsStartLB.getBucket();
				wRunner.speakDebug(msg);
				logProd.log(LogEntry.SEV_INFO, 8, "Proximity: " +  msg);
			}
			
			requestLocationUpdates(GPS);
			gpsStartTime = System.currentTimeMillis();
			
			//notify test server
			Intent intent = new Intent(GPS_STATUS_IF);
			intent.putExtra(GPS_STATUS, GPS_START);
			wRunner.sendBroadcast(intent);
			
			GPS_ON = true;
			
			toggleNetworkListener();
			return true;
		} else {
			if(forced) gpsStartTime = System.currentTimeMillis();
			return false;
		}
	}
	
	private boolean stopGPS() { return stopGPS(false); }
	private boolean stopGPS(boolean sleep) {
		if(GPS_ON) {
			String talkMsg = "GPS Stopped" + (sleep ? " with sleep" : "");
			wRunner.speakDebug(talkMsg);
			logProd.log(LogEntry.SEV_INFO, 5, "Proximity: " + talkMsg);
			
			if(okGPSLoc != null)  sendOkGPSLoc();
			hadFirstGPS = false;
			
			locManager.removeUpdates(providerListeners.get(GPS));
			if(sleep) {
				gpsStopTime = System.currentTimeMillis();
				if(maybeDepartedTimer > 0) maybeDepartedTimer += GPS_MAX_TIME + BAD_LOC_TIME_TO_GPS; //try this again later
				if(maybeEnterTimer > 0) maybeEnterTimer += GPS_MAX_TIME + MAX_BAD_LOC_TIME_TO_GPS;
			}
			
			//notify test server
			Intent intent = new Intent(GPS_STATUS_IF);
			intent.putExtra(GPS_STATUS, GPS_STOP);
			wRunner.sendBroadcast(intent);
			
			GPS_ON = false;
			return true;
		} else {
			return false;
		}
	}
	
	private Location normalizeLocation(Location location) {
		Location loc = new Location(location);
		String provider = loc.getProvider();
		if(provider.equals(NETWORK)) {
			if(loc.getAccuracy() <= WIFI_MAX_DIST) {
				loc.setProvider(WIFI);
			}
		}
		return loc;
	}
	
	private void stopTracking() {
	   logProd.log(LogEntry.SEV_INFO, 5, "Proximity: stop tracking");
		GPS_ON = false;
		locManager.removeUpdates(providerListeners.get(GPS));
		locManager.removeUpdates(providerListeners.get(NETWORK));
		locManager.removeUpdates(passiveListener);
	}
	
	private void startTracking() {
		boolean canStart = verifyNeededProviders();
		if(canStart && allowedToTrack) {
		   logProd.log(LogEntry.SEV_INFO, 5, "Proximity: started tracking");
			requestLocationUpdates(PASSIVE);
			requestLocationUpdates(NETWORK);
		}
	}
	
	private void requestLocationUpdates(String provider) {
		if(provider.equals(PASSIVE)) {
			locManager.requestLocationUpdates(provider, 0, 0, passiveListener, mHandler.getLooper());
		} else {
			long timeBetweenLocs = 0;
			/*
			if(provider.equals(NETWORK)) {
				timeBetweenLocs = 20000; //20 seconds
			}
			*/
			
			locManager.requestLocationUpdates(provider, timeBetweenLocs, 0, providerListeners.get(provider), mHandler.getLooper());
		}
	}
	
	private boolean verifyNeededProviders() {
		//check if and who we need to listen too
		//TODO: check for private mode and don't run in that mode
		boolean canStart = true;
		if(!allowedToTrack) {
			canStart = false;
			if(listeningOnWifi) {
				wRunner.getAppContext().unregisterReceiver(wifiBR);
				listeningOnWifi = false;
			}
		} else {
			if(!listeningOnWifi) {
				IntentFilter wifiIntentFilter = new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION);
				wRunner.getAppContext().registerReceiver(wifiBR, wifiIntentFilter);
				listeningOnWifi = true;
			}
			
			Set<String> newDisabledProviders = new HashSet<String>();
			if(!locManager.isProviderEnabled(GPS)) {
				newDisabledProviders.add(GPS);
			}
			if(!locManager.isProviderEnabled(NETWORK)) {
				newDisabledProviders.add(NETWORK);
			}
			
			if(newDisabledProviders.size() > 0)
			{
				canStart = false;
				if(!askedForProviders) {
					try {
						servicesEventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_LOCATION_NEEDPROVIDER,
	            		   	Boolean.TRUE));
	               		askedForProviders = true;
	            	} catch(Exception putException) {};
				}		   
			} else {
			   if(askedForProviders) {
				   askedForProviders = false;
		           try {
		               
		               servicesEventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_LOCATION_NEEDPROVIDER, 
		                     Boolean.FALSE));
		           } catch(Exception putException) {};
			   }
			}
			
			if(disabledProviders == null || !disabledProviders.equals(newDisabledProviders)) {
				stopTracking();
				disabledProviders = newDisabledProviders;
				Iterator<String> it = disabledProviders.iterator();
				while(it.hasNext()) {
					requestLocationUpdates(it.next());
				}
			}
			
			if(wifiManager.isWifiEnabled()) {
				if(askedForWifi == true) askedForWifi = false;
			} else {
				canStart = false;
				if(!askedForWifi) {
		            try {
		               askedForWifi = true;
		               servicesEventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_LOCATION_NEEDWIFI, null));
		            } catch(Exception putException) {};
				}
			}
		}
		
		return canStart;
	}
}

class ChanceOnPlaceResult {
	public double enter;
	public double depart;
	
	public ChanceOnPlaceResult(double enter, double depart) {
		this.enter = enter;
		this.depart = depart;
	}
	
	public ChanceOnPlaceResult() {
		this(0, 0);
	}
}

class GPSOnFunctions {
	public static final String DEPART = "departing";
	public static final String ENTER = "entering";
	
	private Map<String, Double> gpsOnStretch;
	private Map<String, Double> gpsOnGrowth;
	private Map<String, Double> gpsOnShift;
	private Map<String, Double> gpsOnSlope;
	
	WishRunner wRunner;
	LogProducer logProd;
	
	public GPSOnFunctions(WishRunner wRunner, LogProducer logProd) {
		gpsOnGrowth = new HashMap<String, Double>();
		gpsOnStretch = new HashMap<String, Double>();
		gpsOnShift = new HashMap<String, Double>();
		gpsOnSlope = new HashMap<String, Double>();
		
		//Initialize
		gpsOnGrowth.put(ENTER, 0.0);
		gpsOnGrowth.put(DEPART, 0.0);
		gpsOnStretch.put(ENTER, 0.0);
		gpsOnStretch.put(DEPART, 0.0);
		gpsOnShift.put(ENTER, 0.0);
		gpsOnShift.put(DEPART, 0.0);
		
		this.wRunner = wRunner;
		this.logProd = logProd;
	}
	
	public void setGrowth(String type, double growth) {
		gpsOnGrowth.put(type, growth);
		calculateSlope(type);
	}
	
	public void setStretch(String type, double stretch) {
		gpsOnStretch.put(type, stretch);
		calculateSlope(type);
	}
	
	public void setShift(String type, double shift) {
		gpsOnShift.put(type, shift);
	}
	
	private void calculateSlope(String type) {
		double stretch = gpsOnStretch.get(type);
		double growth = gpsOnGrowth.get(type);
		gpsOnSlope.put(type, 1 / Math.pow(stretch, growth));
	}
	
	private double calculateChanceForGpsOn(String type, double chanceOnPlace) {
		double chanceForGps = 0;
		if(chanceOnPlace > gpsOnStretch.get(type) + gpsOnShift.get(type)) {
			wRunner.speakDebug(type + " " + ((int) (100 * chanceOnPlace)) + ". GPS needed");
			logProd.log(LogEntry.SEV_INFO, 8, "Proximity: " + type + " " + ((int) (100 * chanceOnPlace)) + ". GPS needed");
			chanceForGps = 1;
		} else {
			if(chanceOnPlace >= .0001) {
				if(chanceOnPlace < .01) {
					wRunner.speakDebug(type + " ." + ((int) (10000 * chanceOnPlace)));
					logProd.log(LogEntry.SEV_INFO, 8, "Proximity: " + type + " ." + ((int) (10000 * chanceOnPlace)));
				} else {
					wRunner.speakDebug(type + " " + ((int) (100 * chanceOnPlace)));
					logProd.log(LogEntry.SEV_INFO, 8, "Proximity: " + type + " " + ((int) (100 * chanceOnPlace)));
				}
			}
			
			if(chanceOnPlace > gpsOnShift.get(type)) {
				chanceForGps = (gpsOnSlope.get(type) * Math.pow((chanceOnPlace - gpsOnShift.get(type)), gpsOnGrowth.get(type)));
				
				wRunner.speakDebug("" + ((int) (100 * chanceForGps)));
				logProd.log(LogEntry.SEV_INFO, 8, "Proximity: " + ((int) (100 * chanceForGps)));
			}
		}
		
		return chanceForGps;
	}
	
	public boolean isCloseEnough(ChanceOnPlaceResult copr) {
		//turn on GPS for entering
		double chanceGpsEnter = calculateChanceForGpsOn(ENTER, copr.enter);
		if(Math.random() < chanceGpsEnter) return true;
		
		double chanceGpsDepart = calculateChanceForGpsOn(DEPART, copr.depart);
		if(Math.random() < chanceGpsDepart) return true;
		
		return false;
	}
}
/*
class TimedLeakyBucket {
	private int bucketSize;
	private long replenishTime;
	private long changeTime;
	private int bucket;
	
	public TimedLeakyBucket(int bucketSize, long replenishTime) {
		this.bucketSize = bucketSize;
		this.replenishTime = replenishTime;
		bucket = bucketSize;
	}
	
	public boolean isEmpty() {
		return bucket == 0;
	}
	
	public boolean takeToken() {
		replenish();
		if(isEmpty()) {
			return false;
		} else {
			bucket--;
			changeTime = System.currentTimeMillis();
			return true;
		}
	}
	
	public boolean isFull() {
		return bucket == bucketSize;
	}
	
	public void replenish() {
		if(isFull()) return;
		
		long currentTime = System.currentTimeMillis();
		long timeDiff = currentTime - changeTime;
		
		int replenishTokens = (int) (timeDiff / replenishTime);
		long remainingTime = timeDiff % replenishTime;
		
		if(replenishTokens > 0) {
			bucket += replenishTokens;
			if(bucket > bucketSize) bucket = bucketSize;
			
			changeTime = currentTime - remainingTime;
		}
	}
	
	public int getBucket() {
		return bucket;
	}
	
	public int getBucketSize() {
		return bucketSize;
	}
	
	public void setBucketSize(int newBucketSize) {
		bucketSize = newBucketSize;
		if(bucket > bucketSize) {
			bucket = bucketSize;
		}
	}
	
	public void setReplenishTime(long newReplenishTime) {
		replenishTime = newReplenishTime;
	}
	
	public long getReplenishTime() {
		return replenishTime;
	}
}
*/
