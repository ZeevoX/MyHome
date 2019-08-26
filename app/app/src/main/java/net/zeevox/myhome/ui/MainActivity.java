package net.zeevox.myhome.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.TextView;

import com.google.gson.Gson;

import net.zeevox.myhome.BuildConfig;
import net.zeevox.myhome.Heater;
import net.zeevox.myhome.R;
import net.zeevox.myhome.Sensor;
import net.zeevox.myhome.Sensors;
import net.zeevox.myhome.json.CustomJsonObject;
import net.zeevox.myhome.json.Methods;
import net.zeevox.myhome.json.Params;
import net.zeevox.myhome.websocket.CustomWebSocketListener;
import net.zeevox.myhome.websocket.WebSocketUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import okhttp3.Response;
import okhttp3.WebSocket;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, Sensors.OnListChangedListener {

    public static final Sensors sensors = new Sensors();
    public static Heater heater;
    public static WebSocketUtils webSocketUtils;
    private final FragmentManager fragmentManager = getSupportFragmentManager();
    private final Gson gson = new Gson();
    private final Handler handler = new Handler();
    private CustomWebSocketListener webSocketListener;
    private NavigationView navigationView;
    private SharedPreferences preferences;
    private SwipeRefreshLayout swipeRefreshLayout;
    private final Runnable refreshData = new Runnable() {
        @Override
        public void run() {
            refreshData();
            handler.postDelayed(this, 5000);
        }
    };
    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    public static int toInt(Object o) {
        Double d = (Double) o;
        return (int) Math.round(d);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        if (preferences.getString(SettingsFragment.HUB_URL, null) == null ||
                preferences.getString(SettingsFragment.DATA_URL, null) == null) {
            startActivity(new Intent(MainActivity.this, SetupActivity.class));
            finish();
        }

        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(getResources().getColor(R.color.colorPrimary));
        }

        setUpViewPager();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.nav_drawer_open, R.string.nav_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        Objects.requireNonNull(getSupportActionBar()).setElevation(0);

        sensors.setOnHeadlineSelectedListener(this);

        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        ((TextView) navigationView.getHeaderView(0).findViewById(R.id.nav_header_app_version)).setText(String.format(getString(R.string.nav_app_version), BuildConfig.VERSION_NAME));

        swipeRefreshLayout = findViewById(R.id.dashboard_swipe_refresh);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            swipeRefreshLayout.setRefreshing(false);
            if (!refreshData()) {
                Snackbar.make(findViewById(android.R.id.content), R.string.error_device_connecting, Snackbar.LENGTH_SHORT).show();
            }
        });
        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorAccent));

        webSocketUtils = new WebSocketUtils();
        webSocketListener = new CustomWebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
                MainActivity.this.runOnUiThread(() -> swipeRefreshLayout.setRefreshing(false));
                webSocket.send(gson.toJson(new CustomJsonObject().setMethod(Methods.DATA_LIST).setId(Methods.Codes.DATA_LIST)));
                refreshData();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                Map data = gson.fromJson(text, Map.class);
                if (data.get("error") != null) {
                    Map error = (Map) data.get("error");
                    assert error != null;
                    Snackbar.make(findViewById(android.R.id.content),
                            String.format(Locale.getDefault(), getString(R.string.error_code_message), toInt(error.get("code")), error.get("message")), Snackbar.LENGTH_LONG).show();
                    return;
                }

                final Fragment fragment = mSectionsPagerAdapter.getItem(mViewPager.getCurrentItem());

                switch (toInt(data.get("id"))) {
                    case Methods.Codes.DATA_LIST: // A message listing the available sensors
                        try {
                            ArrayList<Map> sensorsList = (ArrayList<Map>) data.get(Sensor.RESULT);
                            assert sensorsList != null;
                            mSectionsPagerAdapter.notifyDataSetChanged();
                            for (Map sensorInfo : sensorsList) {
                                if (sensorInfo.get("name") != null) {
                                    if (sensors.getBySID(toInt(sensorInfo.get(Sensor.SID))) == null) {
                                        Sensor sensorToAdd = new Sensor(toInt(sensorInfo.get(Sensor.SID)));
                                        sensorToAdd.setName((String) sensorInfo.get(Sensor.NAME));
                                        sensorToAdd.addSubID(toInt(sensorInfo.get(Sensor.SUBID)));
                                        sensors.addSensor(sensorToAdd);
                                    } else {
                                        Sensor sensorToManage = sensors.getBySID(toInt(sensorInfo.get(Sensor.SID)));
                                        sensorToManage.addSubID(toInt(sensorInfo.get(Sensor.SUBID)));
                                        sensorToManage.setName((String) sensorInfo.get(Sensor.NAME));
                                    }
                                }
                            }
                        } catch (NullPointerException npe) {
                            npe.printStackTrace();
                        }
                        MainActivity.this.runOnUiThread(() -> {
                            Menu menu = navigationView.getMenu();
                            menu.removeGroup(R.id.group_sensors);
                            SubMenu subMenu = menu.addSubMenu(R.id.group_sensors, R.id.group_sensors, 0, R.string.nav_title_sensors);
                            for (Sensor sensor : sensors.getList()) {
                                MenuItem item = subMenu.add(R.id.group_sensors, sensor.getSID(), 1, sensor.getName());
                                item.setIcon(R.drawable.ic_thermometer_black);
                                item.setCheckable(true);
                            }
                        });
                        break;
                    case Methods.Codes.DATA_GET:
                        Map resultSensor = (Map) data.get(Sensor.RESULT);
                        assert resultSensor != null;
                        Sensor sensor = sensors.getBySID(toInt(resultSensor.get(Sensor.SID)));
                        sensor.setTimestamp((Double) resultSensor.get(Sensor.TIMESTAMP));
                        sensor.newValue(toInt(resultSensor.get(Sensor.SUBID)), (Double) resultSensor.get(Sensor.VALUE));
                        if (fragment instanceof SensorFragment)
                            ((SensorFragment) fragment).onSensorUpdated(sensor);
                        break;
                    case Methods.Codes.HEATER_GET_LIMITS:
                        ArrayList<Map> mapArrayList = (ArrayList<Map>) data.get(Sensor.RESULT);
                        Map resultSHL;

                        // If this particular sensor does not have heater target values set, the array will be empty
                        // We use the fact that there will be no elements and do not continue any further.
                        try {
                            resultSHL = mapArrayList.get(0);
                        } catch (IndexOutOfBoundsException e) {
                            break;
                        }
                        assert resultSHL != null;
                        Sensor sensor1 = sensors.getBySID(toInt(resultSHL.get(Sensor.SID)));
                        sensor1.setTarget(
                                (boolean) resultSHL.get(Sensor.TARGET_ENABLE),
                                toInt(resultSHL.get(Sensor.SUBID)),
                                (Double) resultSHL.get(Sensor.TARGET_MIN),
                                (Double) resultSHL.get(Sensor.TARGET_MAX));
                        if (fragment instanceof SensorFragment)
                            ((SensorFragment) fragment).onSensorUpdated(sensor1);
                        break;
                    case Methods.Codes.HEATER_GET_STATUS:
                        Map resultHeater = (Map) data.get(Sensor.RESULT);
                        assert resultHeater != null;
                        heater = new Heater((boolean) resultHeater.get(Heater.ON), toInt(resultHeater.get(Heater.DURATION)));
                        if (fragment instanceof DashboardFragment)
                            ((DashboardFragment) fragment).onHeaterUpdated(MainActivity.this, heater);
                        break;
                    case Methods.Codes.HEATER_SET_STATUS:
                        webSocket.send(gson.toJson(new CustomJsonObject().setId(Methods.Codes.HEATER_GET_STATUS).setMethod(Methods.HEATER_GET_STATUS)));
                        break;
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                super.onClosed(webSocket, code, reason);
                webSocketUtils.clearWebSocket();
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refreshData);
    }

    private boolean refreshData() {
        Log.d(getClass().getSimpleName(), "refreshData called");

        // If the WebSocket is not connected we ought
        if (!webSocketUtils.isWebSocketConnected()) {
            // Turn on the WiFi of the device if it isn't already
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (!wifiManager.isWifiEnabled()) wifiManager.setWifiEnabled(true);

            // If WiFi is connected, we proceed to connect
            ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
                // Check that the WebSocket has not been created before -- this is for cases when refreshData is called, the
                // WebSocket is created but not connected yet, since we do not want multiple WebSockets open simultaneously.
                if (webSocketUtils.getWebSocket() == null) {
                    // Update the UI to reflect the fact that we are connecting; shows a loading spinner
                    swipeRefreshLayout.setRefreshing(true);
                    // Connect and create the new WebSocket
                    webSocketUtils.connectWebSocket(preferences.getString(SettingsFragment.HUB_URL, null), webSocketListener);
                }
            } else {
                // Inform the user that WIFi is not connected and proceed to open settings page to connect to a WiFi network
                Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), R.string.error_wifi_not_connected, Snackbar.LENGTH_INDEFINITE);
                snackbar.setAction(R.string.action_connect, v -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                                .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
                    } catch (ActivityNotFoundException e) { // In some cases, a matching Activity may not exist, so we ensure we safeguard against this.
                        startActivity(new Intent(Settings.ACTION_SETTINGS)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                                .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
                    }
                    snackbar.dismiss();
                }).show();
            }
            return false;
        } else {
            // If the WebSocket is already connected it is ready for sending messages
            WebSocket webSocket = webSocketUtils.getWebSocket();
            // Iterate over all available Sensor ID + Sensor SubID combinations and request both readings and their target values
            for (Sensor sensor : sensors.getList()) {
                for (Integer SubID : sensor.getValues().keySet()) {
                    Log.d("WebSocket", "Requesting data from sensor " + sensor.getSID() + " #" + SubID);
                    Params params = new Params().setSID(sensor.getSID()).setSubID(SubID);
                    webSocket.send(gson.toJson(new CustomJsonObject().setMethod(Methods.DATA_GET).setId(Methods.Codes.DATA_GET).setParams(params)));
                    webSocket.send(gson.toJson(new CustomJsonObject().setMethod(Methods.HEATER_GET_LIMITS).setId(Methods.Codes.HEATER_GET_LIMITS).setParams(params)));
                }
            }
            // Request information on the current state of the heater (on/off/auto ...)
            webSocket.send(gson.toJson(new CustomJsonObject().setId(Methods.Codes.HEATER_GET_STATUS).setMethod(Methods.HEATER_GET_STATUS)));
            return true;
        }
    }


    /**
     * onPause is called when this activity goes out of view -- for example when the user switches to another application or locks their phone
     */
    @Override
    protected void onPause() {
        super.onPause();
        // We close the webSocket (it will be recreated in onResume)
        if (webSocketUtils.isWebSocketConnected()) webSocketUtils.getWebSocket().close(4522, "onPause");
        // and stop requesting new sensor readings automatically
        handler.removeCallbacks(refreshData);
    }

    /**
     * onBackPressed is called when the Android (either virtual or physical) back button is pressed
     */
    @Override
    public void onBackPressed() {
        // If the navigation drawer (side menu) is open, this is our priority in closing it
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            // Handle back presses for navigating up from fragment to fragment
            if (fragmentManager.getBackStackEntryCount() == 0) {
                // If there is no parent fragment to navigate to, we let the system handle the
                // default back button action, which is usually to close (finish) the activity
                super.onBackPressed();
            } else {
                fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }
        }
    }

    /**
     * onNavigationItemSelected is called when a menu item is clicked in the navigation menu
     * @param item The item that was selected in the navigation view
     * @return Return true to display the item as the selected item
     */
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        int id = item.getItemId();

        switch (id) {
            case R.id.nav_dashboard:
                mViewPager.setCurrentItem(0, true);
                break;
            case R.id.nav_settings:
                // Dashboard    = 1
                // Sensors.size = number of sensors
                // Settings     = 1
                // Index @ 0    = -1
                mViewPager.setCurrentItem(sensors.getList().size() + 1, true);
                break;
            default:
                mViewPager.setCurrentItem(sensors.getList().indexOf(sensors.getBySID(id)) + 1, true);
                break;
        }

        // If the item is 'checkable' (this property is set in the XML of the menu {@link R.menu.activity_main_drawer})
        // then we set the item as checked and set the appropriate title on the toolbar
        if (item.isCheckable()) {
            navigationView.setCheckedItem(item);
            setTitle(item.getTitle());
        }

        // Finally we close the drawer when an item is selected since the action (i.e. scrolling
        // the right sensor page etc.) will otherwise happen behind the navigation view.
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        // We always return true since we want to display the item as the selected item
        return true;
    }

    private void setUpViewPager() {
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), sensors);

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.view_pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        // Listen for actions done to the view pager
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                MenuItem item;
                int count = mSectionsPagerAdapter.getCount();
                if (position == 0) {
                    item = navigationView.findViewById(R.id.nav_dashboard);
                } else if (position == count - 1 && count != 1) {
                    item = navigationView.findViewById(R.id.nav_settings);
                } else {
                    item = navigationView.findViewById(sensors.getList().get(position - 1).getSID());
                }
                if (item != null) {
                    if (item.isCheckable()) {
                        navigationView.setCheckedItem(item);
                        setTitle(item.getTitle());
                    }
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setEnabled(state == ViewPager.SCROLL_STATE_IDLE);
                }
            }
        });
    }

    /**
     * When we discover a new sensor, we use {@link Sensors.OnListChangedListener} to know when to update the {@link ViewPager}
     * @param list A list of sensors that we will use to generate fragments
     */
    @Override
    public void onListChanged(List<Sensor> list) {
        MainActivity.this.runOnUiThread(this::setUpViewPager);
    }

    /**
     * A {@link FragmentStatePagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentStatePagerAdapter {

        Sensors sensors;

        /**
         * @param fm A {@link FragmentManager}, usually just pass in getSupportFragmentManager()
         * @param s A {@link Sensors} -- basically just a list of currently available sensors
         */
        SectionsPagerAdapter(FragmentManager fm, Sensors s) {
            super(fm);
            sensors = s;
        }

        /**
         * getItem is called to instantiate the fragment for the given page.
         * @param position The position of the fragment in the "list" -- indexing from 0
         * @return Return an instance of the appropriate fragment for the given page
         */
        @Override
        public Fragment getItem(int position) {
            // Make sure the last item is the Settings page
            if (position == getCount() - 1 && getCount() != 1)
                return SettingsFragment.newInstance();

            // The first item is the Dashboard and everything in between is sensor screens
            switch (position) {
                case 0:
                    return DashboardFragment.newInstance();
                default:
                    return SensorFragment.newInstance(sensors.getList().get(position - 1).getSID());
            }
        }

        @Override
        public int getCount() {
            // If the sensors are not loaded yet, only show the dashboard. We will load other fragments later.
            if (sensors.getList().size() == 0) return 1;
            // Otherwise show the same number of pages that there are sensors plus Dashboard and Settings
            return sensors.getList().size() + 2;
        }
    }
}
