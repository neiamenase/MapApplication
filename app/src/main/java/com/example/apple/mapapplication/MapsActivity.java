package com.example.apple.mapapplication;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private List<Marker> markers;

    private List<Polyline> trackingPolylines;

    private TextView mTextViewTravelMode;
    private TextView mTextViewSetDest;
    private TextView mTextViewTime;
    private TextView mTextViewDistance;
    private TextView mTextViewCurrentStep;

    private String destination = "";
    private int currentStep;
    private double distance;

    private boolean tracking = false;

    private FusedLocationProviderClient mFusedLocationProviderClient;
    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private boolean mLocationPermissionGranted;
    private Location mLastKnownLocation;
    private EditText mEditText_Destination;
    private ImageView mImageView;
    private TextView mTextView;
    private Context mContext;


    public String getSearchQuery(){
        String filter = mEditText_Destination.getText().toString();
        if(!filter.equals("") && mLastKnownLocation!=null) {
            filter = filter.replaceAll("\\ +", "+");
            String key = getResources().getString(R.string.google_maps_key);
            String searchQuery = "https://maps.googleapis.com/maps/api/directions/json?origin=" + mLastKnownLocation.getLatitude() + "," +
                    mLastKnownLocation.getLongitude() + "&destination=" + filter + "&key=" + key;
            return searchQuery;
        }
        return "";
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        markers = new ArrayList<Marker>();
        trackingPolylines = new ArrayList<Polyline>();

        mContext = this.getApplicationContext();
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        mTextView = (TextView) findViewById(R.id.textView);
        mTextView.setTextColor(Color.RED);
        mTextViewSetDest = (TextView) findViewById(R.id.textViewSetDest);
        mTextViewTravelMode = (TextView) findViewById(R.id.textViewTravelMode);
        mTextViewDistance = (TextView) findViewById(R.id.textViewDistance);
        mTextViewTime = (TextView) findViewById(R.id.textViewExpectedTime);
        mTextViewCurrentStep = (TextView) findViewById(R.id.textViewCurrentStep);

        mImageView = (ImageView) findViewById(R.id.imageView);
        mImageView.setImageResource(R.drawable.warning);
        mImageView.setVisibility(View.INVISIBLE);

        mEditText_Destination = (EditText) findViewById(R.id.editText);

        currentStep = -1;
        distance = 0;
        Button mButton = (Button) findViewById(R.id.button);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String searchQuery = getSearchQuery();
                if (!searchQuery.equals("")) {
                    View _view = MapsActivity.this.getCurrentFocus();
                    if (_view != null) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(_view.getWindowToken(), 0);
                    }
                    findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
                    new RetrieveTask(searchQuery).execute();
                }
            }
        });
        Button mLeft = (Button) findViewById(R.id.button_last);
//        mLeft.setImageResource(R.drawable.left);
//        mLeft.setBackground(null);
        mLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentStep > -1) {
                    Marker marker;
                    marker = markers.get(currentStep);
                    zoomBest(marker.getPosition());
                    marker.showInfoWindow();
                }
            }
        });
        Button mRight = (Button) findViewById(R.id.button_next);
//        mRight.setImageResource(R.drawable.right);
//        mRight.setBackground(null);
        mRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentStep > -1) {
                    Marker marker;
                    if (currentStep == markers.size() - 1) {
                        marker = markers.get(currentStep);
                    } else {
                        marker = markers.get(currentStep + 1);
                    }
                    zoomBest(marker.getPosition());
                    marker.showInfoWindow();
                }
            }
        });
        findViewById(R.id.loadingPanel).setVisibility(View.INVISIBLE);
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                getLocationPermission();
                try {
                    if (mLocationPermissionGranted) {
                        for (int y =0 ; y < trackingPolylines.size(); y++) {
                            trackingPolylines.get(y).remove();
                        }
                        trackingPolylines.clear();
                        findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);

                        Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                        locationResult.addOnCompleteListener(MapsActivity.this, new OnCompleteListener<Location>() {
                            @Override
                            public void onComplete(@NonNull Task<Location> task) {
                                if (task.isSuccessful()) {
                                    // Set the map's camera position to the current location of the device.
                                    mLastKnownLocation = task.getResult();
                                    mMap.setMyLocationEnabled(true);
                                    if (mLastKnownLocation == null) {
                                        mTextView.setText(R.string.getLastLocationError);
                                        return;
                                    }
                                    mTextViewSetDest.setText(getResources().getString(R.string.setDest) + destination);
                                    new TrackTask(getSearchQuery()).execute();
                                }
                            }
                        });
                    }
                } catch (SecurityException ex) {

                }

            }
        };

        Button track = (Button) findViewById(R.id.track);
        track.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Handler trackNow = new Handler();
                if (!(destination.equals("")) && currentStep > -1) {
                    tracking = true;
                    trackNow.post(task);
                }
            }
        });

    }

    public void zoomBest(LatLng start){
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(start);
        LatLngBounds bounds = builder.build();
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
    }
    public void zoomBest(LatLng start, LatLng end){
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(start);
        builder.include(end);
        LatLngBounds bounds = builder.build();
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        getLocationPermission();
        try {
            if (mLocationPermissionGranted) {
                Task<Location> locationResult = mFusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.
                            mLastKnownLocation = task.getResult();
                            mMap.setMyLocationEnabled(true);
                            if(mLastKnownLocation == null) {
                                mTextView.setText(R.string.getLastLocationError);
                                return;
                            }
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(mLastKnownLocation.getLatitude(),
                                            mLastKnownLocation.getLongitude()), 15));
                        } else {
                            LatLng sydney = new LatLng(-34, 151);
                            mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney (Default Location)"));
                            mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
                            mTextView.setText(R.string.getLastLocationError);
                        }
                    }
                });
            }
        }catch(SecurityException ex) {

        }
    }

    private void getLocationPermission() { // get permission
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    private List<LatLng> decodePoly(String encoded) {

        List<LatLng> poly = new ArrayList<LatLng>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),(((double) lng / 1E5)));
            //LatLng p = new LatLng((int) (((double) lat / 1E5) * 1E6), (int) (((double) lng / 1E5) * 1E6));
            poly.add(p);
        }

        return poly;
    }

    public String getJsonFromURl(String searchQuery){
        try {
            URL url = new URL(searchQuery);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.connect();
            BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String json = "", line;
            while ((line = rd.readLine()) != null) {
                json += line + "\n";
            }
            connection.disconnect();
            return json;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    class RetrieveTask extends AsyncTask<String, Void, Void> {

        public RetrieveTask(String url) {
            super();
            this.url = url;
        }

        public String url;
        public String json;

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            try {
                JSONObject json = new JSONObject(this.json);
                mMap.clear();
                currentStep = -1;
                destination = "";
                distance = 0;
                markers.clear();
                trackingPolylines.clear();
                if (json.getString("status").equals("NOT_FOUND") || json.getString("status").equals("ZERO_RESULTS")) {
                    mImageView.setVisibility(View.VISIBLE);
                    mTextView.setText(R.string.placeNotFound);
                    findViewById(R.id.loadingPanel).setVisibility(View.INVISIBLE);
                    return;
                } else if (json.getString("status").equals("OK")) {
                    mImageView.setVisibility(View.INVISIBLE);
                    mTextView.setText(R.string.placeFound);
                }
                mTextViewTravelMode.setText(getResources().getString(R.string.travelMode));
                JSONArray children = (JSONArray) json.get("routes");
                if (children.length() > 0) {
                    JSONObject child = (JSONObject) (children.getJSONObject(0));
                    JSONArray legs = (JSONArray) (child.get("legs"));
                    if (legs.length() > 0) {
                        JSONObject leg = (JSONObject) (legs.getJSONObject(0));
                        JSONObject end_location;
                        JSONObject distances = leg.getJSONObject("distance");
                        JSONObject durations = leg.getJSONObject("duration");

                        destination = leg.getString("end_address");
                        mTextViewSetDest.setText(getResources().getString(R.string.setDest) + destination);
                        currentStep = 0;

                        distance = distances.getDouble("value");
                        mTextViewDistance.setText(getResources().getString(R.string.Distance) + distances.getString("text"));
                        mTextViewTime.setText(getResources().getString(R.string.expectedTime) + durations.getString("text"));

                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        LatLng l;
                        MarkerOptions temp;
                        JSONArray steps = (JSONArray) leg.get("steps");
                        String travel_mode = "";

                        for (int i = 0; i < steps.length(); i++) {
                            JSONObject polylineJson = steps.getJSONObject(i).getJSONObject("polyline");
                            String points = polylineJson.getString("points");
                            mMap.addPolyline(new PolylineOptions().addAll(decodePoly(points)).color(Color.RED));

                            JSONObject start_location = steps.getJSONObject(i).getJSONObject("start_location");
                            l = new LatLng(start_location.getDouble("lat"), start_location.getDouble("lng"));
                            temp = new MarkerOptions().position(l).title(
                                    "Step " + (i+1) + " /" + (steps.length() + 1)).snippet("Instruction: " + Html.fromHtml(steps.getJSONObject(i).getString("html_instructions")));
                            markers.add(mMap.addMarker(temp));
                            builder.include(l);
                            if (i == steps.length() - 1) {
                                end_location = steps.getJSONObject(i).getJSONObject("end_location");
                                l = new LatLng(end_location.getDouble("lat"),
                                        end_location.getDouble("lng"));
                                temp = new MarkerOptions().position(l).title(destination).snippet("Destination");
                                markers.add(mMap.addMarker(temp));
                            }
                            travel_mode = steps.getJSONObject(i).getString("travel_mode");
                            builder.include(l);
                        }
                        mTextViewTravelMode.setText(getResources().getString(R.string.travelMode) + " " + travel_mode);
                        mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
                            @Override
                            public View getInfoWindow(Marker arg0) {
                                return null;
                            }

                            @Override
                            public View getInfoContents(Marker marker) {

                                LinearLayout info = new LinearLayout(mContext);
                                info.setOrientation(LinearLayout.VERTICAL);

                                TextView title = new TextView(mContext);
                                title.setTextColor(Color.BLACK);
                                title.setGravity(Gravity.CENTER);
                                title.setTypeface(null, Typeface.BOLD);
                                title.setText(marker.getTitle());

                                TextView snippet = new TextView(mContext);
                                snippet.setTextColor(Color.GRAY);
                                snippet.setText(marker.getSnippet());

                                info.addView(title);
                                info.addView(snippet);

                                return info;
                            }
                        });
                        LatLngBounds bounds = builder.build();
                        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50));
                    }
                } else {
                    mTextView.setText(R.string.noRouteFound);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception ex) {
                ex.printStackTrace();
                mTextView.setText(R.string.getLastLocationError);
            }
            findViewById(R.id.loadingPanel).setVisibility(View.INVISIBLE);
        }

        @Override
        protected Void doInBackground(String... strings) {
            this.json = getJsonFromURl(this.url);
            return null;
        }


    }

    private double distance(double lat1, double lng1, double lat2, double lng2) {

        double earthRadius = 3958.75; // in miles, change to 6371 for kilometer output

        double dLat = Math.toRadians(lat2-lat1);
        double dLng = Math.toRadians(lng2-lng1);

        double sindLat = Math.sin(dLat / 2);
        double sindLng = Math.sin(dLng / 2);

        double a = Math.pow(sindLat, 2) + Math.pow(sindLng, 2)
                * Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2));

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        double dist = earthRadius * c;

        return dist; // output distance, in MILES
    }

    class TrackTask extends AsyncTask<String, Void, Void> {

        public TrackTask(String url) {
            super();
            this.url = url;
        }

        public String url;
        public String json;

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            try {
                if (json.equals("")) return;
                JSONObject jsonObject = new JSONObject(json);
                JSONArray children = (JSONArray) jsonObject.get("routes");
                if (children.length() > 0) {
                    JSONObject child = (JSONObject) (children.getJSONObject(0));
                    JSONArray legs = (JSONArray) (child.get("legs"));
                    if (legs.length() > 0) {
                        JSONObject leg = (JSONObject) (legs.getJSONObject(0));
                        currentStep = 0;
                        JSONArray steps = (JSONArray) leg.get("steps");
                        LatLng l1;
                        int j = markers.size()-1;   // route b4
                        int i = steps.length() - 1; // new route

                        JSONObject distances = leg.getJSONObject("distance");
                        JSONObject durations = leg.getJSONObject("duration");
                        double thisDistance = distances.getDouble("value");

                        mTextViewDistance.setText(getResources().getString(R.string.Distance) + distances.getString("text"));
                        mTextViewTime.setText(getResources().getString(R.string.expectedTime) + durations.getString("text"));

                        //distance
                        if(thisDistance > distance){ // distance, should but be marker
                            mTextView.setText("you are now even farther from the destination than your starting point");
                        }

                        for (; i >= 0 && j>=0; i--, j--) {
                            if(j == markers.size()-1) {
                                // get end location of the old route
                                JSONObject end_location = steps.getJSONObject(i).getJSONObject("end_location");
                                LatLng l2 = new LatLng(end_location.getDouble("lat"),
                                        end_location.getDouble("lng"));
                                l1 = markers.get(j).getPosition();

                                if(distance(l2.latitude, l2.longitude, l1.latitude, l1.longitude) >= 0.1){
                                    currentStep = -1;
                                    mTextViewSetDest.setText(getResources().getString(R.string.setDest));
                                    destination = "";
                                    tracking = false;
                                    mTextView.setText("Different destination on tracking, Please restart the process by planning again.");
                                    mTextViewCurrentStep.setText(getResources().getString(R.string.currentStepStr0));
                                    mMap.clear();
                                    findViewById(R.id.loadingPanel).setVisibility(View.INVISIBLE);
                                    return;
                                }
                                j--;
                            }

                            l1 = markers.get(j).getPosition();
                            JSONObject start_location = steps.getJSONObject(i).getJSONObject("start_location");
                            LatLng l2 = new LatLng(start_location.getDouble("lat"),
                                    start_location.getDouble("lng"));
                            if(distance(l2.latitude, l2.longitude, l1.latitude, l1.longitude) > 0.1) {
                                mTextView.setText("You are not on the suggested path. Blue line is how you can go back to step " + ( j + 1 ));
                                for (int t = 0; t < j; t++) {
                                    String points = steps.getJSONObject(i).getJSONObject("polyline").getString("points");
                                    trackingPolylines.add(mMap.addPolyline(new PolylineOptions().addAll(decodePoly(points)).color(Color.BLUE)));
                                    currentStep = j + 1;// we minus in for-loop
                                }
                                findViewById(R.id.loadingPanel).setVisibility(View.INVISIBLE);
                                return;
                            }
                        }


                        if(j == 0 && i > 0 ) {
                            mTextView.setText("You are far from the suggested path. Blue line is how you can go back to the starting point");
                            for (int t = 0; t <= i; t++) {
                                String points = steps.getJSONObject(i).getJSONObject("polyline").getString("points");
                                trackingPolylines.add(mMap.addPolyline(new PolylineOptions().addAll(decodePoly(points)).color(Color.BLUE)));
                                currentStep = 0;
                            }

                        }else{
                            currentStep = j + 1;// we minus in for-loop
                            mTextViewCurrentStep.setText(getResources().getString(R.string.currentStepStr) + (currentStep + 1) +  "/ "+ markers.size());
                            mTextView.setText("You are on the suggested path. Current Step: " + (currentStep + 1));
                            if (currentStep < markers.size() -1 ) {
                                zoomBest(markers.get(currentStep).getPosition(), markers.get(currentStep + 1).getPosition());
                            }else {
                                zoomBest(markers.get(currentStep).getPosition());
                            }
                        }

                        findViewById(R.id.loadingPanel).setVisibility(View.INVISIBLE);

                    }
                } else {
                    mTextView.setText(R.string.noRouteFound);
                }


            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(String... strings) {
            this.json = getJsonFromURl(this.url);
            return null;
        }


    }
}
