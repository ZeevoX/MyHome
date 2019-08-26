package net.zeevox.myhome;

import java.util.ArrayList;
import java.util.List;

public class Sensors {
    private final List<Sensor> list = new ArrayList<>();
    OnListChangedListener callback;

    public List<Sensor> getList() {
        return list;
    }

    public Sensor getBySID(int SID) {
        for (Sensor tempSensor : list) {
            if (tempSensor.getSID() == SID) {
                return tempSensor;
            }
        }
        return null;
    }

    public void addSensor(Sensor sensor) {
        list.add(sensor);
        callback.onListChanged(list);
    }

    public void setOnHeadlineSelectedListener(OnListChangedListener callback) {
        this.callback = callback;
    }

    public interface OnListChangedListener {
        void onListChanged(List<Sensor> list);
    }
}
