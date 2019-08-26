package net.zeevox.myhome;

/**
 * A class that stores information about the state of the heating system
 */
public class Heater {
    public static final String ON = "heater_on";
    public static final String DURATION = "duration";
    private final boolean on;
    private final int duration;

    /**
     * @param on boolean to tell if the heater currently heating
     * @param duration an integer with how long the current state will persist, -1 for automatic and anything else for manually set
     */
    public Heater(boolean on, int duration) {
        this.on = on;
        this.duration = duration;
    }

    public boolean isOn() {
        return on;
    }

    public int getDuration() {
        return duration;
    }

    public boolean isInAutomaticMode() {
        return duration == -1;
    }

    public boolean isInManualMode() {
        return duration >= 0;
    }
}
