package com.example.android.gymkhagymkha.fragments;

import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.gymkhagymkha.R;
import com.example.android.gymkhagymkha.bbdd.BDManager;
import com.example.android.gymkhagymkha.classes.Clase_Tesoro;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;


public class Fragment_Mapa extends android.support.v4.app.Fragment implements OnMapReadyCallback,
        LocationListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    protected static final String TAG = "location-updates";

    /**
     * The desired interval for location updates. Inexact. Updates may be more or less frequent.
     */
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;

    /**
     * The fastest rate for active location updates. Exact. Updates will never be more frequent
     * than this value.
     */
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    // Keys for storing activity state in the Bundle.
    protected final static String REQUESTING_LOCATION_UPDATES_KEY = "requesting-location-updates-key";
    protected final static String LOCATION_KEY = "location-key";
    protected final static String LAST_UPDATED_TIME_STRING_KEY = "last-updated-time-string-key";

    /**
     * Provides the entry point to Google Play services.
     */
    protected GoogleApiClient mGoogleApiClient;

    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    protected LocationRequest mLocationRequest;

    /**
     * Represents a geographical location.
     */
    protected Location mCurrentLocation;

    // UI Widgets.
    /*
    protected Button mStartUpdatesButton;
    protected Button mStopUpdatesButton;
    protected TextView mLastUpdateTimeTextView;
    protected TextView mLatitudeTextView;
    protected TextView mLongitudeTextView;
    */

    // Labels.
    /*
    protected String mLatitudeLabel;
    protected String mLongitudeLabel;
    protected String mLastUpdateTimeLabel;
    */

    /**
     * Tracks the status of the location updates request. Value changes when the user presses the
     * Start Updates and Stop Updates buttons.
     */
    protected Boolean mRequestingLocationUpdates;

    /**
     * Time when the location was updated represented as a String.
     */
    protected String mLastUpdateTime;

    private GoogleMap mMap;
    private BDManager manager;
    private Cursor cursorTesoros;
    private double latitud,longitud;
    private String pista,resul;
    private ArrayList<Clase_Tesoro> arrayTesoros;
    private Toolbar toolbarInGame;
    private TextView tvPista;
    Location myLocation;

    @Override public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mapa, container, false);
        setUpMapIfNeeded();

        return view;
    }


    public void onViewCreated(View view, Bundle savedInstanceState){
        super.onViewCreated(view, savedInstanceState);



        mRequestingLocationUpdates = false;
        mLastUpdateTime = "";

        // Update values using data stored in the Bundle.
        updateValuesFromBundle(savedInstanceState);

        // Kick off the process of building a GoogleApiClient and requesting the LocationServices
        // API.
        buildGoogleApiClient();


        //TODO falta poner el stop

        manager = new BDManager(getActivity());
        arrayTesoros = new ArrayList<Clase_Tesoro>();
        int idEvento = this.getActivity().getIntent().getExtras().getInt("idEvento");
        new AsyncTesoros().execute("http://www.victordam2b.hol.es/tesorosAcceso.php?idEvento=" + idEvento);
    }

    /*
     * Updates fields based on data stored in the bundle.
     *
     * @param savedInstanceState The activity state saved in the Bundle.
     */
    private void updateValuesFromBundle(Bundle savedInstanceState) {
        Log.i(TAG, "Updating values from bundle");
        if (savedInstanceState != null) {
            // Update the value of mRequestingLocationUpdates from the Bundle, and make sure that
            // the Start Updates and Stop Updates buttons are correctly enabled or disabled.
            if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                        REQUESTING_LOCATION_UPDATES_KEY);
            }

            // Update the value of mCurrentLocation from the Bundle and update the UI to show the
            // correct latitude and longitude.
            if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
                // Since LOCATION_KEY was found in the Bundle, we can be sure that mCurrentLocation
                // is not null.
                mCurrentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
            }

            // Update the value of mLastUpdateTime from the Bundle and update the UI.
            if (savedInstanceState.keySet().contains(LAST_UPDATED_TIME_STRING_KEY)) {
                mLastUpdateTime = savedInstanceState.getString(LAST_UPDATED_TIME_STRING_KEY);
            }
            updateUI();
        }
    }

    /**
     * Builds a GoogleApiClient. Uses the {@code #addApi} method to request the
     * LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        Log.i(TAG, "Building GoogleApiClient");
        mGoogleApiClient = new GoogleApiClient.Builder(getActivity())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();
    }

    /**
     * Sets up the location request. Android has two location request settings:
     * {@code ACCESS_COARSE_LOCATION} and {@code ACCESS_FINE_LOCATION}. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     * <p/>
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     * <p/>
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();

        // Sets the desired interval for active location updates. This interval is
        // inexact. You may not receive updates at all if no location sources are available, or
        // you may receive them slower than requested. You may also receive updates faster than
        // requested if other applications are requesting location at a faster interval.
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);

        // Sets the fastest rate for active location updates. This interval is exact, and your
        // application will never receive updates faster than this value.
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);

        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * Requests location updates from the FusedLocationApi.
     */
    protected void startLocationUpdates() {
        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient, mLocationRequest, this);
    }

    /**
     * Updates the latitude, the longitude, and the last location time in the UI.
     */
    private void updateUI() {
        String mensaje = "Cambiando location Latitud:" + mCurrentLocation.getLatitude() + " Longitud:" + mCurrentLocation.getLongitude();
        ((TextView) getActivity().findViewById(R.id.tvPista)).setText(mensaje);
        Toast.makeText(getActivity(), "Cambiando location Latitud:" + mCurrentLocation.getLatitude() + " Longitud:" + mCurrentLocation.getLongitude(), Toast.LENGTH_LONG).show();

    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    protected void stopLocationUpdates() {
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.

        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    @Override
    public void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Within {@code onPause()}, we pause location updates, but leave the
        // connection to GoogleApiClient intact.  Here, we resume receiving
        // location updates if the user has requested them.

        if (mGoogleApiClient.isConnected() && mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop location updates to save battery, but don't disconnect the GoogleApiClient object.
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
        }
    }

    @Override
    public void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Connected to GoogleApiClient");
        // If the initial location was never previously requested, we use
        // FusedLocationApi.getLastLocation() to get it. If it was previously requested, we store
        // its value in the Bundle and check for it in onCreate(). We
        // do not request it again unless the user specifically requests location updates by pressing
        // the Start Updates button.
        //
        // Because we cache the value of the initial location in the Bundle, it means that if the
        // user launches the activity,
        // moves to a new location, and then changes the device orientation, the original location
        // is displayed as the activity is re-created.
        if (mCurrentLocation == null) {
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
            updateUI();
        }

        // If the user presses the Start Updates button before GoogleApiClient connects, we set
        // mRequestingLocationUpdates to true (see startUpdatesButtonHandler()). Here, we check
        // the value of mRequestingLocationUpdates and if it is true, we start location updates.
        if (mRequestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    @Override
    public void onLocationChanged(Location location) {

        String mensaje = "Cambiando location Latitud:" + location.getLatitude() + " Longitud:" + location.getLongitude();
        ((TextView) getActivity().findViewById(R.id.tvPista)).setText(mensaje);
        Toast.makeText(getActivity(), "Cambiando location Latitud:" + location.getLatitude() + " Longitud:" + location.getLongitude(), Toast.LENGTH_LONG).show();

        mCurrentLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        updateUI();
        Toast.makeText(getActivity(), "La location ha updateado",Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());
    }

    /*
    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
    */

    private void setUpMapIfNeeded() {
        if (mMap == null) {
            SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap() {
        mMap.addMarker(new MarkerOptions().position(new LatLng(40.3836, -3.7319)).title("Wiiiii!!!"));
    }


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        mMap = map;
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);
        //mMap.getUiSettings().setAllGesturesEnabled(false);

    }

    public Location getMyLocation() {
        return myLocation;
    }

    public void setMyLocation(Location myLocation) {
        this.myLocation = myLocation;
    }


    public class AsyncTesoros extends AsyncTask<String, Void, StringBuilder> {

        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            super.onPreExecute();
            //progressBarEventos.setVisibility(View.VISIBLE);
        }

        @Override
        protected StringBuilder doInBackground(String... _url) {
            HttpURLConnection urlConnection = null;
            StringBuilder sb = new StringBuilder();
            String linea;
            resul = "";
            try {
                URL url = new URL(_url[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                InputStream is = new BufferedInputStream(urlConnection.getInputStream());
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                while ((linea = br.readLine()) != null) {
                    resul = resul + linea;
                }
            } catch (MalformedURLException e) {
                Log.e("TESTNET", "URL MAL FORMADA");

            } catch (IOException e) {
                Log.e("TESTNET", "IO ERROR");
            } finally {
                urlConnection.disconnect();
            }
            return sb;
        }

        protected void onPostExecute(StringBuilder sb) {
            if (resul.compareTo("-1") == 0 && resul.compareTo("-2") == 0 && resul.compareTo("-3") == 0 && resul.compareTo("-4") == 0) {
                Log.i("Tesoros", "no encontrados");
            } else {
                JSONObject resultadoJSON;
                Clase_Tesoro auxTesoro;
                try {
                    resultadoJSON = new JSONObject(resul);
                    for (int i = 0; i < resultadoJSON.length(); i++) {
                        auxTesoro = new Clase_Tesoro(resultadoJSON.getJSONObject(i + ""));
                        //Clase_Tesoro auxAux = new Clase_Tesoro(auxTesoro.getIdTesoro(),auxTesoro.getNombre(),auxTesoro.getPista(),auxTesoro.getEstado(),auxTesoro.getLatitud(),auxTesoro.getLongitud());
                        //manager.guardarEvento(evento.getIdEvento(), evento.getDescripcion(),evento.getNombre());
                        arrayTesoros.add(auxTesoro);
                        manager.guardarTesoro(auxTesoro);
                    }

                    toolbarInGame = (Toolbar) getActivity().findViewById(R.id.toolbarInGame);
                    //setSupportActionBar(toolbarInGame);
                    toolbarInGame.setTitle(arrayTesoros.get(0).getNombre());
                    String auxPista = arrayTesoros.get(0).getPista();
                    tvPista = (TextView) getActivity().findViewById(R.id.tvPista);
                    tvPista.setText(auxPista);

                    SharedPreferences prefs = getActivity().getSharedPreferences("preferenciasGymkha", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("pista", auxPista);
                    editor.commit();

                    cursorTesoros = manager.cursorTesoros();
                    cursorTesoros.moveToFirst();
                    latitud = cursorTesoros.getDouble(cursorTesoros.getColumnIndex(manager.CN_TREASURE_LATITUDE));
                    longitud = cursorTesoros.getDouble(cursorTesoros.getColumnIndex(manager.CN_TREASURE_LONGITUDE));
                    pista = cursorTesoros.getString(cursorTesoros.getColumnIndex(manager.CN_TREASURE_CLUE));

                    mMap.addMarker(new MarkerOptions().position(new LatLng(latitud, longitud)).title(pista));
                    //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitud, longitud), 16));
                    mMap.addCircle(new CircleOptions()
                            .center(new LatLng(latitud, longitud))
                            .radius(250)
                            .strokeColor(Color.RED)
                            .fillColor(Color.TRANSPARENT));
                    setMyLocation(mMap.getMyLocation());
                    CameraPosition position = new CameraPosition.Builder()
                            .target(new LatLng(latitud, longitud))
                            //.target(new LatLng(mMap.getMyLocation().getLatitude(), mMap.getMyLocation().getLongitude()))
                            .zoom(16).build();

                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(position));

                    if(!mGoogleApiClient.isConnecting()){
                        if (!mRequestingLocationUpdates) {
                            mRequestingLocationUpdates = true;
                            startLocationUpdates();
                        }
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
