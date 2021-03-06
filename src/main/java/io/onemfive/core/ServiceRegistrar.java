package io.onemfive.core;

import io.onemfive.core.bus.ServiceNotAccessibleException;
import io.onemfive.core.bus.ServiceNotSupportedException;
import io.onemfive.core.bus.ServiceRegisteredException;

import java.util.Properties;

/**
 * TODO: Add Description
 *
 * @author objectorange
 */
public interface ServiceRegistrar {

    void register(Class serviceClass, Properties properties) throws ServiceNotAccessibleException, ServiceNotSupportedException, ServiceRegisteredException;

    void unregister(Class serviceClass);
}
