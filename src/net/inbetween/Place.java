package net.inbetween;

import java.util.ArrayList;
import java.util.List;

import android.app.PendingIntent;
import android.location.Location;

public class Place
{
   public static final String NO_GPS_FIX_LOC = "no_gps";
   
   private double longitude;
   private double latitude;
   private float radius;
   private boolean onLocation;
   private boolean entered;
   private boolean departed;
   private Location locOnPlace;
   private List<Location> cellLocs;
   private long switchTime;
   private PendingIntent pendingIntent;
   private String key;

   public static final String ENTERED = "_ENTERED";
   public static final String DEPARTED = "_DEPARTED";
   
   public PendingIntent getPendingIntent()
   {
      return pendingIntent;
   }

   public void setPendingIntent(PendingIntent inPendingIntent)
   {
      pendingIntent = inPendingIntent;
   }

   public Place(double inLat, double inLon, float inRadius) {
      longitude = inLon;
      latitude = inLat;
      radius = inRadius;
      onLocation = false;
      entered = false;
      departed = false;
      key = makeKey(latitude, longitude, radius);
      locOnPlace = null;
      cellLocs = new ArrayList<Location>();
      switchTime = 0;
   }
   
   public boolean entered()
   {
      return entered;
   }

   public void setEntered(boolean entered)
   {
      this.entered = entered;
   }

   public boolean departed()
   {
      return departed;
   }

   public void setDeparted(boolean departed)
   {
      this.departed = departed;
   }

   public double getLongitude()
   {
      return(longitude);
   }
   
   public double getLatitude()
   {
      return latitude;
   }
   
   public float getRadius()
   {
      return radius;
   }
   
   ////////////////////////////////////////////
   
   public Location getLocOnPlace() {
	   return locOnPlace;
   }
   
   public void setLocOnPlace(Location loc) {
	   locOnPlace = loc;
   }
   
   public boolean hasLocOnPlace() {
	   return (locOnPlace != null);
   }
   
   public void addCellLoc(Location loc) {
	   cellLocs.add(loc);
   }
   
   public int cellLocsSize() {
	   return cellLocs.size();
   }
   
   public List<Location> getCellLocs() {
	   return cellLocs;
   }
   
   public void clearCellLocs() {
	   cellLocs.clear();
   }
   
   public long getSwitchTime() {
	   return switchTime;
   }
   
   public void setSwitchTime(long time) {
	   switchTime = time;
   }
   
   ////////////////////////////////////////////
   
   public boolean onLocation()
   {
      return onLocation;
   }
   
   public void setOnLocation(boolean onLocation)
   {
      this.onLocation = onLocation;
   }
   
   public boolean equals(Object other) {
	   if (!(other instanceof Place)) {
		   return false;
	   }
	   Place p = (Place) other;
	   return ((longitude == p.longitude) &&
			   (latitude == p.latitude) &&
			   (radius == p.radius));
   }
   
   public String getKey() {
      return(key);
   }
   
   public static String makeKey(double lat, double lng, float rad) {
      return(Double.toString(lat) + "_" + Double.toString(lng) + "_" + Float.toString(rad));
   }
}
