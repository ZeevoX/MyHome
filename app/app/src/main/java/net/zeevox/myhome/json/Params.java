package net.zeevox.myhome.json;

/**
 * Use this class in conjunction with {@link CustomJsonObject} in order to send parameters to the WebSocket, such as the sensor ID
 */
@SuppressWarnings({"FieldCanBeLocal", "unused", "UnusedReturnValue"})
public class Params {
    private int sid;
    private int subid;
    private boolean heater_on;
    private int duration;
    private int limit;
    private float ts_from;
    private float ts_to;
    private double min;
    private double max;
    private boolean enable;

    public Params() {
    }

    /**
     * @param sid integer -- the Sensor ID -- required for the majority of requests
     * @return {@link Params} <code>this</code> -- allows for method chaining + shorter code
     */
    public Params setSID(int sid) {
        this.sid = sid;
        return this;
    }

    /**
     * @param subid integer -- the Sensor SubID -- required for the majority of requests
     * @return {@link Params} <code>this</code> -- allows for method chaining + shorter code
     */
    public Params setSubID(int subid) {
        this.subid = subid;
        return this;
    }

    /**
     * @param heater_on boolean representing the current heater state (on/off)
     * @return {@link Params} <code>this</code> -- allows for method chaining + shorter code
     *
     * @see net.zeevox.myhome.Heater
     */
    public Params setHeaterOn(boolean heater_on) {
        this.heater_on = heater_on;
        return this;
    }

    /**
     * @param duration integer value in seconds for how long the heater should stay on
     * @return {@link Params} <code>this</code> -- allows for method chaining + shorter code
     *
     * @see net.zeevox.myhome.Heater
     */
    public Params setDuration(int duration) {
        this.duration = duration;
        return this;
    }

    /**
     * When requesting data from the historical data server
     * @param limit integer representing the limit for the number of historical readings we are willing to receive (if no parameter sent the default is 100)
     * @return {@link Params} <code>this</code> -- allows for method chaining + shorter code
     */
    public Params setLimit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * When requesting data from the historical data server
     * @param ts_from float representing the epoch timestamp in seconds of the start of the time period we are requesting
     * @return {@link Params} <code>this</code> -- allows for method chaining + shorter code
     */
    public Params setTimeFrom(float ts_from) {
        this.ts_from = ts_from;
        return this;
    }

    /**
     * When requesting data from the historical data server
     * @param ts_to float representing the epoch timestamp in seconds of the end of the time period we are requesting -- note that this parameter is less important than {@link #setLimit(int)}
     * @return {@link Params} <code>this</code> -- allows for method chaining + shorter code
     */
    public Params setTimeTo(float ts_to) {
        this.ts_to = ts_to;
        return this;
    }

    /**
     * @param min double -- for hysteresis - the minimum reading from the sensor before we turn on the heating
     * @return {@link Params} <code>this</code> -- allows for method chaining + shorter code
     *
     * @see net.zeevox.myhome.Sensor
     */
    public Params setMin(double min) {
        this.min = min;
        return this;
    }

    /**
     * @param max double -- for hysteresis - the maximum reading from the sensor before we turn off the heating (NB: the heating will stay on until all sensors achieve their target temperature)
     * @return {@link Params} <code>this</code> -- allows for method chaining + shorter code
     *
     * @see net.zeevox.myhome.Sensor
     */
    public Params setMax(double max) {
        this.max = max;
        return this;
    }

    /**
     * @param enable boolean whether the target values of the sensor should be applied in practice -- use this to ignore a sensor's readings
     * @return {@link Params} <code>this</code> -- allows for method chaining + shorter code
     *
     * @see net.zeevox.myhome.Sensor
     */
    public Params setEnable(boolean enable) {
        this.enable = enable;
        return this;
    }
}
