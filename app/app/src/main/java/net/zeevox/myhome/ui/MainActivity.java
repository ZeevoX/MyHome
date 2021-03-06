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
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
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
import net.zeevox.myhome.WebSocketUtils;
import net.zeevox.myhome.json.CustomJsonObject;
import net.zeevox.myhome.json.Methods;
import net.zeevox.myhome.json.Params;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    public static final Sensors sensors = new Sensors();
    public static Heater heater;
    public static WebSocketUtils webSocketUtils;
    private final boolean[] web_socket_connected = {false};
    private final FragmentManager fragmentManager = getSupportFragmentManager();
    private final Gson gson = new Gson();
    private final Handler handler = new Handler();
    private WebSocketListener webSocketListener;
    private NavigationView navigationView;
    private MenuItem menuItem;
    private SharedPreferences preferences;
    private SwipeRefreshLayout swipeRefreshLayout;
    private final Runnable refreshData = new Runnable() {
        @Override
        public void run() {
            refreshData();
            handler.postDelayed(this, 5000);
        }
    };

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

        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.frame_layout, new DashboardFragment(), DashboardFragment.class.getSimpleName());
        fragmentTransaction.commit();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.nav_drawer_open, R.string.nav_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        Objects.requireNonNull(getSupportActionBar()).setElevation(0);

        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        ((TextView) navigationView.getHeaderView(0).findViewById(R.id.nav_header_app_version)).setText(String.format(getString(R.string.nav_app_version), BuildConfig.VERSION_NAME));

        swipeRefreshLayout = findViewById(R.id.dashboard_swipe_refresh);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            swipeRefreshLayout.setRefreshing(false);
            if (!refreshData()) {
                Snackbar.make(findViewById(android.R.id.content), R.string.error_device_connecting, Snackbar.LENGTH_SHORT).show();
            } else if (menuItem != null) {
                onNavigationItemSelected(menuItem);
            }
        });
        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.colorAccent));

        webSocketUtils = new WebSocketUtils();
        webSocketListener = new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
                web_socket_connected[0] = true;
                MainActivity.this.runOnUiThread(() -> swipeRefreshLayout.setRefreshing(false));
                webSocket.send(gson.toJson(new CustomJsonObject().setMethod(Methods.DATA_LIST).setId(1157)));
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

                switch (toInt(data.get("id"))) {
                    case 1157: // A message listing the available sensors
                        try {
                            ArrayList<Map> sensorsList = (ArrayList<Map>) data.get(Sensor.RESULT);
                            assert sensorsList != null;
                            for (Map sensorInfo : sensorsList) {
                                if (sensorInfo.get("name") != null) {
                                    if (sensors.getBySID(toInt(sensorInfo.get(Sensor.SID))) == null) {
                                        Sensor sensorToAdd = new Sensor(toInt(sensorInfo.get(Sensor.SID)));
                                        sensorToAdd.setName((String) sensorInfo.get(Sensor.NAME));
                                        sensorToAdd.addSubID(toInt(sensorInfo.get(Sensor.SUBID)));
                                        sensors.getList().add(sensorToAdd);
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
                    case 637:
                        Map resultSensor = (Map) data.get(Sensor.RESULT);
                        assert resultSensor != null;
                        Sensor sensor = sensors.getBySID(toInt(resultSensor.get(Sensor.SID)));
                        sensor.setTimestamp((Double) resultSensor.get(Sensor.TIMESTAMP));
                        sensor.newValue(toInt(resultSensor.get(Sensor.SUBID)), (Double) resultSensor.get(Sensor.VALUE));
                        Fragment fragment1 = fragmentManager.findFragmentByTag(SensorFragment.class.getSimpleName());
                        if (fragment1 instanceof SensorFragment) ((SensorFragment) fragment1).onSensorUpdated(sensor);
                        break;
                    case 638:
                        ArrayList<Map> mapArrayList = (ArrayList<Map>) data.get(Sensor.RESULT);
                        Map resultSHL;
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
                        Fragment fragment2 = fragmentManager.findFragmentByTag(SensorFragment.class.getSimpleName());
                        if (fragment2 instanceof SensorFragment) ((SensorFragment) fragment2).onSensorUpdated(sensor1);
                        break;
                    case 8347:
                        Map resultHeater = (Map) data.get(Sensor.RESULT);
                        assert resultHeater != null;
                        heater = new Heater((boolean) resultHeater.get(Heater.ON), toInt(resultHeater.get(Heater.DURATION)));
                        Fragment fragment3 = fragmentManager.findFragmentByTag(DashboardFragment.class.getSimpleName());
                        if (fragment3 instanceof DashboardFragment) ((DashboardFragment) fragment3).onHeaterUpdated(MainActivity.this, heater);
                        break;
                    case 53:
                        webSocket.send(gson.toJson(new CustomJsonObject().setId(8347).setMethod(Methods.HEATER_GET_STATUS)));
                        break;
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                super.onClosed(webSocket, code, reason);
                Log.w("WebSocket.onClosed", reason);
                web_socket_connected[0] = false;
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                if (t instanceof SocketTimeoutException) {
                    Snackbar.make(findViewById(android.R.id.content), R.string.error_connection_failure, Snackbar.LENGTH_SHORT)
                            .setAction(R.string.action_refresh, v -> refreshData())
                            .show();
                }
                t.printStackTrace();
                web_socket_connected[0] = false;
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

        if (!web_socket_connected[0]) {
            // Turn on the WiFi of the device if it isn't already
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (!wifiManager.isWifiEnabled()) wifiManager.setWifiEnabled(true);

            // If WiFi is connected, we proceed to connect, otherwise, inform the user and open settings page to connect to a WiFi network
            ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
                swipeRefreshLayout.setRefreshing(true);
                webSocketUtils.connectWebSocket(preferences.getString(SettingsFragment.HUB_URL, null), webSocketListener);
            } else {
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
            WebSocket webSocket = webSocketUtils.getWebSocket();
            for (Sensor sensor : sensors.getList()) {
                for (Integer SubID : sensor.getValues().keySet()) {
                    Log.d("WebSocket", "Requesting data from sensor " + sensor.getSID() + " #" + SubID);
                    Params params = new Params().setSID(sensor.getSID()).setSubID(SubID);
                    webSocket.send(gson.toJson(new CustomJsonObject().setMethod(Methods.DATA_GET).setId(637).setParams(params)));
                    webSocket.send(gson.toJson(new CustomJsonObject().setMethod(Methods.HEATER_GET_LIMITS).setId(638).setParams(params)));
                }
            }
            webSocket.send(gson.toJson(new CustomJsonObject().setId(8347).setMethod(Methods.HEATER_GET_STATUS)));
            return true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web_socket_connected[0]) webSocketUtils.getWebSocket().close(4522, "onPause");
        handler.removeCallbacks(refreshData);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            // Handle back presses for navigating up from fragment to fragment
            if (fragmentManager.getBackStackEntryCount() == 0) {
                super.onBackPressed();
            } else {
                fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            }
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        menuItem = item;
        Fragment fragment = null;

        Class fragmentClass;
        Bundle bundle = new Bundle();

        switch (id) {
            case R.id.nav_dashboard:
                fragmentClass = DashboardFragment.class;
                break;
            case R.id.nav_settings:
                fragmentClass = SettingsFragment.class;
                break;
            default:
                fragmentClass = SensorFragment.class;
                bundle.putInt(Sensor.SID, item.getItemId());
        }

        try {
            fragment = (Fragment) fragmentClass.newInstance();
            fragment.setArguments(bundle);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }

        assert fragment != null;
        fragmentManager.beginTransaction()
                .replace(R.id.frame_layout, fragment, fragment.getClass().getSimpleName())
                .commit();

        if (item.isCheckable()) {
            navigationView.setCheckedItem(item);
            setTitle(item.getTitle());
        }
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
