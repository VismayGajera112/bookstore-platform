package com.example.common.config;

/**
 * Bound to {@code bookstore.demo.*} from the Config Server. The bean itself is created as
 * {@code @RefreshScope} in {@link CommonConfig}, so {@code POST /actuator/refresh} rebuilds it after
 * a central config change without restarting the process.
 */
public class DemoProperties {

    private String message = "config-server not yet loaded";

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
