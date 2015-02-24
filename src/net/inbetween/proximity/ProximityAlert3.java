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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class ProximityAlert3 {
	
	//Used by location Playback tool MockLocationGenerator class
	public static final String GPS_STATUS_IF = "net.inbetween.proximity.GPS_STATUS"; //Intent filter value
	public static final String GPS_STATUS = "gpsStatus";
	public static final String GPS_START = "gpsStart";
	public static final String GPS_STOP = "gpsStop";
	
	private static final String GPS = LocationManager.GPS_PROVIDER;
	private static final String NETWORK = LocationManager.NETWORK_PROVIDER;
	private static final String WIFI = "WiFi";
	private static final String PASSIVE = LocationManager.PASSIVE_PROVIDER;
	
	private boolean TRACKING;
	
	private boolean testingMode;
	
	private LocationManager locManager;
	private WifiManager wifiManager;
	
	private Accelerometer accelerometer;
	private long PHONE_STATIONARY_TIME = 4 * 60 * 1000;
	private boolean confirmNoMoveOnPlace;
	
	private Location lastLocation;
	private Map<String, Location> bestLocations;
	//private int LOC_STALE_TIME = 90 * 1000;
	private Map<String, Float> locStaleFactor;
	
	private Map<String, List<Location>> prevLocations;
	private long MAX_LOC_TIME = 10 * 60 * 1000;
	
	private long CAN_USE_LOC_TIME = 5 * 1000;
	
	private Map<String, Float> departFactor;
	private float NETWORK_DEPART_FACTOR = 1.5f;
	
	private double ON_PLACE_PERCENT = .75;
	
	private float WIFI_MAX_DIST = 80;
	private float CELL_MIN_ACC = 150;
	
	private List<Place> myPlaces;
	
	private int MAX_GPS_OK_LOC_TIME = 7 * 1000;
	private int GPS_BUCKET_MAX = 2;
	private TimedLeakyBucket gpsBurstLB;
	private Location okGPSLoc;
	
	private int GPS_START_BUCKET_TIME = 10 * 60 * 1000;
	private int GPS_START_BUCKET_SIZE = 3;
	private TimedLeakyBucket gpsStartLB;
	
	private boolean GPS_ON;
	private int GPS_MAX_FIX_TIME = 45 * 1000;
	private int GPS_TIME_AFTER_FIX = 30 * 1000;
	private int GPS_SLEEP_TIME = 60 * 1000;
	private long gpsStartTime;
	private long gpsStopTime;
	private long gpsFixTime;
	
	private boolean providerCheckNeedsGPS;
	private boolean userNeedsGPS;
	
	private float MAX_LOC_ACC = 500;
	private long CELL_DEPART_TIME = 30 * 1000;
	private float DEPART_TOGGLE_FACTOR = .4f; //times the radius
	
	private boolean askedForProviders;
	private boolean askedForWifi;
	
	private Map<Place, Location> maybeDepartedTimes;
	
	private int CELL_DEPART_TOTAL_COUNT = 5;
	private int CELL_DEPART_FAR_COUNT = 3;
	private int CELL_MAYBE_ENTER_COUNT = 2;
	
	private long IGNORE_NETWORK_AFTER_GPS_TIME = 60 * 1000;
	private boolean firstNetworkLocAfterStart;
	private long IGNORE_NETWORK_AFTER_WIFI_TIME = 15 * 1000;
	
	private LinkedBlockingQueue<ServiceEvent> servicesEventQ;

	private boolean allowedToTrack = false;
	
	private Set<String> disabledProviders;
	private Map<String, LocationListener> providerListeners;
	private LocationListener passiveListener;
	
	private long FIRST_GPS_ATTEMPT_TIME = 150 * 1000;
	private long APPROACHING_TIME = 5 * 60 * 1000;
	private long NOT_APPROACHING_TIME = 10 * 60 * 1000;
	private long STATIONARY_TIME = 20 * 60 * 1000;
	
	private long RECENT_DEPART_TIME = 2 * 60 * 1000;
	
	private Location prevNearGPSLoc;
	private Location nearGPSLoc;
	private boolean nearGPSLocsNeeded;
	
	private Handler mHandler;
	
	WishRunner wRunner;
	LogProducer logProd;
	
	private BroadcastReceiver wifiBR;
	private boolean listeningOnWifi;
	
	private boolean saveBattery;
	
	public ProximityAlert3(LinkedBlockingQueue<ServiceEvent> servicesEventQ, final WishRunner wRunner,
			LogProducer inLogger) {

		mHandler = new Handler(Looper.getMainLooper());
	    
		this.wRunner = wRunner;
		saveBattery = wRunner.getSaveBattery();
		this.servicesEventQ = servicesEventQ;
		Context wrContext = wRunner.getAppContext();
		locManager = (LocationManager) wrContext.getSystemService(Context.LOCATION_SERVICE);
		wifiManager = (WifiManager) wrContext.getSystemService(Context.WIFI_SERVICE);
		
		accelerometer = new Accelerometer(wrContext, this);
		
		firstNetworkLocAfterStart = true;
		
		GPS_ON = false;
		gpsFixTime = -1;
		
		askedForProviders = false;
		askedForWifi = false;
		
		myPlaces = new ArrayList<Place>();
		
		departFactor = new HashMap<String, Float>();
		departFactor.put(GPS, new Float(1.5));
		departFactor.put(WIFI, new Float(1.2));
		departFactor.put(NETWORK, new Float(1.1));
		
		logProd = inLogger;
		
		bestLocations = new HashMap<String, Location>();
		bestLocations.put(GPS, null);
		bestLocations.put(WIFI, null);
		bestLocations.put(NETWORK, null);
		
		lastLocation = null;
		
		prevLocations = new HashMap<String, List<Location>>();
		prevLocations.put(GPS, new ArrayList<Location>());
		prevLocations.put(WIFI, new ArrayList<Location>());
		prevLocations.put(NETWORK, new ArrayList<Location>());
		
		locStaleFactor = new HashMap<String, Float>();
		locStaleFactor.put(GPS, .25f); //(meter acc)/second
		locStaleFactor.put(WIFI, .25f); //(meter acc)/second
		locStaleFactor.put(NETWORK, 2.5f); //(meter acc)/second
		
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
				try {
					handleLocation(location);
				} catch(Exception e) {
					wRunner.speakDebug("Threw handling location");
					logProd.log(LogEntry.SEV_ERROR, 1, "Proximity Exception: Threw handling location: \n" + getStackError(e));
					stopTracking();
					startTracking();
				}
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
		maybeDepartedTimes = new HashMap<Place, Location>();
		
		startTracking();
	}
	
	/**
	 * Public functions
	 */
	
	public Location getLastLocation() {
		logProd.log(LogEntry.SEV_INFO, 10, "Proximity: Last Location: " + LocationHelper.locationToJSONString(lastLocation));
		if(lastLocation == null) return null;
		return new Location(lastLocation);
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
   	
   public boolean SaveBattery(final boolean save) {
      if(mHandler==null) return false;
      return mHandler.post(new Runnable() {
         public void run() {
            saveBattery = save;
         }
      });
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
				}
				
				for(Place p: myPlaces) {
					maybeDepartedTimes.remove(p);
				}
				
				myPlaces = newPlaceList;
				if(lastLocation != null) {
					lastLocation = setLocationHelper(lastLocation);
				}
				
				toggleNetworkListener();
				
				wRunner.speakDebug("new places");
			}
		});
	}
	
	/**
	 * Helper functions
	 */
	
	private void speakAndLog(String msg) {
		speakAndLog(msg, 10);
	}
	
	private void speakAndLog(String msg, int priority) {
		logProd.log(LogEntry.SEV_INFO, priority, "Proximity: " + msg);
		wRunner.speakDebug(msg);
	}
	
	private String getStackError(Exception e) {
		
		String errStr = e.getClass().getName();
		String errMsg = e.getMessage();
		if(errMsg == null) {
			errMsg =  e.getLocalizedMessage();
		}
		if(errMsg != null) {
			errStr += ": " + errMsg;
		}
		errStr += ":\n";
		
		StackTraceElement[] stackTrace = e.getStackTrace();
		for(StackTraceElement elm: stackTrace) {
			errStr += elm.toString() + ",\n";
		}
		return errStr;
	}
	
	public void toggleNetworkListener() {
   		locManager.removeUpdates(providerListeners.get(NETWORK));
    	requestLocationUpdates(NETWORK);
   	}
	
	private void requestLocationUpdates(String provider) {
		if(provider.equals(PASSIVE)) {
			locManager.requestLocationUpdates(provider, 0, 0, passiveListener, mHandler.getLooper());
		} else {
			locManager.requestLocationUpdates(provider, 0, 0, providerListeners.get(provider), mHandler.getLooper());
		}
	}
	
	private float distanceToPlace(Place p, Location loc) {
		float[] result = new float[1];
		Location.distanceBetween(p.getLatitude(), p.getLongitude(),
				loc.getLatitude(), loc.getLongitude(), result);
		return result[0];
	}
	
	/**
	 * Function used to determine the overlap in handling the locations
	 * TODO: replace with bell curve?
	 * 
	 */
	
	private double percentOverlap(float dist, Place p, Location loc) {
		
		float placeRad = p.getRadius();
		float locAcc = loc.getAccuracy();
		
		if(p.hasLocOnPlace()) {
			locAcc *= departFactor.get(loc.getProvider());
		}
		
		//no coverage
		if(dist > (placeRad + locAcc)) return 0;
		//loc fully in the place
		if(placeRad >= (dist + locAcc)) return 1;
		//place is fully in accuracy
		if(locAcc >= (dist + placeRad)) return ((placeRad * placeRad) / (locAcc * locAcc));
		
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
		
		double overlapArea = a1 + a2;
		double locArea = Math.PI * (locAcc * locAcc);
		
		return overlapArea / locArea;
	}
	
	/**
	 * Functions used to notify the service that places have been entered/departed
	 */
	
	private void departPlace(Place p) {
		p.setOnLocation(false);
		p.setLocOnPlace(null);
		p.clearCellLocs();
		p.setSwitchTime(System.currentTimeMillis());
		maybeDepartedTimes.remove(p);
		String info = p.getKey() + Place.DEPARTED;
		servicesEventQ.add(new ServiceEvent(ServiceEvent.EventType.EVENT_LOCATION_DEPARTED, info));
	}
	
	private void enterPlace(Place p) {
		p.setOnLocation(true);
		p.clearCellLocs();
		p.setSwitchTime(System.currentTimeMillis());
		String info = p.getKey() + Place.ENTERED;
		servicesEventQ.add(new ServiceEvent(ServiceEvent.EventType.EVENT_LOCATION_ENTERED, info));
	}

	private void doneWithPlaces(Location loc) {
		lastLocation = loc;
		Location newLoc = null;
		if(loc != null) newLoc = new Location(loc);
		
	    servicesEventQ.add(new ServiceEvent(ServiceEvent.EventType.EVENT_LOCATION_DONE, newLoc));
	    logProd.log(LogEntry.SEV_INFO, 10, "Proximity: Done with Places Loc: " + LocationHelper.locationToJSONString(lastLocation));
	}
	
	/**
	 * Helper functions for determining when to turn on GPS when entering with Cell Locations
	 */
	
	private int numLocsFarAway(Place p, List<Location> locs) {
		int numFarAway = 0;
		for(int i = 0; i < locs.size(); i++) {
			Location loc = locs.get(i);
			if(distanceToPlace(p, loc) > (p.getRadius() + (NETWORK_DEPART_FACTOR * loc.getAccuracy()))) {
				numFarAway++;
			}
		}
		
		return numFarAway;
	}
	
	private float MAX_CELL_DIST_DIFF = 50;
	private float MAX_CELL_ACC_DIFF = 50;
	private boolean isSimilarCellLocs(Location loc1, Location loc2) {
		
		if(Math.abs(loc1.getAccuracy() - loc2.getAccuracy()) > MAX_CELL_ACC_DIFF) return false;
		
		float dist = loc1.distanceTo(loc2);
		if(dist > MAX_CELL_DIST_DIFF) return false;
		
		return true;
	}
	
	private boolean isUniqueCellLoc(Location newLoc, List<Location> cellLocs) {
		for(int i = 0; i < cellLocs.size(); i++) {
			Location oldLoc = cellLocs.get(i);
			if(isSimilarCellLocs(newLoc, oldLoc)) return false;
		}
		
		return true;
	}
	
	private float APPROACHING_GPS_SPEED = 1.0f; // in m/s (approx. 2 mph)
	private boolean isGPSApproachingPlace(Location loc1, Location loc2, Place p) {
		
		if(loc1.getProvider().equals(Place.NO_GPS_FIX_LOC)) return true;
		
		float dist1 = distanceToPlace(p, loc1);
		float dist2 = distanceToPlace(p, loc2);
		
		float speedToPlace = 1000 * (dist2 - dist1) / (loc2.getTime() - loc1.getTime());
		
		return speedToPlace >= APPROACHING_GPS_SPEED;
	}
	
	private float STATIONARY_GPS_DISTANCE = 75; //number of meters considered stationary
	private boolean isStationary(Location loc1, Location loc2) {
		if(loc1.getProvider().equals(loc2.getProvider())) return false;
		return loc1.distanceTo(loc2) <= STATIONARY_GPS_DISTANCE;
	}
	
	private void setNearGPSLoc(Location loc) {
		prevNearGPSLoc = nearGPSLoc;
		nearGPSLoc = loc;
	}
	
	private void clearNearGPSLocs() {
		nearGPSLocsNeeded = false;
		if(nearGPSLoc == null) {
			prevNearGPSLoc = null;
			nearGPSLoc = null;
		}
	}
	
	private boolean shouldStartGPS(Place p) {
		long currentTime = System.currentTimeMillis();
		
		if(prevNearGPSLoc == null || (prevNearGPSLoc.getProvider().equals(GPS) &&
				(nearGPSLoc.getProvider().equals(Place.NO_GPS_FIX_LOC))))
		{
			if(nearGPSLoc.getTime() + FIRST_GPS_ATTEMPT_TIME < currentTime) {
				speakAndLog("maybe enter first attempt", 2);
				return true;
			}
			return false;
		}
		
		if(isStationary(prevNearGPSLoc, nearGPSLoc)) {
			if(nearGPSLoc.getTime() + STATIONARY_TIME < currentTime) {
				speakAndLog("maybe enter stationary", 2);
				return true;
			}
			return false;
		}
		
		if(isGPSApproachingPlace(prevNearGPSLoc, nearGPSLoc, p)) {
			if(nearGPSLoc.getTime() + APPROACHING_TIME < currentTime) {
				speakAndLog("maybe enter approaching", 2);
				return true;
			}
		} else {
			if(nearGPSLoc.getTime() + NOT_APPROACHING_TIME < currentTime) {
				speakAndLog("maybe enter moving away", 2);
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Functions to handle a location, returns true if there were any places were entered or exited
	 */
	
	private boolean handleCellLoc(Location loc) {
		
		boolean placeDelta = false;
		boolean doFirstToggle = false;
		boolean keepNearGPSLocs = false;
		boolean needsGPS = false;
		boolean gpsWasOn = GPS_ON;
		long currentTime = System.currentTimeMillis();
		
		for(int i = 0; i < myPlaces.size(); i++) {
			Place p = myPlaces.get(i);
			float distance = distanceToPlace(p, loc);
			
			if(p.hasLocOnPlace()) {
				Location locOnP = p.getLocOnPlace();
				String confirmingProvider = locOnP.getProvider();
				
				if(confirmingProvider.equals(NETWORK)) {
					double chanceOnPlace = percentOverlap(distance, p, loc);
					if(chanceOnPlace >= ON_PLACE_PERCENT) {
						p.setLocOnPlace(loc); //update our confirmation on the place
					} else if(chanceOnPlace <= 0) {
						//Cell confirmed us in so cell can confirm us out
						speakAndLog("cell departed place " + i, 2);
						departPlace(p);
						placeDelta = true;
					}
				} else if(confirmingProvider.equals(WIFI)) {
					if(maybeDepartedTimes.containsKey(p)) {
						Location maybeDepartLoc = maybeDepartedTimes.get(p);
						if((maybeDepartLoc.getTime() + CELL_DEPART_TIME) < currentTime) {
							if(maybeDepartLoc.getProvider().equals(GPS)) {
								speakAndLog("after toggle GPS departed place " + i, 2);
								departPlace(p);
								placeDelta = true;
							} else if(!gpsWasOn) {
								speakAndLog("cell maybe depart place " + i, 2);
								needsGPS = true;
							}
						} else {
							if(!(distance > (p.getRadius() + (loc.getAccuracy() * NETWORK_DEPART_FACTOR))
									|| distance > (p.getRadius() + (p.cellLocsSize() * DEPART_TOGGLE_FACTOR * loc.getAccuracy())))) {
								speakAndLog("cell remove maybe depart place " + i, 2);
								maybeDepartedTimes.remove(p);
							}
						}
					} else {
						if(distance > (p.getRadius() + (loc.getAccuracy() * NETWORK_DEPART_FACTOR)) //greater than distance to depart by network
								|| distance > (p.getRadius() + (p.cellLocsSize() * DEPART_TOGGLE_FACTOR * loc.getAccuracy()))) {
							doFirstToggle |= (maybeDepartedTimes.size() == 0);
							maybeDepartedTimes.put(p, loc);
						}
					}
				} else if(confirmingProvider.equals(GPS)) {
					//Collect Cell locations that could suggest a false depart
					boolean isUnique = isUniqueCellLoc(loc, p.getCellLocs());
					if(isUnique) {
						if(distance > (p.getRadius() + (NETWORK_DEPART_FACTOR * loc.getAccuracy())) && (GPS_ON || startGPS())) {
							p.addCellLoc(loc);
						}
						
						if(p.cellLocsSize() >= (CELL_DEPART_TOTAL_COUNT) && numLocsFarAway(p, p.getCellLocs()) >= CELL_DEPART_FAR_COUNT) {
							speakAndLog(loc.getProvider() + " departed place " + i + " with cell", 2);
							departPlace(p);
							stopGPS();
							placeDelta = true;
						}
					}
				}
			} else {
				if((p.getSwitchTime() + RECENT_DEPART_TIME) >= currentTime) {
					wRunner.speakDebug("recent depart place " + i);
					continue;
				}
				
				double chanceOnPlace = percentOverlap(distance, p, loc);
				if(chanceOnPlace >= ON_PLACE_PERCENT) { //this could happen for a very large place like a city
					speakAndLog(loc.getProvider() + " entered place " + i + ". " + ((int) (100 * chanceOnPlace)) + "% confidence", 2);
					p.setLocOnPlace(loc);
					enterPlace(p);
					placeDelta = true;
				} else {
					if(chanceOnPlace > 0) {
						
						/**
						 * If 3 unique cell near a place (with no wifi or gps), suspected enter so start GPS. If not entered...
						 * In 2.5 minutes, try GPS again.
						 * 	  If no fix, try again in 2.5 minutes to either get another fix or 
						 * 	  If not entered... compare
						 * If stationary or both had no fix, try again in 20 minutes.
						 * If approaching, try again in 5 minutes.
						 * If moving away or going parallel, try again in 10 minutes.
						 */
						if(!GPS_ON) {
							//GPS was already started do we don't need to go through logic to start it
							
							if(nearGPSLoc == null) {
								if(isUniqueCellLoc(loc, p.getCellLocs())) {
									speakAndLog("adding network maybe enter to place " + i, 7);
									p.addCellLoc(loc);
								}
								
								if(p.cellLocsSize() >= CELL_MAYBE_ENTER_COUNT) {
									speakAndLog("maybe enter place " + i, 2);
									needsGPS = true;
									nearGPSLocsNeeded = true;
								}
							} else {
								needsGPS = needsGPS || shouldStartGPS(p);
							}
						}
					} else {
						if(p.cellLocsSize() > 0) {
							speakAndLog("clear maybe enter place " + i, 2);
						}
						
						p.clearCellLocs();
					}
					
					keepNearGPSLocs |= (p.cellLocsSize() >= CELL_MAYBE_ENTER_COUNT);
				}
			}
		}
		
		if(doFirstToggle) {
			wRunner.speakDebug(maybeDepartedTimes.size() + " places start toggle");
		}
		
		if(nearGPSLocsNeeded && !keepNearGPSLocs) {
			clearNearGPSLocs();
		}
		
		if(needsGPS && !gpsWasOn) {
			startGPS();
		}
		
		return placeDelta;
	}
	
	private boolean handleWifiLoc(Location loc) {
		stopGPS(true);
		
		boolean placeDelta = false;
		for(int i = 0; i < myPlaces.size(); i++) {
			Place p = myPlaces.get(i);
			
			//With wifi we know where we are now, we can clear these checks now
			
			
			float distance = distanceToPlace(p, loc);
			double chanceOnPlace = percentOverlap(distance, p, loc);
			
			if(p.hasLocOnPlace()) {
				if(chanceOnPlace <= 0) {
					//WIFI trumps all so we definately must have departed
					speakAndLog(loc.getProvider() + " departed place " + i + " with WiFi");
					departPlace(p);
					placeDelta = true;
				} else {
					if(!p.getLocOnPlace().getProvider().equals(WIFI)) {
						p.clearCellLocs();
					}
					p.setLocOnPlace(loc);
				}
			} else {
				p.clearCellLocs();
				if(chanceOnPlace >= ON_PLACE_PERCENT) {
					p.setLocOnPlace(loc);
					speakAndLog("WiFi entered place " + i + ". " + ((int) (100 * chanceOnPlace)) + "% confidence", 2);
					enterPlace(p);
					placeDelta = true;
				}
			}
		}
		
		clearNearGPSLocs();
		
		return placeDelta;
	}
	
	private boolean handleGPSLoc(Location loc) {
		
		boolean placeDelta = false;
		boolean doFirstToggle = false;
		
		for(int i = 0; i < myPlaces.size(); i++) {
			Place p = myPlaces.get(i);
			float distance = distanceToPlace(p, loc);
			
			double chanceOnPlace = percentOverlap(distance, p, loc);
			
			if(p.hasLocOnPlace()) {
				Location locOnP = p.getLocOnPlace();
				String confirmingProvider = locOnP.getProvider();
				
				if(confirmingProvider.equals(GPS) || confirmingProvider.equals(NETWORK)) {
					if(chanceOnPlace >= ON_PLACE_PERCENT) {
						Location locOnPlace = p.getLocOnPlace();
						if(!locOnPlace.getProvider().equals(GPS)) {
							p.clearCellLocs();
						}
						p.setLocOnPlace(loc); //update our confirmation on the place
					} else if(chanceOnPlace <= 0) {
						speakAndLog(loc.getProvider() + " departed place " + i, 2);
						departPlace(p);
						placeDelta = true;
					}
					/*
					//For now don't do anything if we're unsure
					else {
						//Do something so we can make a judgement sooner
					}
					*/
				} else if(confirmingProvider.equals(WIFI)) {
					if(chanceOnPlace <= 0) {
						if(!maybeDepartedTimes.containsKey(p)) {
							doFirstToggle |= (maybeDepartedTimes.size() == 0);
							maybeDepartedTimes.put(p, loc);
						} else {
							if((maybeDepartedTimes.get(p).getTime() + CELL_DEPART_TIME) < System.currentTimeMillis()) {
								speakAndLog(loc.getProvider() + " confirmed departed place " + i, 2);
								departPlace(p);
								placeDelta = true;
							}
						}
					} else {
						maybeDepartedTimes.remove(p);
					}
				}
			} else {
				if(chanceOnPlace >= ON_PLACE_PERCENT) {
					p.setLocOnPlace(loc);
					speakAndLog("GPS entered place " + i + ". " + ((int) (100 * chanceOnPlace)) + "% confidence", 2);
					enterPlace(p);
					placeDelta = true;
				} else {
					//TODO: figure out heuristics to set up the start of GPS if the person is moving
					if(!GPS_ON) {
						
					}
				}
			}
		}
			
		if(doFirstToggle) {
			wRunner.speakDebug("GPS maybe depart start toggle");
			toggleNetworkListener();
		}
		
		if(placeDelta) {
			stopGPS();
		}
		
		return placeDelta;
	}
	
	private boolean handleNetworkLoc(Location loc) {
		boolean placeDelta = false;
		for(Place p: myPlaces) {
			float distance = distanceToPlace(p, loc);
			double chanceOnPlace = percentOverlap(distance, p, loc);
			
			if(p.hasLocOnPlace()) {
				if(chanceOnPlace > 0) {
					maybeDepartedTimes.remove(p);
				}
			}
		}
		
		return placeDelta;
	}
	
	private Location normalizeLocation(Location location) {
		Location loc = new Location(location);
		String provider = loc.getProvider();
		if(provider.equals(NETWORK)) {
			if(loc.getAccuracy() <= WIFI_MAX_DIST) {
				loc.setProvider(WIFI);
			}
		}
		loc.setTime(System.currentTimeMillis());
		
		return loc;
	}
	
	private void handleLocation(Location location) {
		
		boolean placeDelta = false;
		boolean canProcessLoc = true;
		
		if(firstNetworkLocAfterStart && location.getProvider().equals(NETWORK)) {
			toggleNetworkListener();
			firstNetworkLocAfterStart = false;
			canProcessLoc = false;
		}
		
		if(location.getTime() + CAN_USE_LOC_TIME < System.currentTimeMillis()) {
			canProcessLoc = false;
		}
		
		Location loc = normalizeLocation(location);
		long locTime = loc.getTime();
		
		if(canProcessLoc) {
			
			String provider = loc.getProvider();
			
			if(provider.equals(WIFI) && maybeDepartedTimes.size() > 0) {
				maybeDepartedTimes.clear();
				speakAndLog("Remove network maybe depart", 7);
			}
			
			if(provider.equals(GPS)) {
				if(GPS_ON && (gpsFixTime < 0)) gpsFixTime = System.currentTimeMillis();
				
				//Hold on to best result, send best when leaky bucket can send a new value on stop GPS, fire stored location
				if(okGPSLoc == null) {
					okGPSLoc = loc;
				} else {
					long timeDiff = (loc.getTime() - okGPSLoc.getTime()) / 1000;;
					if(loc.getAccuracy() <= (okGPSLoc.getAccuracy() + (timeDiff * locStaleFactor.get(GPS)))) {
						okGPSLoc = loc;
					}
				}
				
				if(gpsBurstLB.takeToken()) {
					wRunner.speakDebug(provider);
					sendOkGPSLoc();
				}
			} else {
				wRunner.speakDebug(provider);
				
				if(provider.equals(WIFI)) {
					setLocation(loc);
					
					placeDelta = handleWifiLoc(loc);
				} else {
					if(loc.getAccuracy() >= CELL_MIN_ACC) {
						setLocation(loc);
						
						Location lastGPSLoc = bestLocations.get(GPS);
						Location lastWIFILoc = bestLocations.get(WIFI);
						if(lastGPSLoc == null 
								|| (lastGPSLoc.getTime() + IGNORE_NETWORK_AFTER_GPS_TIME) < locTime
								|| (lastWIFILoc.getTime() + IGNORE_NETWORK_AFTER_WIFI_TIME) < locTime) {
							placeDelta = handleCellLoc(loc);
						} else {
							wRunner.speakDebug("ignored after GPS");
						}
					} else {
						placeDelta = handleNetworkLoc(loc); //these are chaos monkey signals that can't be trusted
					}
				}
				
				if(((GPS_ON || userNeedsGPS) && (gpsFixTime < 0)) || maybeDepartedTimes.size() > 0) {
					toggleNetworkListener();
				}
				
				//addPrevLocation(loc);
			}
		}
			
		if(GPS_ON) {
			if(gpsFixTime > 0) {
				if(gpsFixTime + GPS_TIME_AFTER_FIX < locTime) stopGPS(true);
			} else {
				if(gpsStartTime + GPS_MAX_FIX_TIME < locTime) stopGPS(true);
			}
		} else {
			if(!accelerometer.isMoving() && (accelerometer.getStopMovingTime() + PHONE_STATIONARY_TIME < locTime)) {
				speakAndLog("No movement time", 2);
				
				if(needGPSBeforeSleep()) { //maybe entering or on a place b/c of GPS
					confirmNoMoveOnPlace = true;
					startGPS();
				} else {
					stopTracking(false);
				}
			}
		}
			
		if(placeDelta) {
			doneWithPlaces(loc);
		}
	}
	
	private boolean needGPSBeforeSleep() {
		for(int i = 0; i < myPlaces.size(); i++) {
			Place p = myPlaces.get(i);
			Location locOnPlace = p.getLocOnPlace();
			if(locOnPlace == null) {
				if(p.cellLocsSize() >= CELL_MAYBE_ENTER_COUNT) return true;
			} else {
				if(locOnPlace.getProvider().equals(GPS)) return true;
			}
		}
		return false;
	}
	
	private void sendOkGPSLoc() {
		if(okGPSLoc == null) return;
		
		Location locToSend = okGPSLoc;
		okGPSLoc = null;
		setLocation(locToSend);
		//addPrevLocation(loc);
		boolean placeDelta = handleGPSLoc(locToSend);
		if(placeDelta) doneWithPlaces(locToSend);
	}
	
	/**
	 * Helper functions to set the Location to store and send up
	 */
	
	/*
	// Use this later 
	private void addPrevLocation(Location loc) {
		String provider = loc.getProvider();
		wRunner.speakDebug(provider);
		
		List<Location> locList = listAfterTime(prevLocations.get(provider), System.currentTimeMillis() - MAX_LOC_TIME);
		locList.add(loc);
		
		prevLocations.put(provider, locList);
	}
	
	private List<Location> listAfterTime(List<Location> locList, long startTime) {
		int firstLocToKeep = 0;
		for(; firstLocToKeep < locList.size(); firstLocToKeep++) {
			Location l = locList.get(firstLocToKeep);
			if(l.getTime() >= startTime) break;
		}
		
		locList = locList.subList(firstLocToKeep, locList.size());
		return locList;
	}
	 */
	
	private Location ensureLocNotOnPlace(Location loc) {
		Location newLoc = loc;
		
		boolean modifyLoc = false;
		for(int i = 0; i < myPlaces.size(); i++) {
			Place p = myPlaces.get(i);
			if(distanceToPlace(p, loc) < p.getRadius()) {
				modifyLoc = true;
				break;
			}
		}
		
		if(modifyLoc) {
			newLoc = new Location(loc);
			newLoc.setLatitude(0);
			newLoc.setLongitude(0);
		}
		
		return newLoc;
	}
	
	private void setLastLocation(Location loc) {
		if(lastLocation == null) {
			lastLocation = setLocationHelper(loc);
			if(userNeedsGPS) wRunner.broadcastServiceEvent(ServiceEvent.EventType.EVENT_CLIENT_LOCATION_SUCCESS);
			return;
		}
		
		long timeDiff = (System.currentTimeMillis() - lastLocation.getTime()) / 1000;
		String lastLocProv = lastLocation.getProvider();
		int lastLocProvPri = getProviderPriority(lastLocProv);
		int newLocProvPri = getProviderPriority(loc.getProvider());
		
		if(lastLocProvPri > newLocProvPri || (lastLocProvPri == newLocProvPri
				&& (lastLocation.getAccuracy() + (timeDiff * locStaleFactor.get(lastLocProv))) >= loc.getAccuracy())) {
			lastLocation = setLocationHelper(loc);
			if(userNeedsGPS) wRunner.broadcastServiceEvent(ServiceEvent.EventType.EVENT_CLIENT_LOCATION_SUCCESS);
		}
	}
	
	private int getProviderPriority(String provider) {
		if(provider.equals(WIFI)) {
			return 1;
		} else if(provider.equals(GPS)) {
			return 2;
		} else if(provider.equals(NETWORK)) {
			return 3;
		} else {
			return 10;
		}
	}
	
	private boolean setLocation(Location location) {
		setLastLocation(location); //set this location until we coordinate all locations with the server
		
		Location lastLoc = bestLocations.get(location.getProvider());
		if(lastLoc == null) {
			bestLocations.put(location.getProvider(), location);
			return true;
		}
		
		long timeDiff = (System.currentTimeMillis() - lastLoc.getTime()) / 1000;
		
		if((lastLoc.getAccuracy() + (timeDiff * locStaleFactor.get(lastLoc.getProvider()))) >= location.getAccuracy()) {
			bestLocations.put(location.getProvider(), location);
			return true;
		} else {
			return false;
		}
	}
	
	//TODO: remove this function once server can handle very inaccurate locations
	private Location setLocationHelper(Location location) {
		if(location == null) return location;
		
		if(location.getAccuracy() <= MAX_LOC_ACC && !location.getProvider().equals(NETWORK)) {
			return location;
		} else {
			//return null;
			return ensureLocNotOnPlace(location);
		}
	}
	
	/**
	 * Functions to the control the GPS, returns true if the action was successful
	 */
	
	public boolean forceStartGPS() {
		return startGPS(true);
	}
	
	private boolean startGPS() { return startGPS(false); }
	private boolean startGPS(boolean forced) {
	    if(saveBattery)
	    {
	        speakAndLog("Save battery, no GPS", 5);
	        return false;
	    }
	    
	    if(providerCheckNeedsGPS) {
	    	speakAndLog("GPS not enabled, no GPS", 5);
	    	return false;
	    }
	   
		if(!GPS_ON) {
			speakAndLog((forced ? "forced " : "") + "starting GPS", 5);
			
			if(!forced && (gpsStopTime + GPS_SLEEP_TIME) > System.currentTimeMillis()) {
				speakAndLog("GPS not started in sleep time", 3);
				return false;
			}
			
			if(!forced && !gpsStartLB.takeToken()) {
				speakAndLog("bucket empty", 3);
				return false;
			} else {
				speakAndLog("bucket " + (gpsStartLB.getBucket() + 1), 8);
			}
			
			okGPSLoc = null;
			
			if(!userNeedsGPS) {
				gpsFixTime = -1;
				requestLocationUpdates(GPS);
			}
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
		if(providerCheckNeedsGPS) {
	    	return false;
	    }
		
		if(GPS_ON) {
			speakAndLog("GPS Stopped" + (sleep ? " with sleep" : ""), 5);
			
			if(okGPSLoc != null)  sendOkGPSLoc();
			
			
			if(!userNeedsGPS) locManager.removeUpdates(providerListeners.get(GPS));
			long currentTime = System.currentTimeMillis();
			if(sleep) {
				gpsStopTime = currentTime;
			}
			
			//notify test server
			Intent intent = new Intent(GPS_STATUS_IF);
			intent.putExtra(GPS_STATUS, GPS_STOP);
			wRunner.sendBroadcast(intent);
			
			//Logic to slow down GPS for entering a place with GPS
			if(nearGPSLocsNeeded) {
				Location bestLocOfGPSFixTime;
				if(gpsFixTime > 0) {
					bestLocOfGPSFixTime = bestLocations.get(GPS);
				} else {
					bestLocOfGPSFixTime = new Location(Place.NO_GPS_FIX_LOC);
					bestLocOfGPSFixTime.setTime(currentTime);
				}
				
				if(prevNearGPSLoc != null && nearGPSLoc != null && !bestLocOfGPSFixTime.getProvider().equals(Place.NO_GPS_FIX_LOC) &&
						prevNearGPSLoc.getProvider().equals(GPS) && nearGPSLoc.getProvider().equals(Place.NO_GPS_FIX_LOC)) {
					setNearGPSLoc(prevNearGPSLoc); //pushes the no GPS fix out of the way
				}
				
				setNearGPSLoc(bestLocOfGPSFixTime);
			}
			
			//Clear GPS statuses
			gpsFixTime = -1;
			GPS_ON = false;
			
			if(maybeDepartedTimes.size() > 0) {
				Set<Place> maybeDepartingPlaces = new HashSet(maybeDepartedTimes.keySet());
				Location lastCellLoc = bestLocations.get(NETWORK);
				boolean placeDelta = false;
				for(Place p: maybeDepartingPlaces) {
					float distance = distanceToPlace(p, lastCellLoc);
					if(distance > (p.getRadius() + (lastCellLoc.getAccuracy() * NETWORK_DEPART_FACTOR))) {
						departPlace(p);
						placeDelta = true;
					} else {
						p.addCellLoc(lastCellLoc);
					}
				}
				
				if(placeDelta) {
					Location loc = ensureLocNotOnPlace(lastCellLoc);
					doneWithPlaces(loc);
				}
				
				maybeDepartedTimes.clear();
			}
			
			if(confirmNoMoveOnPlace && (accelerometer.getStartMovingTime() < gpsStartTime)) {
				stopTracking(false);
				confirmNoMoveOnPlace = false;
			}
			
			return true;
		} else {
			return false;
		}
	}
	
	public boolean userStartGPS() {
		if(!TRACKING) return false;
		
		if(userNeedsGPS) return true;
		
		if(!GPS_ON && !providerCheckNeedsGPS) {
			gpsFixTime = -1;
			requestLocationUpdates(GPS);
		}
		
		userNeedsGPS = true;
		
		toggleNetworkListener();
		wRunner.broadcastServiceEvent(ServiceEvent.EventType.EVENT_CLIENT_LOCATION_SUCCESS);
		
		return true;
	}
	
	public boolean userStopGPS() {
		boolean prevUserNeedsGPS = userNeedsGPS;
		userNeedsGPS = false;
		
		if(!TRACKING) return false;
		if(!prevUserNeedsGPS) return true;
		
		if(!GPS_ON && !providerCheckNeedsGPS) {
			locManager.removeUpdates(providerListeners.get(GPS));
			gpsFixTime = -1;
		}
		
		return true;
	}
	
	private boolean stopTracking(boolean stopAccelerometer) {
		try {
			speakAndLog("stop tracking", 5);
		    TRACKING = false;
			GPS_ON = false;
			confirmNoMoveOnPlace = false;
			maybeDepartedTimes.clear();
			locManager.removeUpdates(passiveListener);
			locManager.removeUpdates(providerListeners.get(GPS));
			locManager.removeUpdates(providerListeners.get(NETWORK));
			if(stopAccelerometer) accelerometer.stop();
			return true;
		} catch(Exception e) {
			wRunner.speakDebug("Threw in stop tracking");
			logProd.log(LogEntry.SEV_ERROR, 1, "Proximity Exception: Threw in stop tracking: \n" + getStackError(e));
		}
		return false;
	}
	private boolean stopTracking() {
		return stopTracking(true);
	}
	
	private boolean startTracking(boolean startAccelerometer) {
		try {
			boolean canStart = verifyNeededProviders();
			if(canStart && allowedToTrack) {
				if(TRACKING) return false; //prevents adding listeners multiple times
				
				speakAndLog("started tracking", 5);
				TRACKING = true;
				firstNetworkLocAfterStart = true;
				confirmNoMoveOnPlace = false;
				requestLocationUpdates(PASSIVE);
				requestLocationUpdates(NETWORK);
				if(startAccelerometer) accelerometer.start();
				return true;
			}
		} catch(Exception e) {
			wRunner.speakDebug("Threw starting tracking");
			logProd.log(LogEntry.SEV_ERROR, 1, "Proximity Exception: Threw starting tracking: \n" + getStackError(e));
			stopTracking();
		}
		return false;
	}
	private boolean startTracking() {
		return startTracking(true);
	}
	
	private boolean verifyNeededProviders() {
		//check if and who we need to listen too
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
				if(newDisabledProviders.contains(GPS)) {
					providerCheckNeedsGPS = true;
					if(newDisabledProviders.size() > 1) {
						canStart = false;
					}
				} else {
					canStart = false;
					providerCheckNeedsGPS = false;
				}
				
				if(!askedForProviders) {
					askedForProviders = true;
					try {
						servicesEventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_LOCATION_NEEDPROVIDER,
	            		   	Boolean.TRUE));
	            	} catch(Exception putException) {};
				}
			} else {
				providerCheckNeedsGPS = false;
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
				if(askedForWifi == true) {
				   askedForWifi = false;
               try {
                  servicesEventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_LOCATION_NEEDWIFI, false));
               } catch(Exception putException) {};
				}
			} else {
				canStart = false;
				if(!askedForWifi) {
					try {
		               askedForWifi = true;
		               servicesEventQ.put(new ServiceEvent(ServiceEvent.EventType.EVENT_LOCATION_NEEDWIFI, true));
		            } catch(Exception putException) {};
		            stopTracking();
				}
			}
		}
		
		return canStart;
	}
	
	/**
	 * Public functions used by the accelerometer to trigger events in the proximity alert
	 */
	
	public boolean startedMoving() {
		wRunner.speakDebug("moving");
		return mHandler.post(new Runnable() {
			public void run() {
				if(!TRACKING) {
					speakAndLog("Movement", 2);
				}
				startTracking(false);
			}
		});
	}
	
	public boolean stoppedMovingForTime() {
		wRunner.speakDebug("stopped for time");
		/*
		return mHandler.post(new Runnable() {
			public void run() {
				//TODO: start the GPS if it's needed
			}
		});
		*/
		return true;
	}
	
	public boolean stoppedMoving() {
		wRunner.speakDebug("stopped");
		/*
		return mHandler.post(new Runnable() {
			public void run() {
				
			}
		});
		*/
		return true;
	}
}

class Accelerometer implements SensorEventListener {
	private SensorManager sensorManager;
	//private float[] gravity = {5.658f, 5.658f, 5.658f}; //averages to 9.8 m/s
	private float gravityMag = SensorManager.GRAVITY_EARTH;
	public static float FILTER_FACTOR = 0.1f; //Kalman filter factor
	
	private long possiblyChangeTime;
	private long startMovingTime;
	private long stopMovingTime;
	
	private ProximityAlert3 proxAlert;
	
	public static float START_ACC_CHANGE = .1f;// m/(s^3)
	public static float STOP_ACC_CHANGE = .05f;// m/(s^3)
	public static long TIME_TO_START = 1000;
	public static long TIME_TO_STOP = 2000;
	
	private boolean moving;
	
	public Accelerometer(Context context, ProximityAlert3 proxAlert) {
		sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
		this.proxAlert = proxAlert;
	    
	    moving = false;
	    
	    possiblyChangeTime = -1;
	    stopMovingTime = System.currentTimeMillis();
	    startMovingTime = 0;
	}
	
	public void onAccuracyChanged(Sensor sensor,int accuracy){ }
	
	public void onSensorChanged(SensorEvent event) {
		
		// check sensor type
		if(event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
		
		float[] accValues = event.values;
		
		float accMag = getMagnitude(accValues);
		gravityMag = (accMag * FILTER_FACTOR) + (gravityMag * (1.0f - FILTER_FACTOR));
        float magDiff = Math.abs(accMag - gravityMag);
		
        /*
        gravity[0] = (accValues[0] * kFilterFactor) + (gravity[0] * (1.0f - kFilterFactor));
        gravity[1] = (accValues[1] * kFilterFactor) + (gravity[1] * (1.0f - kFilterFactor));
        gravity[2] = (accValues[2] * kFilterFactor) + (gravity[2] * (1.0f - kFilterFactor));
        
        float[] accChange = {
        	(accValues[0] - gravity[0]),
        	(accValues[1] - gravity[1]),
        	(accValues[2] - gravity[2])
        };
        
        float accChangeMag = getMagnitude(accChange);
        */
        
        if(magDiff > START_ACC_CHANGE) {
        	if(!moving) {
        		long now = System.currentTimeMillis();
        		if(possiblyChangeTime > 0) {
        			if(possiblyChangeTime + TIME_TO_START < now) {
        		
	        			//started moving!
        				startMovingTime = System.currentTimeMillis();
	            		moving = true;
	            		proxAlert.startedMoving();
	            		possiblyChangeTime = -1;
        			}
        		} else {
        			possiblyChangeTime = now;
        		}
        	} else {
        		possiblyChangeTime = -1;
        	}
        //} else if(accChangeMag < STOP_ACC_CHANGE) {
        } else if(magDiff < STOP_ACC_CHANGE) {
        	if(moving) {
        		long now = System.currentTimeMillis();
        		if(possiblyChangeTime > 0) {
        			if(possiblyChangeTime + TIME_TO_STOP < now) {
	        			//stopped moving!
        				stopMovingTime = System.currentTimeMillis();
	            		moving = false;
	            		proxAlert.stoppedMoving();
	            		possiblyChangeTime = -1;
        			}
        		} else {
        			possiblyChangeTime = now;
        		}
        	} else {
        		possiblyChangeTime = -1;
        	}
        } else {
        	possiblyChangeTime = -1;
        }
	}
	
	private float getMagnitude(float[] acc) {
		if(acc == null) return -1;
		
		float total = 0;
		for(int i = 0; i < acc.length; i++) {
			total += (acc[i] * acc[i]);
		}
		
		return (float) Math.sqrt(total);
	}
	
	public long getStartMovingTime() {
		return startMovingTime;
	}
	
	public long getStopMovingTime() {
		return stopMovingTime;
	}
	
	public boolean isMoving() {
		return moving;
	}
	
	public void start() {
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_NORMAL);
	}
	
	public void stop() {
		sensorManager.unregisterListener(this);
	}
}
