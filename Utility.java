package il.co.anyway.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import il.co.anyway.app.models.Accident;
import il.co.anyway.app.models.Discussion;
import il.co.anyway.app.singletons.AnywayRequestQueue;

public class Utility {

    private static final String LOG_TAG = Utility.class.getSimpleName();

    // AD = Accident and discussion parameters
    private static final String AD_CREATED = "created";
    private static final String AD_ID = "id";
    private static final String AD_LATITUDE = "latitude";
    private static final String AD_LONGITUDE = "longitude";
    private static final String AD_TITLE = "title";
    private static final String AD_TYPE = "type";

    private static final String DISCUSSION_IDENTIFIER = "identifier";

    private static final String ACCIDENT_ADDRESS = "address";
    private static final String ACCIDENT_DESC = "description";
    private static final String ACCIDENT_LOCATION_ACCURACY = "locationAccuracy";
    private static final String ACCIDENT_SEVERITY = "severity";
    private static final String ACCIDENT_SUBTYPE = "subtype";

    private static final int MARKER_TYPE_ACCIDENT = 1;
    private static final int MARKER_TYPE_DISCUSSION = 2;

    private static final String ANYWAY_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    /**
     * Match AccidentCluster an icon by the number of accidents
     *
     * @param count the number of accident
     * @return id of drawable icon
     */
    public static int getClusterImageByCountOfAccidents(int count) {

        final int[] res = {R.drawable.m1, R.drawable.m2, R.drawable.m3, R.drawable.m4};

        final int[] forCounts = {10, 100, 1000, Integer.MAX_VALUE};

        int markerIcon;
        int i = 0;
        do {
            markerIcon = res[i];
        } while (count >= forCounts[i++]);

        return markerIcon;
    }

    /**
     * Draw text on image, used to draw the number of AccidentCluster marker
     *
     * @param gContext context
     * @param gResId   id of image to draw on
     * @param gText    text to draw
     * @return Bitmap image with text draw on it
     */
    public static Bitmap drawTextToBitmap(Context gContext, int gResId, String gText) {

        Resources resources = gContext.getResources();
        float scale = resources.getDisplayMetrics().density;
        Bitmap bitmap = BitmapFactory.decodeResource(resources, gResId);

        android.graphics.Bitmap.Config bitmapConfig = bitmap.getConfig();

        // set default bitmap config if none
        if (bitmapConfig == null)
            bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888;

        // resource bitmaps are imutable,
        // so we need to convert it to mutable one
        bitmap = bitmap.copy(bitmapConfig, true);

        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize((int) (13 * scale)); // text size in pixels
        paint.setShadowLayer(1f, 0f, 1f, Color.WHITE); // text shadow

        // draw text to the Canvas center
        Rect bounds = new Rect();
        paint.getTextBounds(gText, 0, gText.length(), bounds);
        int x = (bitmap.getWidth() - bounds.width()) / 2;
        int y = (bitmap.getHeight() + bounds.height()) / 2;

        canvas.drawText(gText, x, y, paint);

        return bitmap;
    }

    /**
     * Parse JSON string to accidents list
     *
     * @param accidentJson       JSON object received from Anyway
     * @param fetchedAccidents   empty List<Accident> that all accidents will be added to
     * @param fetchedDiscussions empty List<Discussion> that all discussion will be added to
     * @return 0 for ok status, -1 for error in list, -2 for error in JSON
     */
    public static int getMarkersDataFromJson(@NonNull JSONObject accidentJson,
                                             @NonNull List<Accident> fetchedAccidents,
                                             @NonNull List<Discussion> fetchedDiscussions) {

        // These are the names of the JSON objects that need to be extracted.
        final String MARKERS_LIST = "markers";

        JSONArray accidentsArray;
        try {
            accidentsArray = accidentJson.getJSONArray(MARKERS_LIST);
        } catch (JSONException e) {
            return -2;
        }

        for (int i = 0; i < accidentsArray.length(); i++) {

            try {
                // Get the JSON object representing accident/discussion
                JSONObject markerDetails = accidentsArray.getJSONObject(i);

                // check if array object is Accident or Discussion
                int type;
                try {
                    type = markerDetails.getInt(AD_TYPE);
                } catch (JSONException e) {
                    // due to a temporary error in server Accident type is 'null' instead of 1
                    type = MARKER_TYPE_ACCIDENT;
                }

                if (type == MARKER_TYPE_ACCIDENT) {

                    Accident accident = parseJsonToAccident(markerDetails);
                    if (accident != null)
                        fetchedAccidents.add(accident);

                } else if (type == MARKER_TYPE_DISCUSSION) {

                    Discussion discussion = parseJsonToDiscussion(markerDetails);
                    if (discussion != null)
                        fetchedDiscussions.add(discussion);

                }
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getLocalizedMessage());
            }

        }

        return 0;
    }

    /**
     * Parse JSONobject to Discussion object
     *
     * @param discussionJsonObject the json object to parse
     * @return Discussion or null on error
     */
    public static Discussion parseJsonToDiscussion(JSONObject discussionJsonObject) {

        // get discussion details from json
        try {
            String created = discussionJsonObject.getString(AD_CREATED);
            // Date comes as 2013-12-30T21:00:00, needs to be converted
            Date createdDate;
            SimpleDateFormat sdf = new SimpleDateFormat(ANYWAY_DATE_FORMAT);
            try {
                createdDate = sdf.parse(created);
            } catch (ParseException e) {
                Log.e(LOG_TAG, e.getMessage());
                return null;
            }

            String identifier = discussionJsonObject.getString(DISCUSSION_IDENTIFIER);
            Double lat = discussionJsonObject.getDouble(AD_LATITUDE);
            Double lng = discussionJsonObject.getDouble(AD_LONGITUDE);
            Long id = discussionJsonObject.getLong(AD_ID);
            String title = discussionJsonObject.getString(AD_TITLE);

            // create new Discussion object and set parameters
            return new Discussion()
                    .setId(id)
                    .setTitle(title)
                    .setLocation(new LatLng(lat, lng))
                    .setCreated(createdDate)
                    .setType(MARKER_TYPE_DISCUSSION)
                    .setIdentifier(identifier);

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage());
            return null;
        }
    }

    /**
     * Parse JSONobject to Accident object
     *
     * @param accidentJsonObject the json object to parse
     * @return Accident or null on error
     */
    public static Accident parseJsonToAccident(JSONObject accidentJsonObject) {

        // get accident details from json
        try {
            // Date comes as 2013-12-30T21:00:00, needs to be converted
            String created = accidentJsonObject.getString(AD_CREATED);
            Date createdDate = new Date();
            SimpleDateFormat sdf = new SimpleDateFormat(ANYWAY_DATE_FORMAT);
            try {
                createdDate = sdf.parse(created);
            } catch (ParseException e) {
                Log.e(LOG_TAG, e.getMessage());
            }

            Double lat = accidentJsonObject.getDouble(AD_LATITUDE);
            Double lng = accidentJsonObject.getDouble(AD_LONGITUDE);
            Long id = accidentJsonObject.getLong(AD_ID);
            String title = accidentJsonObject.getString(AD_TITLE);
            String address = accidentJsonObject.getString(ACCIDENT_ADDRESS);
            String desc = accidentJsonObject.getString(ACCIDENT_DESC);
            Integer accuracy = accidentJsonObject.getInt(ACCIDENT_LOCATION_ACCURACY);
            Integer severity = accidentJsonObject.getInt(ACCIDENT_SEVERITY);
            Integer subtype = accidentJsonObject.getInt(ACCIDENT_SUBTYPE);

            // create new Accident object and set parameters
            return new Accident()
                    .setId(id)
                    .setTitle(title)
                    .setDescription(desc)
                    .setType(MARKER_TYPE_DISCUSSION)
                    .setSubType(subtype)
                    .setSeverity(severity)
                    .setCreatedDate(createdDate)
                    .setLocation(new LatLng(lat, lng))
                    .setAddress(address)
                    .setLocationAccuracy(accuracy);

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage());
            return null;
        }
    }

    /**
     * set all the variables needed for pulling accident data from Anyway API
     *
     * @param bounds    map bounds
     * @param zoomLevel map zoom level
     * @param context   activity context
     */
    public static void getMarkersByParameters(LatLngBounds bounds, int zoomLevel, Context context) {

        if (bounds == null || context == null)
            return;

        // Get preferences form SharedPreferences
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        Boolean show_fatal = sharedPrefs.getBoolean(context.getString(R.string.pref_accidents_fatal_key), true);
        Boolean show_severe = sharedPrefs.getBoolean(context.getString(R.string.pref_accidents_severe_key), true);
        Boolean show_light = sharedPrefs.getBoolean(context.getString(R.string.pref_accidents_light_key), true);
        Boolean show_inaccurate = sharedPrefs.getBoolean(context.getString(R.string.pref_accidents_inaccurate_key), false);
        String fromDate = sharedPrefs.getString(context.getString(R.string.pref_from_date_key), context.getString(R.string.pref_default_from_date));
        String toDate = sharedPrefs.getString(context.getString(R.string.pref_to_date_key), context.getString(R.string.pref_default_to_date));

        AnywayRequestQueue.getInstance(context).addMarkersRequest(
                bounds.northeast.latitude,
                bounds.northeast.longitude,
                bounds.southwest.latitude,
                bounds.southwest.longitude,
                zoomLevel,
                getTimeStamp(fromDate),
                getTimeStamp(toDate),
                show_fatal,
                show_severe,
                show_light,
                show_inaccurate
        );
    }

    /**
     * Covert date object to timestamp
     *
     * @param date java.util.Date object
     * @return TimeStamp, formatted to Anyway API requirements or empty String if date is null
     */
    private static String getTimeStamp(Date date) {
        if (date == null)
            return "";

        Long ts = new Timestamp(date.getTime()).getTime() / 1000;
        return Long.toString(ts);
    }

    /**
     * Covert date(saved as string) to timestamp
     *
     * @param dateStr Date as String, in dd/MM/yyyy format
     * @return TimeStamp, formatted to Anyway API requirements or empty String if date is in the wrong format
     */
    public static String getTimeStamp(String dateStr) {

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        Date date = null;

        try {
            date = dateFormat.parse(dateStr);
        } catch (ParseException e) {
            Log.e(LOG_TAG + "_TIME_STAMP", e.getMessage());
            e.printStackTrace();
        }

        return getTimeStamp(date);
    }

    /**
     * @param accidentSubType The type of the accident
     * @param appContext      Application Context
     * @return A string describing the accident type
     */
    public static String getAccidentTypeByIndex(int accidentSubType, Context appContext) {
        String str = "";

        switch (accidentSubType) {
            case Accident.ACCIDENT_TYPE_CAR_TO_PEDESTRIAN:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_CAR_TO_PEDESTRIAN);
                break;
            case Accident.ACCIDENT_TYPE_FRONT_TO_SIDE:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_FRONT_TO_SIDE);
                break;
            case Accident.ACCIDENT_TYPE_FRONT_TO_REAR:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_FRONT_TO_REAR);
                break;
            case Accident.ACCIDENT_TYPE_SIDE_TO_SIDE:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_SIDE_TO_SIDE);
                break;
            case Accident.ACCIDENT_TYPE_FRONT_TO_FRONT:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_FRONT_TO_FRONT);
                break;
            case Accident.ACCIDENT_TYPE_WITH_STOPPED_CAR_NO_PARKING:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_WITH_STOPPED_CAR_NO_PARKING);
                break;
            case Accident.ACCIDENT_TYPE_WITH_STOPPED_CAR_PARKING:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_WITH_STOPPED_CAR_PARKING);
                break;
            case Accident.ACCIDENT_TYPE_WITH_STILL_OBJECT:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_WITH_STILL_OBJECT);
                break;
            case Accident.ACCIDENT_TYPE_OFF_ROAD_OR_SIDEWALK:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_OFF_ROAD_OR_SIDEWALK);
                break;
            case Accident.ACCIDENT_TYPE_ROLLOVER:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_ROLLOVER);
                break;
            case Accident.ACCIDENT_TYPE_SKID:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_SKID);
                break;
            case Accident.ACCIDENT_TYPE_HIT_PASSSENGER_IN_CAR:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_HIT_PASSSENGER_IN_CAR);
                break;
            case Accident.ACCIDENT_TYPE_FALLING_OFF_MOVING_VEHICLE:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_FALLING_OFF_MOVING_VEHICLE);
                break;
            case Accident.ACCIDENT_TYPE_FIRE:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_FIRE);
                break;
            case Accident.ACCIDENT_TYPE_OTHER:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_OTHER);
                break;
            case Accident.ACCIDENT_TYPE_BACK_TO_FRONT:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_BACK_TO_FRONT);
                break;
            case Accident.ACCIDENT_TYPE_BACK_TO_SIDE:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_BACK_TO_SIDE);
                break;
            case Accident.ACCIDENT_TYPE_WITH_ANIMAL:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_WITH_ANIMAL);
                break;
            case Accident.ACCIDENT_TYPE_WITH_VEHICLE_LOAD:
                str = appContext.getString(R.string.str_ACCIDENT_TYPE_WITH_VEHICLE_LOAD);
                break;
        }

        return str;
    }

    /**
     * Choose which icon to show on the map(for the marker)
     *
     * @param severity        The severity of the accident
     * @param accidentSubType the type of the accident
     * @return ID of a drawable icon
     */
    public static int getIconForMarker(int severity, int accidentSubType) {

        int innerType = 0;

        // we only have 3 different icons so we need to choose which one to use for each type of accident
        switch (accidentSubType) {

            case Accident.ACCIDENT_TYPE_CAR_TO_PEDESTRIAN:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_PEDESTRIAN;
                break;
            case Accident.ACCIDENT_TYPE_FRONT_TO_SIDE:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_CAR;
                break;
            case Accident.ACCIDENT_TYPE_FRONT_TO_REAR:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_CAR;
                break;
            case Accident.ACCIDENT_TYPE_SIDE_TO_SIDE:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_CAR;
                break;
            case Accident.ACCIDENT_TYPE_FRONT_TO_FRONT:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_CAR;
                break;
            case Accident.ACCIDENT_TYPE_WITH_STOPPED_CAR_NO_PARKING:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_CAR;
                break;
            case Accident.ACCIDENT_TYPE_WITH_STOPPED_CAR_PARKING:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_CAR;
                break;
            case Accident.ACCIDENT_TYPE_WITH_STILL_OBJECT:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_OBJECT;
                break;
            case Accident.ACCIDENT_TYPE_OFF_ROAD_OR_SIDEWALK:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_OBJECT;
                break;
            case Accident.ACCIDENT_TYPE_ROLLOVER:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_OBJECT;
                break;
            case Accident.ACCIDENT_TYPE_SKID:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_OBJECT;
                break;
            case Accident.ACCIDENT_TYPE_HIT_PASSSENGER_IN_CAR:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_CAR;
                break;
            case Accident.ACCIDENT_TYPE_FALLING_OFF_MOVING_VEHICLE:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_OBJECT;
                break;
            case Accident.ACCIDENT_TYPE_FIRE:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_OBJECT;
                break;
            case Accident.ACCIDENT_TYPE_OTHER:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_OBJECT;
                break;
            case Accident.ACCIDENT_TYPE_BACK_TO_FRONT:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_CAR;
                break;
            case Accident.ACCIDENT_TYPE_BACK_TO_SIDE:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_CAR;
                break;
            case Accident.ACCIDENT_TYPE_WITH_ANIMAL:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_PEDESTRIAN;
                break;
            case Accident.ACCIDENT_TYPE_WITH_VEHICLE_LOAD:
                innerType = Accident.ACCIDENT_TYPE_CAR_TO_CAR;
                break;
        }

        // choose the icon to show by the icon type and the severity
        int icon = 0;
        if (accidentSubType == Accident.ACCIDENT_MULTIPLE) {
            switch (severity) {
                case Accident.SEVERITY_FATAL:
                    icon = R.drawable.multiple_lethal;
                    break;
                case Accident.SEVERITY_SEVERE:
                    icon = R.drawable.multiple_severe;
                    break;
                case Accident.SEVERITY_LIGHT:
                    icon = R.drawable.multiple_medium;
                    break;
                case Accident.SEVERITY_VARIOUS:
                    icon = R.drawable.multiple_various;
                    break;
            }
        } else if (severity == Accident.SEVERITY_FATAL) {
            switch (innerType) {
                case Accident.ACCIDENT_TYPE_CAR_TO_PEDESTRIAN:
                    icon = R.drawable.vehicle_person_lethal;
                    break;
                case Accident.ACCIDENT_TYPE_CAR_TO_CAR:
                    icon = R.drawable.vehicle_vehicle_lethal;
                    break;
                case Accident.ACCIDENT_TYPE_CAR_TO_OBJECT:
                    icon = R.drawable.vehicle_object_lethal;
                    break;
            }
        } else if (severity == Accident.SEVERITY_SEVERE) {
            switch (innerType) {
                case Accident.ACCIDENT_TYPE_CAR_TO_PEDESTRIAN:
                    icon = R.drawable.vehicle_person_severe;
                    break;
                case Accident.ACCIDENT_TYPE_CAR_TO_CAR:
                    icon = R.drawable.vehicle_vehicle_severe;
                    break;
                case Accident.ACCIDENT_TYPE_CAR_TO_OBJECT:
                    icon = R.drawable.vehicle_object_severe;
                    break;
            }
        } else if (severity == Accident.SEVERITY_LIGHT) {
            switch (innerType) {
                case Accident.ACCIDENT_TYPE_CAR_TO_PEDESTRIAN:
                    icon = R.drawable.vehicle_person_medium;
                    break;
                case Accident.ACCIDENT_TYPE_CAR_TO_CAR:
                    icon = R.drawable.vehicle_vehicle_medium;
                    break;
                case Accident.ACCIDENT_TYPE_CAR_TO_OBJECT:
                    icon = R.drawable.vehicle_object_medium;
                    break;
            }
        }

        return icon;
    }

    /**
     * Build URL to anyway map from current view for sharing
     *
     * @return the URL as String
     */
    public static String getCurrentPositionStringURI(LatLng location, int zoomLevel, Context context) {

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Boolean show_fatal = sharedPrefs.getBoolean(context.getString(R.string.pref_accidents_fatal_key), true);
        Boolean show_severe = sharedPrefs.getBoolean(context.getString(R.string.pref_accidents_severe_key), true);
        Boolean show_light = sharedPrefs.getBoolean(context.getString(R.string.pref_accidents_light_key), true);
        Boolean show_inaccurate = sharedPrefs.getBoolean(context.getString(R.string.pref_accidents_inaccurate_key), false);
        String fromDate = sharedPrefs.getString(context.getString(R.string.pref_from_date_key), context.getString(R.string.pref_default_from_date));
        String toDate = sharedPrefs.getString(context.getString(R.string.pref_to_date_key), context.getString(R.string.pref_default_to_date));

        // re format the dates to yyyy-MM-dd for the url sharing
        fromDate = DatePreference.getYear(fromDate) + "-" + DatePreference.getMonth(fromDate) + "-" + DatePreference.getDate(fromDate);
        toDate = DatePreference.getYear(toDate) + "-" + DatePreference.getMonth(toDate) + "-" + DatePreference.getDate(toDate);

        Uri builtUri = Uri.parse(AnywayRequestQueue.ANYWAY_BASE_URL).buildUpon()
                .appendQueryParameter("start_date", fromDate)
                .appendQueryParameter("end_date", toDate)
                .appendQueryParameter("show_fatal", show_fatal ? "1" : "0")
                .appendQueryParameter("show_severe", show_severe ? "1" : "0")
                .appendQueryParameter("show_light", show_light ? "1" : "0")
                .appendQueryParameter("show_inaccurate", show_inaccurate ? "1" : "0")
                .appendQueryParameter("zoom", Integer.toString(zoomLevel))
                .appendQueryParameter("lat", Double.toString(location.latitude))
                .appendQueryParameter("lon", Double.toString(location.longitude))
                .build();


        return builtUri.toString();
    }

    public static boolean isNetworkConnectionAvailable(Context context) {

        NetworkInfo info = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE))
                .getActiveNetworkInfo();

        return info != null && info.isConnected();
    }

}

