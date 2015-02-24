package net.inbetween.proximity;

import org.json.JSONObject;
import org.json.JSONTokener;

import android.location.Location;

public class LocationHelper {
	public static final String KEY_LATITUDE = "latitude";
	public static final String KEY_LONGITUDE = "longitude";
	public static final String KEY_PROVIDER = "provider";
	public static final String KEY_TIME = "time";
	public static final String KEY_ALTITUDE = "altitude";
	public static final String KEY_ACCURACY = "accuracy";
	public static final String KEY_BEARING = "bearing";
	public static final String KEY_SPEED = "speed";
	   
	public static Location locationFromJSON(JSONObject locJSON) {
		Location loc = null;
		try {
			String provider = locJSON.getString(LocationHelper.KEY_PROVIDER);
			loc = new Location(provider);
			
			if(locJSON.has(LocationHelper.KEY_ACCURACY)) loc.setAccuracy((float) locJSON.getDouble(LocationHelper.KEY_ACCURACY));
			if(locJSON.has(LocationHelper.KEY_ALTITUDE)) loc.setAltitude(locJSON.getDouble(LocationHelper.KEY_ALTITUDE));
			if(locJSON.has(LocationHelper.KEY_BEARING)) loc.setBearing((float) locJSON.getDouble(LocationHelper.KEY_BEARING));
			if(locJSON.has(LocationHelper.KEY_SPEED)) loc.setSpeed((float) locJSON.getDouble(LocationHelper.KEY_SPEED));
			loc.setLatitude(locJSON.getDouble(LocationHelper.KEY_LATITUDE));
			loc.setLongitude(locJSON.getDouble(LocationHelper.KEY_LONGITUDE));
			loc.setTime(locJSON.getLong(LocationHelper.KEY_TIME));
			
		} catch(Exception e) { return null; }
		
		return loc;
	}
	
	public static JSONObject locationToJSON(Location loc) {
		JSONObject locJSON = new JSONObject();
		try {
			if(loc.hasAccuracy()) locJSON.put(LocationHelper.KEY_ACCURACY, loc.getAccuracy());
			if(loc.hasAltitude()) locJSON.put(LocationHelper.KEY_ALTITUDE, loc.getAltitude());
			if(loc.hasBearing()) locJSON.put(LocationHelper.KEY_BEARING, loc.getBearing());
			if(loc.hasSpeed()) locJSON.put(LocationHelper.KEY_SPEED, loc.getSpeed());
			locJSON.put(LocationHelper.KEY_LATITUDE, loc.getLatitude());
			locJSON.put(LocationHelper.KEY_LONGITUDE, loc.getLongitude());
			locJSON.put(LocationHelper.KEY_PROVIDER, loc.getProvider());
			locJSON.put(LocationHelper.KEY_TIME, loc.getTime());
		} catch (Exception e) { return null; }
		
		return locJSON;
	}
	
	public static String locationToJSONString(Location loc) {
		JSONObject locJSON = locationToJSON(loc);
		return (locJSON == null) ? "" : locJSON.toString();
	}
	
	public static Location jsonStringToLocation(String locJSONString) {
		JSONObject locJSON;
		try {
			locJSON = (JSONObject) new JSONTokener(locJSONString).nextValue();
		} catch(Exception e) { return null; }
		return locationFromJSON(locJSON);
	}
	
	public static boolean isSimilarLoc(Location loc1, Location loc2) {
		//compare locations excluding time
		Location testLoc1 = new Location(loc1);
		testLoc1.setTime(0);
		Location testLoc2 = new Location(loc2);
		testLoc2.setTime(0);
		
		return testLoc1.toString().equals(testLoc2.toString());
	}
	
	public static void recordLoc(Location loc) {
		//if in recording mode && not in private mode
		//(May be trivial, we shouldn't be running in private mode)
		
		//String newLoc = locationToJSONString(loc);
		//write to file
	}
	
	public static void readInLocs(String fileLoc) {
		//Read in file at fileLoc, parse the data, send to mockLocationGenerator
		
		//write to file
	}
}
