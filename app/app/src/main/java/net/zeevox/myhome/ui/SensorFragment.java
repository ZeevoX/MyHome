package net.zeevox.myhome.ui;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import net.zeevox.myhome.R;
import net.zeevox.myhome.Sensor;

import java.text.DecimalFormat;

public class SensorFragment extends Fragment {

    private final DecimalFormat oneDecimal = new DecimalFormat("#.#");
    private final DecimalFormat oneDecimalExact = new DecimalFormat("0.0");
    private final String TAG = getClass().getSimpleName();
    private Sensor sensor;
    private double currentTemp;
    private double relativeHumidity;
    private double[] tempTargets;
    private double hysteresis;
    private boolean targetEnabled;
    private double timestamp;

    public static SensorFragment newInstance(int sid) {
        SensorFragment fragment = new SensorFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(Sensor.SID, sid);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_sensor, container, false);

        assert getArguments() != null;

        onSensorUpdated(MainActivity.sensors.getBySID(getArguments().getInt(Sensor.SID)));

        TextView tempUnitsTextView = view.findViewById(R.id.sensor_units);
        tempUnitsTextView.setText(R.string.units_celsius);

        TextView currentTempValue = view.findViewById(R.id.sensor_value);
        if (!(currentTemp == -1000)) {
            currentTempValue.setText(String.valueOf(oneDecimal.format(currentTemp)));
            view.findViewById(R.id.sensor_value_layout).setOnClickListener(v -> startGraphFragment(sensor.getSID(), Sensor.TEMP_SUBID));
        }

        if (relativeHumidity == -1000) {
            view.findViewById(R.id.sensor_rh_layout).setVisibility(View.GONE);
        } else {
            ((TextView) view.findViewById(R.id.sensor_rh)).setText(
                    String.format("%s%%", String.valueOf(oneDecimal.format(relativeHumidity))));
            view.findViewById(R.id.sensor_rh_layout).setOnClickListener(v -> startGraphFragment(sensor.getSID(), Sensor.RH_SUBID));
        }

        if (tempTargets[0] == 0 && tempTargets[1] == 0) {
            view.findViewById(R.id.sensor_target_layout).setVisibility(View.GONE);
            view.findViewById(R.id.target_control_layout).setVisibility(View.GONE);
        } else {
            Switch targetEnabledSwitch = view.findViewById(R.id.target_control);

            targetEnabledSwitch.setChecked(targetEnabled);
            view.findViewById(R.id.sensor_target_layout).setVisibility(targetEnabled ? View.VISIBLE : View.GONE);

            targetEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                view.findViewById(R.id.sensor_target_layout).setVisibility(isChecked ? View.VISIBLE : View.GONE);
                sensor.setTargetEnabled(isChecked);
            });
            view.findViewById(R.id.target_control_layout).setOnClickListener(v -> targetEnabledSwitch.setChecked(!targetEnabledSwitch.isChecked()));

            ((TextView) view.findViewById(R.id.sensor_target)).setText(
                    String.format("%s %s", String.valueOf(oneDecimalExact.format(tempTargets[1])), getString(R.string.units_celsius)));

            view.findViewById(R.id.sensor_target_layout).setOnClickListener(v -> {
                assert getActivity() != null;
                Dialog dialog = new Dialog(getActivity());
                dialog.setContentView(R.layout.dialog_number_pick);

                TextView dialogValue = dialog.findViewById(R.id.dialog_number_pick_value);
                dialogValue.setText(oneDecimalExact.format(tempTargets[1]));

                ImageView addButton = dialog.findViewById(R.id.dialog_button_plus);
                addButton.setOnClickListener(v12 -> {
                    tempTargets[1] += 0.1;
                    dialogValue.setText(oneDecimalExact.format(tempTargets[1]));
                });
                addButton.setOnLongClickListener(v14 -> {
                    tempTargets[1] += 1.0;
                    dialogValue.setText(oneDecimalExact.format(tempTargets[1]));
                    return true;
                });
                ImageView minusButton = dialog.findViewById(R.id.dialog_button_minus);
                minusButton.setOnClickListener(v13 -> {
                    tempTargets[1] -= 0.1;
                    dialogValue.setText(oneDecimalExact.format(tempTargets[1]));
                });
                minusButton.setOnLongClickListener(v14 -> {
                    tempTargets[1] -= 1.0;
                    dialogValue.setText(oneDecimalExact.format(tempTargets[1]));
                    return true;
                });

                Button positiveButton = dialog.findViewById(R.id.dialog_button_ok);
                positiveButton.setOnClickListener(v1 -> {
                    ((TextView) view.findViewById(R.id.sensor_target)).setText(String.format("%s %s", oneDecimalExact.format(tempTargets[1]), getString(R.string.units_celsius)));
                    sensor.setTargets(new double[]{tempTargets[1] - hysteresis, tempTargets[1]});
                    dialog.dismiss();
                });
                dialog.show();
            });
        }

        if (timestamp == 0) {
            view.findViewById(R.id.last_updated).setVisibility(View.INVISIBLE);
        } else {
            String status;
            final double diff = System.currentTimeMillis() - timestamp * 1000;
            if (diff < 60000) {
                status = getString(R.string.ts_less_than_minute);
            } else if (diff < 120000) {
                status = getString(R.string.ts_one_minute);
            } else if (diff > 3600000) {
                status = getString(R.string.ts_hour);
            } else {
                status = String.format(getString(R.string.ts_minutes), String.valueOf(((Double) Math.floor(diff / 1000 / 60)).intValue()));
            }
            ((TextView) view.findViewById(R.id.last_updated)).setText(status);
        }

        return view;
    }

    private void startGraphFragment(int sid, int subid) {
        Intent intent = new Intent(getActivity(), GraphActivity.class);
        intent.putExtra(Sensor.SID, sid);
        intent.putExtra(Sensor.SUBID, subid);
        startActivity(intent);
    }

    public void onSensorUpdated(@NonNull Sensor s) {
        Log.i("SensorFragment", "onSensorUpdated");
        this.sensor = s;
        currentTemp = sensor.getValue(Sensor.TEMP_SUBID, 0.0);
        relativeHumidity = sensor.getValue(Sensor.RH_SUBID, -1000.0);
        tempTargets = sensor.getTargets();
        hysteresis = tempTargets[1] - tempTargets[0];
        targetEnabled = sensor.isTargetEnabled();
        timestamp = sensor.getTimestamp();
    }
}
