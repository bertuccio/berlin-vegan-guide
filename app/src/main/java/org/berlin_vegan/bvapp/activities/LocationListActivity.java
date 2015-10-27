package org.berlin_vegan.bvapp.activities;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.berlin_vegan.bvapp.MainApplication;
import org.berlin_vegan.bvapp.R;
import org.berlin_vegan.bvapp.adapters.LocationAdapter;
import org.berlin_vegan.bvapp.data.GastroLocation;
import org.berlin_vegan.bvapp.data.Location;
import org.berlin_vegan.bvapp.data.Locations;
import org.berlin_vegan.bvapp.data.Preferences;
import org.berlin_vegan.bvapp.data.ShoppingLocation;
import org.berlin_vegan.bvapp.helpers.DividerItemDecoration;
import org.berlin_vegan.bvapp.helpers.GastroLocationFilterCallback;
import org.berlin_vegan.bvapp.helpers.UiUtils;
import org.berlin_vegan.bvapp.listeners.CustomLocationListener;
import org.berlin_vegan.bvapp.views.GastroFilterView;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point of the program.
 */
public class LocationListActivity extends BaseActivity {

    private static final String TAG = "LocationListActivity";

    private static final String JSON_BASE_URL = "http://www.berlin-vegan.de/app/data/";
    private static final String GASTRO_LOCATIONS_JSON = "GastroLocations.json";
    private static final String SHOPPING_LOCATIONS_JSON = "ShoppingLocations.json";

    private static final String HTTP_GASTRO_LOCATIONS_JSON = JSON_BASE_URL + GASTRO_LOCATIONS_JSON;
    private static final String HTTP_SHOPPING_LOCATIONS_JSON = JSON_BASE_URL + SHOPPING_LOCATIONS_JSON;

    enum LOCATION_VIEW_MODE {GASTRO, SHOPPING, FAVORITE}

    private Context mContext;
    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private LocationAdapter mLocationAdapter;
    private LocationManager mLocationManager;
    private CustomLocationListener mLocationListener;
    // the GPS/Network Location
    private android.location.Location mGpsLocationFound;
    private Dialog mProgressDialog;
    private SharedPreferences mSharedPreferences;
    private Locations mLocations;
    private LOCATION_VIEW_MODE mViewMode = LOCATION_VIEW_MODE.GASTRO;

    private final GastroLocationFilterCallback mButtonCallback = new GastroLocationFilterCallback(this);

    //NavDrawer
    private DrawerLayout mDrawer;
    private NavigationView nvDrawer;

    // --------------------------------------------------------------------
    // life cycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.location_list_activity);
        setTitle(getString(R.string.app_name));

        mContext = this;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.location_list_activity_swipe_refresh_layout);
        if (mSwipeRefreshLayout != null) {
            setupSwipeRefresh();
        }

        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);

        // start a thread to retrieve the json from the server and to wait for the geo location
        RetrieveLocations retrieveLocations = new RetrieveLocations(this);
        retrieveLocations.execute();

        mLocationAdapter = new LocationAdapter(this);
        mLocations = new Locations(this);
        mLocationListener = new CustomLocationListener(this, mLocations);

        mRecyclerView = (RecyclerView) findViewById(R.id.main_list_recycler_view);
        if (mRecyclerView != null) {
            setupRecyclerView(linearLayoutManager);
        }

        //NavDrawer
        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        //find our drawer view
        nvDrawer = (NavigationView) findViewById(R.id.nvView);
        //setup drawer view
        setupDrawerContent(nvDrawer);
    }

    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        selectDrawerItem(menuItem);
                        return true;
                    }
                });
    }

    public void selectDrawerItem(MenuItem menuItem){
        switch (menuItem.getItemId()) {
            case R.id.nav_gastro:
                applyViewMode(LOCATION_VIEW_MODE.GASTRO);
                break;

            case R.id.nav_shopping:
                applyViewMode(LOCATION_VIEW_MODE.SHOPPING);
                break;

            case R.id.nav_fav:
                applyViewMode(LOCATION_VIEW_MODE.FAVORITE);
                break;

            case R.id.nav_pref:
                final Intent settings = new Intent(this, SettingsActivity.class);
                startActivity(settings);
                break;

            case R.id.nav_about:
                if (mContext != null) {
                    UiUtils.showMaterialAboutDialog(mContext, getResources().getString(R.string.action_about));
                }

            default:
                break;
        }

        mDrawer.closeDrawers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestGpsLocationUpdates();
        if (mViewMode == LOCATION_VIEW_MODE.FAVORITE) {
            // update the list, because the user may have added or removed a favorite in {@code GastroActivity}
            mLocations.showFavorites();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        removeGpsLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // --------------------------------------------------------------------
    // menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_location_list_activity, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        menu.clear();

        inflater.inflate(R.menu.menu_location_list_activity, menu);
        MenuItem menuItem = menu.findItem(R.id.menu_search);
        if (mViewMode == LOCATION_VIEW_MODE.GASTRO || mViewMode == LOCATION_VIEW_MODE.SHOPPING) {
            initializeSearch(menuItem);
        } else {
            menuItem.setVisible(false); // hide for favorite
        }

        menuItem = menu.findItem(R.id.action_filter);
        if (mViewMode == LOCATION_VIEW_MODE.FAVORITE || mViewMode == LOCATION_VIEW_MODE.SHOPPING) { // at the moment no filter for shopping and favorite
            menuItem.setVisible(false);
        }
        return true;
    }

    private void initializeSearch(MenuItem searchViewItem) {
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchViewItem);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (mLocations != null) {
                    mLocations.processQueryFilter(query);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return onQueryTextSubmit(newText);
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchViewItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                if (mLocations != null) {
                    mLocations.resetQueryFilter();
                }
                return true;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_filter:
                final GastroFilterView gastroFilterView = new GastroFilterView(LocationListActivity.this);
                gastroFilterView.init(getLocations(), Preferences.getGastroFilter(this));
                UiUtils.showMaterialDialogCustomView(LocationListActivity.this,
                        getString(R.string.gastro_filter_title_dialog),
                        gastroFilterView,
                        mButtonCallback);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle saveInstanceState){
        super.onPostCreate(saveInstanceState);
    }

    private void applyViewMode(LOCATION_VIEW_MODE viewMode) {
        mViewMode = viewMode;
        if (viewMode == LOCATION_VIEW_MODE.FAVORITE) {
            mLocations.showFavorites();
        } else if (viewMode == LOCATION_VIEW_MODE.SHOPPING) {
            mLocations.showShoppingLocations();
        } else {
            mLocations.showGastroLocations();
        }
        mRecyclerView.scrollToPosition(0);
        invalidateOptionsMenu();
    }

    // --------------------------------------------------------------------
    // setups

    private void setupRecyclerView(final LinearLayoutManager linearLayoutManager) {
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(linearLayoutManager);
        mRecyclerView.setAdapter(mLocationAdapter);
        RecyclerView.ItemDecoration itemDecoration =
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST);
        mRecyclerView.addItemDecoration(itemDecoration);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                mSwipeRefreshLayout.setEnabled(linearLayoutManager.findFirstCompletelyVisibleItemPosition() == 0);
            }
        });
        // TODO: fast scroll
    }

    private void setupSwipeRefresh() {
        mSwipeRefreshLayout.setColorSchemeResources(
                R.color.refresh_progress_1,
                R.color.refresh_progress_2,
                R.color.refresh_progress_3);
        // refreshes the gps fix. re-sorts the card view
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // very important for the runnable further below
                mGpsLocationFound = null;
                removeGpsLocationUpdates();
                requestGpsLocationUpdates();
                // runnable to determine when the first GPS fix was received.
                final Runnable waitForGpsFix = new Runnable() {
                    @Override
                    public void run() {
                        waitForGpsFix();
                        mLocations.updateLocationAdapter(mGpsLocationFound);
                    }
                };
                Thread t = new Thread(waitForGpsFix);
                t.start();
            }
        });
    }

    // --------------------------------------------------------------------
    // location handling

    private void requestGpsLocationUpdates() {
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);

        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mLocationManager.requestSingleUpdate(criteria, mLocationListener, null);
    }

    public void removeGpsLocationUpdates() {
        if (mLocationManager != null)
            mLocationManager.removeUpdates(mLocationListener);
    }

    private void waitForGpsFix() {
        final long startTimeMillis = System.currentTimeMillis();
        final int waitTimeMillis = 20 * 1000;
        while (mGpsLocationFound == null) {
            // wait for first GPS fix (do nothing)
            if ((System.currentTimeMillis() - startTimeMillis) > waitTimeMillis) {
                if (!LocationListActivity.this.isFinishing()) {
                    LocationListActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            UiUtils.showMaterialDialog(LocationListActivity.this, getString(R.string.error),
                                    getString(R.string.no_gps_data));
                        }
                    });
                }
                break;
            }
        }
        LocationListActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }
                mSwipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    public Locations getLocations() {
        return mLocations;
    }

    public LocationAdapter getLocationAdapter() {
        return mLocationAdapter;
    }

    public void setLocationFound(android.location.Location locationFound) {
        mGpsLocationFound = locationFound;
    }

    public static List<Location> createList(final InputStream inputStream, Type type) {
        final InputStreamReader reader = new InputStreamReader(inputStream, Charset.defaultCharset());
        return new Gson().fromJson(reader, type);
    }

    private class RetrieveLocations extends AsyncTask<Void, Void, Void> {
        public static final int TIMEOUT_MILLIS = 5 * 1000;
        private final LocationListActivity mLocationListActivity;
        private final Type gastroTokenType = new TypeToken<ArrayList<GastroLocation>>() {
        }.getType();
        private final Type shoppingTokenType = new TypeToken<ArrayList<ShoppingLocation>>() {
        }.getType();

        public RetrieveLocations(LocationListActivity locationListActivity) {
            mLocationListActivity = locationListActivity;
        }

        @Override
        protected void onPreExecute() {
            LocationListActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!mSwipeRefreshLayout.isRefreshing()) {
                        mProgressDialog = UiUtils.showMaterialProgressDialog(mLocationListActivity, getString(R.string.please_wait),
                                getString(R.string.retrieving_data));
                    }
                }
            });
        }


        @Override
        protected void onPostExecute(Void param) {
            applyViewMode(LOCATION_VIEW_MODE.GASTRO); // todo, gastro is default ?
            mLocations.updateLocationAdapter();
        }

        // todo remove duplicated code
        @Override
        protected Void doInBackground(Void... params) {
            List<Location> gastroLocations = getGastroLocationsFromServer();
            if (gastroLocations == null) { // not modified, timeout or parsing problem, so use cached version if available
                gastroLocations = getLocationsFromCache(GASTRO_LOCATIONS_JSON, gastroTokenType);
            }
            if (gastroLocations == null) { // use included json file as fall back
                gastroLocations = getLocationsFromBundle(GASTRO_LOCATIONS_JSON, gastroTokenType);
            }
            Log.d(TAG, "read " + gastroLocations.size() + " entries");

            List<Location> shoppingLocations = getShoppingLocationsFromServer();
            if (shoppingLocations == null) { // not modified, timeout or parsing problem, so use cached version if available
                shoppingLocations = getLocationsFromCache(SHOPPING_LOCATIONS_JSON, shoppingTokenType);
            }
            if (shoppingLocations == null) { // use included json file as fall back
                shoppingLocations = getLocationsFromBundle(SHOPPING_LOCATIONS_JSON, gastroTokenType);
            }
            Log.d(TAG, "read " + shoppingLocations.size() + " entries");

            gastroLocations.addAll(shoppingLocations); // merge both lists

            mLocations.set(gastroLocations);
            waitForGpsFix();
            return null;
        }

        // todo merge with getGastroLocationsFromServer, remove code duplication
        @Nullable
        private List<Location> getShoppingLocationsFromServer() {
            FileOutputStream fileOutputStream = null;
            InputStream inputStream = null;
            List<Location> locations = null;
            try {
                // fetch json file from server
                final URL url = new URL(HTTP_SHOPPING_LOCATIONS_JSON);
                final HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setConnectTimeout(TIMEOUT_MILLIS);
                urlConnection.setReadTimeout(TIMEOUT_MILLIS);
                if (Preferences.getShoppingLastModified(mLocationListActivity) != 0) {
                    urlConnection.setIfModifiedSince(Preferences.getShoppingLastModified(mLocationListActivity));
                }
                if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_NOT_MODIFIED) { // modified, try to parse and if successfully store a cached version
                    inputStream = urlConnection.getInputStream();
                    locations = createList(inputStream, shoppingTokenType);
                    final long lastModified = urlConnection.getLastModified();
                    if (lastModified != 0) { //valid timestamp, store local cache version
                        fileOutputStream = mLocationListActivity.openFileOutput(SHOPPING_LOCATIONS_JSON, Context.MODE_PRIVATE);
                        final String gastroStr = new Gson().toJson(locations);
                        if (!TextUtils.isEmpty(gastroStr)) {
                            fileOutputStream.write(gastroStr.getBytes());
                            fileOutputStream.close();
                            Preferences.saveShoppingLastModified(mLocationListActivity, lastModified);
                        }
                    }
                    Log.i(TAG, "retrieving shopping database from server successful");
                }
            } catch (IOException e) {
                Log.e(TAG, "fetching json file from server failed", e);
            } catch (RuntimeException e) {
                // is thrown if a JsonParseException occurs
                Log.e(TAG, "parsing the json file failed", e);
                locations = null;
            } finally {
                closeStream(inputStream);
                closeStream(fileOutputStream);
            }
            return locations;
        }

        // todo merge with getShoppingLocationsFromServer, remove code duplication
        @Nullable
        private List<Location> getGastroLocationsFromServer() {
            FileOutputStream fileOutputStream = null;
            InputStream inputStream = null;
            List<Location> gastroLocations = null;
            try {
                // fetch json file from server
                final URL url = new URL(HTTP_GASTRO_LOCATIONS_JSON);
                final HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setConnectTimeout(TIMEOUT_MILLIS);
                urlConnection.setReadTimeout(TIMEOUT_MILLIS);
                if (Preferences.getGastroLastModified(mLocationListActivity) != 0) {
                    urlConnection.setIfModifiedSince(Preferences.getGastroLastModified(mLocationListActivity));
                }
                if (urlConnection.getResponseCode() != HttpURLConnection.HTTP_NOT_MODIFIED) { // modified, try to parse and if successfully store a cached version
                    inputStream = urlConnection.getInputStream();
                    gastroLocations = createList(inputStream, gastroTokenType);
                    final long lastModified = urlConnection.getLastModified();
                    if (lastModified != 0) { //valid timestamp, store local cache version
                        fileOutputStream = mLocationListActivity.openFileOutput(GASTRO_LOCATIONS_JSON, Context.MODE_PRIVATE);
                        final String gastroStr = new Gson().toJson(gastroLocations);
                        if (!TextUtils.isEmpty(gastroStr)) {
                            fileOutputStream.write(gastroStr.getBytes());
                            fileOutputStream.close();
                            Preferences.saveGastroLastModified(mLocationListActivity, lastModified);
                        }
                    }
                    Log.i(TAG, "retrieving gastro database from server successful");
                }
            } catch (IOException e) {
                Log.e(TAG, "fetching json file from server failed", e);
            } catch (RuntimeException e) {
                // is thrown if a JsonParseException occurs
                Log.e(TAG, "parsing the json file failed", e);
                gastroLocations = null;
            } finally {
                closeStream(inputStream);
                closeStream(fileOutputStream);
            }
            return gastroLocations;
        }

        @Nullable
        private List<Location> getLocationsFromCache(String fileName, Type tokenType) {
            List<Location> locations;
            FileInputStream fileInputStream = null;
            try { // try cached version
                fileInputStream = mLocationListActivity.openFileInput(fileName);
                locations = createList(fileInputStream, tokenType);
                Log.i(TAG, "use cached version of database file");
            } catch (RuntimeException | IOException e) {
                Log.e(TAG, "parsing the cached json file failed", e);
                locations = null;
            } finally {
                closeStream(fileInputStream);
            }
            return locations;
        }

        private List<Location> getLocationsFromBundle(String locationsJson, Type tokenType) {
            List<Location> locations;
            InputStream inputStream;
            inputStream = MainApplication.class.getResourceAsStream(locationsJson);
            locations = createList(inputStream, tokenType);
            closeStream(inputStream);
            Log.i(TAG, "fall back: use bundled copy of database file");
            return locations;
        }

        private void closeStream(Closeable closeable) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "closing stream failed", e);
            }
        }
    }

}