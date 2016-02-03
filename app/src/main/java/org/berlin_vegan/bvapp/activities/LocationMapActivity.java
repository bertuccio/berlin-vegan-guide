package org.berlin_vegan.bvapp.activities;

import org.berlin_vegan.bvapp.data.Location;
import org.berlin_vegan.bvapp.data.Locations;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.ResourceProxyImpl;
import org.osmdroid.views.MapView;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;

import org.berlin_vegan.bvapp.R;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedIconOverlay.OnItemGestureListener;
import org.osmdroid.views.overlay.OverlayItem;

import java.util.ArrayList;

/**
 * Created by micu on 02/02/16.
 */

public class LocationMapActivity extends BaseActivity {

    protected MapView mMapView;
    protected ResourceProxy mResourceProxy;

    protected ItemizedIconOverlay mLocationOverlay;
    protected ArrayList<LocationOverlayItem> mOverlayItemList;

    // inner class seems HACKy here ....
    class LocationOverlayItem extends OverlayItem {
        private Location mCorrespondingLocation;

        public LocationOverlayItem(final String aTitle, final String aSnippet, final IGeoPoint aGeoPoint, Location correspondingLocation) {
            super(aTitle, aSnippet, aGeoPoint);

            mCorrespondingLocation = correspondingLocation;
        }

        public Location getCorrespondingLocation() {
            return mCorrespondingLocation;
        }
    }



    @Override public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.location_map_activity);

        mMapView = (MapView) findViewById(R.id.map);

        mMapView.getController().setInvertedTiles(false);

        mMapView.setTileSource(TileSourceFactory.MAPNIK);
        mMapView.setMultiTouchControls(true);
        mMapView.setTilesScaledToDpi(true);

        mOverlayItemList = new ArrayList<LocationOverlayItem>();

        // inner class seems HACKy here ....
        OnItemGestureListener<LocationOverlayItem> myOnItemGestureListener
                = new OnItemGestureListener<LocationOverlayItem>() {

            @Override
            public boolean onItemLongPress(int arg0, LocationOverlayItem arg1) {
                // TODO
                return false;
            }

            @Override
            public boolean onItemSingleTapUp(int index, LocationOverlayItem item) {
                final Intent intent = new Intent(getBaseContext(), LocationDetailActivity.class);
                intent.putExtra(LocationDetailActivity.EXTRA_LOCATION, item.getCorrespondingLocation());
                startActivity(intent);
                return true;
            }
        };

        mLocationOverlay = new ItemizedIconOverlay(this, mOverlayItemList, myOnItemGestureListener);
        mMapView.getOverlays().add(mLocationOverlay);
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        Toolbar toolbar = getToolbar();

        if (toolbar != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        IMapController mapController = mMapView.getController();
        mapController.setZoom(10);

        // set Center of the map to Alex
        GeoPoint gPoint = new GeoPoint(52.521918, 13.413215);
        mapController.setCenter(gPoint);

        Locations locations = LocationListActivity.getLocations();

        for (int i = 0; i < locations.size(); i++)
        {
            Location location = locations.get(i);
            gPoint = new GeoPoint(location.getLatCoord(), location.getLongCoord());
            OverlayItem mMarkerItem = new LocationOverlayItem(location.getName(), location.getVegan().toString(), gPoint,location);

//            // Change icon of marker
//            Drawable marker = getResources().getDrawable(R.mipmap.ic_place_white_24dp);
//            marker.setColorFilter(getResources().getColor(R.color.theme_primary), PorterDuff.Mode.SRC_ATOP);
//            mMarkerItem.setMarker(marker);

            mLocationOverlay.addItem(mMarkerItem);

        }

    }

}
