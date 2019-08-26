package net.zeevox.myhome.json;

/**
 * Use {@link com.google.gson.Gson} to convert the created {@link CustomJsonObject} into JSON to send via the WebSocket.
 */
@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class CustomJsonObject {
    private String method;
    private int id;
    private Params params;

    /**
     * @param method The method call to request in our message
     * @return {@link CustomJsonObject} <code>this</code> -- allows for method chaining + shorter code
     *
     * @see Methods
     */
    public CustomJsonObject setMethod(String method) {
        this.method = method;
        return this;
    }

    /**
     * @param id integer representing the unique request code of this message -- the Hub will reply with the same ID in its message
     * @return {@link CustomJsonObject} <code>this</code> -- allows for method chaining + shorter code
     *
     * @see Methods.Codes
     */
    public CustomJsonObject setId(int id) {
        this.id = id;
        return this;
    }

    /**
     * Set any parameters to send
     * @param params A {@link Params} object containing any parameters to send to the WebSocket
     * @return {@link CustomJsonObject} <code>this</code> -- allows for method chaining + shorter code
     *
     * @see Params
     */
    public CustomJsonObject setParams(Params params) {
        this.params = params;
        return this;
    }
}