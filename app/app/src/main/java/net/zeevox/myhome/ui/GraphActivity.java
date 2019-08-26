package net.zeevox.myhome.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.EntryXComparator;
import com.google.gson.Gson;

import net.zeevox.myhome.R;
import net.zeevox.myhome.Sensor;
import net.zeevox.myhome.graph.TimeValueFormatter;
import net.zeevox.myhome.json.CustomJsonObject;
import net.zeevox.myhome.json.Methods;
import net.zeevox.myhome.json.Params;
import net.zeevox.myhome.websocket.CustomWebSocketListener;
import net.zeevox.myhome.websocket.WebSocketUtils;

import java.io.EOFException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.Response;
import okhttp3.WebSocket;

public class GraphActivity extends AppCompatActivity {

    public static final String DATA = "data";
    private final WebSocketUtils webSocketUtils = new WebSocketUtils();
    private List<Entry> entries = new ArrayList<>();
    private WebSocket webSocket;
    private int sid;
    private int subid;
    private int daysBack = 0;
    private String name;
    private LineChart chart;
    private float[] extremes;
    private long launchSecond = System.currentTimeMillis() / 1000;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_graph);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        assert getIntent() != null;
        sid = getIntent().getIntExtra(Sensor.SID, -1000);
        subid = getIntent().getIntExtra(Sensor.SUBID, -1000);
        if (sid == -1000 || subid == -1000) finish();

        float target = ((Double) MainActivity.sensors.getBySID(sid).getTargets()[1]).floatValue();

        switch (subid) {
            case Sensor.TEMP_SUBID:
                name = "Temperature";
                break;
            case Sensor.RH_SUBID:
                name = "Relative Humidity";
                break;
            default:
                name = "Sensor";
        }

        chart = findViewById(R.id.chart);

        fetchData();

        chart.setTouchEnabled(true);
        chart.setDragXEnabled(true);
        chart.setScaleXEnabled(true);
        chart.setScaleYEnabled(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                String time = new SimpleDateFormat("EEE d MMM yyyy 'at' HH:mm:ss", Locale.getDefault()).format(e.getX() * 1000);
                ((TextView) findViewById(R.id.graph_point_info)).setText(String.format(Locale.getDefault(), "%s | %.2f", time, e.getY()));
            }

            @Override
            public void onNothingSelected() {

            }
        });

        chart.getAxisRight().setEnabled(false);

        chart.getXAxis().setGranularity(7200f);
        chart.getXAxis().setValueFormatter(new TimeValueFormatter());
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        chart.getXAxis().setDrawGridLines(false);
        if (subid == Sensor.TEMP_SUBID &&
                PreferenceManager.getDefaultSharedPreferences(
                        this).getBoolean(SettingsFragment.SHOW_LIMIT_LINE, true))
            chart.getAxisLeft().addLimitLine(new LimitLine(target));

        if (subid == Sensor.RH_SUBID) {
            chart.getAxisLeft().setAxisMaximum(100f);
        }

        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);

        ImageView buttonBack = findViewById(R.id.graph_chevron_left);
        ImageView buttonForward = findViewById(R.id.graph_chevron_right);
        TextView date = findViewById(R.id.graph_date);

        buttonBack.setOnClickListener(v -> {
            daysBack += 1;
            date.setText(daysBack == 1 ? getString(R.string.graph_yesterday) : String.format(Locale.getDefault(), getString(R.string.graph_days_ago), daysBack));
            chart.highlightValues(null);
            ((TextView) findViewById(R.id.graph_point_info)).setText(null);
            buttonForward.setEnabled(true);
            buttonForward.setVisibility(View.VISIBLE);
            fetchData();
        });

        buttonForward.setOnClickListener(v -> {
            daysBack -= 1;
            chart.highlightValues(null);
            ((TextView) findViewById(R.id.graph_point_info)).setText(null);
            if (daysBack == 0) {
                buttonForward.setEnabled(false);
                buttonForward.setVisibility(View.INVISIBLE);
                date.setText(getResources().getString(R.string.graph_today));
            } else {
                date.setText(daysBack == 1 ? getString(R.string.graph_yesterday) :
                        String.format(Locale.getDefault(), getString(R.string.graph_days_ago), daysBack));
            }
            fetchData();
        });

        // We launch the activity with "Today" view -- therefore we must hide the day forward button
        // This is to prevent the app crashing when it tries to load -1 days ago
        buttonForward.setEnabled(false);
        buttonForward.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onPause() {
        super.onPause();
        webSocket.close(4255, null);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    private void fetchData() {
        extremes = null;
        long timeFrom = launchSecond - 86400 * (daysBack + 1);
        long timeTo = launchSecond - 86400 * daysBack;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        webSocketUtils.connectWebSocket(preferences.getString(SettingsFragment.DATA_URL, null), new CustomWebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
                GraphActivity.this.webSocket = webSocket;
                webSocket.send(new Gson().toJson(new CustomJsonObject().setId(Methods.Codes.SENSOR_GET_DATA).setMethod(Methods.SENSOR_GET_DATA).setParams(new Params()
                        .setLimit(10000)
                        .setSID(sid)
                        .setSubID(subid)
                        .setTimeFrom(timeFrom)
                        .setTimeTo(timeTo))));
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                Log.d("GraphActivity", text);
                final Gson gson = new Gson();
                Map response = gson.fromJson(text, Map.class);
                int messageID = MainActivity.toInt(response.get("id"));
                Log.d("GraphActivity", "Reading message with id " + messageID);
                switch (messageID) {
                    case 100:
                        webSocket.close(4256, null);
                        entries.clear();
                        ArrayList<Map<String, Double>> data = (ArrayList<Map<String, Double>>) ((Map) response.get(Sensor.RESULT)).get(DATA);
                        if (data != null) {
                            Log.d("GraphActivity", "Data received, processing...");
                            for (Map<String, Double> point : data) {
                                float time = point.get("ts").floatValue();
                                float value = point.get("v").floatValue();
                                if (extremes == null) {
                                    extremes = new float[]{time, value};
                                }
                                entries.add(new Entry(time, value));
                                if (value < extremes[0]) extremes[0] = value;
                                if (value > extremes[1]) extremes[1] = value;
                            }
                        } else {
                            Log.e(getClass().getSimpleName(), "Data is empty");
                        }
                        Collections.sort(entries, new EntryXComparator());

                        runOnUiThread(() -> {
                            LineDataSet dataSet = new LineDataSet(entries, name);
                            dataSet.setDrawCircles(false);
                            dataSet.setColor(getResources().getColor(R.color.colorPrimary));

                            if (extremes[0] < 0) {
                                chart.getAxisLeft().setAxisMinimum(extremes[0]);
                            } else {
                                chart.getAxisLeft().setAxisMinimum(0f);
                            }

                            LineData lineData = new LineData(dataSet);

                            chart.setData(lineData);
                            chart.invalidate();

                            TextView extremesInfo = findViewById(R.id.graph_extremes_info);
                            if (preferences.getBoolean(SettingsFragment.SHOW_EXTREMES, true)) {
                                extremesInfo.setText(String.format(Locale.getDefault(), "Min: %.2f | Max: %.2f", extremes[0], extremes[1]));
                            } else {
                                extremesInfo.setVisibility(View.GONE);
                            }
                        });

                        break;
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                if (!(t instanceof EOFException)) {
                    t.printStackTrace();
                }
            }
        });
    }
}
