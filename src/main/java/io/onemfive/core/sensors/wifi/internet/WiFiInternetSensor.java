package io.onemfive.core.sensors.wifi.internet;

import io.onemfive.core.sensors.Sensor;
import io.onemfive.data.Envelope;

import java.util.Properties;
import java.util.logging.Logger;

/**
 * TODO: Add Description
 *
 * @author objectorange
 */
public class WiFiInternetSensor implements Sensor {

    private static final Logger LOG = Logger.getLogger(WiFiInternetSensor.class.getName());

    @Override
    public boolean send(Envelope envelope) {
        return false;
    }

    @Override
    public boolean start(Properties properties) {
        return false;
    }

    @Override
    public boolean pause() {
        return false;
    }

    @Override
    public boolean unpause() {
        return false;
    }

    @Override
    public boolean restart() {
        return false;
    }

    @Override
    public boolean shutdown() {
        return false;
    }

    @Override
    public boolean gracefulShutdown() {
        return false;
    }
}
