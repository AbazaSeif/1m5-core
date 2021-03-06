package io.onemfive.core;

import io.onemfive.core.client.ClientAppManager;
import io.onemfive.core.bus.ServiceBus;
import io.onemfive.core.infovault.InfoVaultDB;
import io.onemfive.core.infovault.InfoVaultService;
import io.onemfive.core.infovault.LocalFSInfoVaultDB;
import io.onemfive.core.util.data.Base64;
import io.onemfive.core.util.*;
import io.onemfive.core.util.stat.StatManager;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * <p>Provide a scope for accessing services that 15M5 provides.  Rather than
 * using the traditional singleton, where any component can access the component
 * in question directly, all of those 1M5 related services are exposed through
 * a particular OneMFiveAppContext. This helps not only with understanding their use
 * and the services 1M5 provides, but it also allows multiple isolated
 * environments to operate concurrently within the same JVM - particularly useful
 * for stubbing out implementations of the rooted services and simulating the
 * software's interaction between multiple instances.</p>
 *
 * <p>As a simplification, there is also a global context - if some component needs
 * access to one of the services but doesn't have its own context from which
 * to root itself, it binds to the OneMFiveAppContext's globalAppContext(), which is
 * the first context that was created within the JVM, or a new one if no context
 * existed already.  This functionality is often used within the 1M5 core for
 * logging - e.g. <pre>
 *     private static final Log _log = new Log(someClass.class);
 * </pre>
 * It is for this reason that applications that care about working with multiple
 * contexts should build their own context as soon as possible (within the main(..))
 * so that any referenced components will latch on to that context instead of
 * instantiating a new one.  However, there are situations in which both can be
 * relevant.</p>
 *
 * @author I2P, objectorange
 */
public class OneMFiveAppContext {

    private static final Logger LOG = Logger.getLogger(OneMFiveAppContext.class.getName());

    /** the context that components without explicit root are bound */
    protected static OneMFiveAppContext globalAppContext;
//    protected final OneMFiveConfig config;

    protected final Properties overrideProps = new Properties();
    private Properties envProps;

    private StatManager statManager;
    private LogManager logManager;
    private SimpleTimer simpleTimer;

    private ServiceBus serviceBus;

    private InfoVaultDB infoVaultDB;

    private volatile boolean statManagerInitialized;
    private volatile boolean logManagerInitialized;
    private volatile boolean simpleTimerInitialized;

    protected Set<Runnable> shutdownTasks;
    private File baseDir;
    private File configDir;
    private File pidDir;
    private File logDir;
    private File appDir;
    private volatile File tmpDir;
    private final Random tmpDirRand = new Random();
    private static ClientAppManager clientAppManager;
    private final static Object lockA = new Object();
    private boolean initialize = false;
    private boolean configured = false;
    // split up big lock on this to avoid deadlocks
    private final Object lock1 = new Object(), lock2 = new Object(), lock3 = new Object(), lock4 = new Object();

    /**
     * Pull the default context, creating a new one if necessary, else using
     * the first one created.
     *
     * Warning - do not save the returned value, or the value of any methods below,
     * in a static field, or you will get the old context if a new instance is
     * started in the same JVM after the first is shut down,
     * e.g. on Android.
     */
    public static synchronized OneMFiveAppContext getInstance() {
        synchronized (lockA) {
            if (globalAppContext == null) {
                globalAppContext = new OneMFiveAppContext(false, null);
                LOG.info("Created and returning new instance: " + globalAppContext);
            } else{
                LOG.info("Returning cached instance: " + globalAppContext);
            }
        }
        if(!globalAppContext.configured) {
            globalAppContext.configure();
        }
        return globalAppContext;
    }

    public static OneMFiveAppContext getInstance(Properties properties) {
        synchronized (lockA) {
            if (globalAppContext == null) {
                globalAppContext = new OneMFiveAppContext(false, properties);
                LOG.info("Created and returning new instance: " + globalAppContext);
            } else {
                LOG.info("Returning cached instance: " + globalAppContext);
            }
        }
        if(!globalAppContext.configured) {
            globalAppContext.configure();
        }
        return globalAppContext;
    }

    public static void clearGlobalContext() {
        globalAppContext = null;
    }

    /**
     * Create a new context.
     *
     * @param doInit should this context be used as the global one (if necessary)?
     *               Will only apply if there is no global context now.
     */
    private OneMFiveAppContext(boolean doInit, Properties envProps) {
        this.initialize = doInit;
        this.envProps = envProps;
    }

    private void configure() {
        // set early to ensure it's not called twice
        this.configured = true;
        try {
            overrideProps.putAll(Config.loadFromClasspath("1m5.config", envProps, false));
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }

        shutdownTasks = new ConcurrentHashSet<>(10);

        String version = getProperty("1m5.version");
        LOG.info("1M5 Version: "+version);

        String systemTimeZone = getProperty("1m5.systemTimeZone");
        LOG.info("1M5 System Time Zone: "+systemTimeZone);
        TimeZone.setDefault(TimeZone.getTimeZone(systemTimeZone));

        String baseStr = getProperty("1m5.dir.base");
        LOG.info("Base Directory: "+baseStr);
        baseDir = new File(baseStr);
        if(!baseDir.exists()) {
            baseDir.mkdir();
        }

        String configStr = baseStr + "/config";
        configDir = new SecureFile(configStr);
        if(!configDir.exists())
            configDir.mkdir();

        String pidStr = baseStr + "/pid";
        pidDir = new SecureFile(pidStr);
        if (!pidDir.exists())
            pidDir.mkdir();

        String logStr = baseStr + "/log";
        logDir = new SecureFile(logStr);
        if (!logDir.exists())
            logDir.mkdir();

        String appStr = baseStr + "/app";
        appDir = new SecureFile(appStr);
        if (!appDir.exists())
            appDir.mkdir();

        String tmpStr = baseStr + "/tmp";
        tmpDir = new SecureFile(tmpStr);
        if (!tmpDir.exists())
            tmpDir.mkdir();

        clientAppManager = new ClientAppManager(false);
        // Instantiate Service Bus
        serviceBus = new ServiceBus(overrideProps, clientAppManager);

        if (initialize) {
            if (globalAppContext == null) {
                globalAppContext = this;
            } else {
                LOG.warning("Warning - New context not replacing old one, you now have an additional one");
                (new Exception("I did it")).printStackTrace();
            }
        }

        // InfoVaultDB
        try {
            if(envProps.getProperty(InfoVaultDB.class.getName()) != null) {
                LOG.info("Instantiating InfoVaultDB of type: "+envProps.getProperty(InfoVaultDB.class.getName()));
                infoVaultDB = InfoVaultService.getInfoVaultDBInstance(envProps.getProperty(InfoVaultDB.class.getName()));
            } else {
                LOG.info("No InfoVaultDB type provided. Instantiating InfoVaultDB of default type: "+LocalFSInfoVaultDB.class.getName());
                infoVaultDB = InfoVaultService.getInfoVaultDBInstance(LocalFSInfoVaultDB.class.getName());
            }
            infoVaultDB.init(envProps);
        } catch (Exception e) {
            LOG.warning(e.getLocalizedMessage());
        }
        this.configured = true;
//        config = new OneMFiveConfig();
    }

    public InfoVaultDB getInfoVaultDB() {
        return infoVaultDB;
    }

    public ClientAppManager getClientAppManager() {
        if(clientAppManager.getStatus() == ClientAppManager.Status.STOPPED)
            clientAppManager.initialize();
        return clientAppManager;
    }

    public ServiceBus getServiceBus() {
        return serviceBus;
    }

    /**
     *  This is the installation dir, often referred to as $1m5.
     *  Applications should consider this directory read-only and never
     *  attempt to write to it.
     *  It may actually be read-only on a multi-user installation.
     *  The config files in this directory are templates for user
     *  installations and should not be accessed by applications.
     *
     *  @return dir constant for the life of the context
     */
    public File getBaseDir() { return baseDir; }

    /**
     *  The base dir for config files.
     *  Applications may use this to access router configuration files if necessary.
     *  Usually ~/.1m5/config on Linux and %APPDIR%\.1m5/config on Windows.
     *
     *  @return dir constant for the life of the context
     */
    public File getConfigDir() { return configDir; }

    /**
     *  Where ping goes.
     *  Applications should not use this.
     *
     *  @return dir constant for the life of the context
     */
    public File getPIDDir() { return pidDir; }

    /**
     *  Where the log directory is.
     *  Applications should not use this.
     *  (i.e. ~/.1m5/log, NOT ~/.1m5/log)
     *
     *  @return dir constant for the life of the context
     */
    public File getLogDir() { return logDir; }

    /**
     *  Where applications may store data.
     *  Applications should create their own directory inside this directory
     *  to avoid collisions with other apps.
     *  (i.e. ~/.1m5/app, NOT ~/.1m5/app)
     *
     *  @return dir constant for the life of the context
     */
    public File getAppDir() { return appDir; }

    /**
     *  Where anybody may store temporary data.
     *  This is a directory created in the system temp dir on the
     *  first call in this context, and is deleted on JVM exit.
     *  Applications should create their own directory inside this directory
     *  to avoid collisions with other apps.
     *  (i.e. ~/.1m5/tmp, NOT ~/.1m5/tmp)
     *
     *  @return dir constant for the life of the context
     */
    public File getTempDir() {
        // fixme don't synchronize every time
        synchronized (lock1) {
            if (tmpDir == null) {
                String d = getProperty("1m5.dir.temp", System.getProperty("java.io.tmpdir"));
                // our random() probably isn't warmed up yet
                byte[] rand = new byte[6];
                tmpDirRand.nextBytes(rand);
                String f = "1m5-" + Base64.encode(rand) + ".tmp";
                tmpDir = new SecureFile(d, f);
                if (tmpDir.exists()) {
                    // good or bad ? loop and try again?
                } else if (tmpDir.mkdir()) {
                    tmpDir.deleteOnExit();
                } else {
                    LOG.warning("WARNING: Could not create temp dir " + tmpDir.getAbsolutePath());
                    tmpDir = new SecureFile(baseDir, "tmp");
                    tmpDir.mkdirs();
                    if (!tmpDir.exists())
                        LOG.severe("ERROR: Could not create temp dir " + tmpDir.getAbsolutePath());
                }
            }
        }
        return tmpDir;
    }

    /** don't rely on deleteOnExit() */
    public void deleteTempDir() {
        synchronized (lock1) {
            if (tmpDir != null) {
                FileUtil.rmdir(tmpDir, false);
                tmpDir = null;
            }
        }
    }

    /**
     * Access the configuration attributes of this context, using properties
     * provided during the context construction, or falling back on
     * System.getProperty if no properties were provided during construction
     * (or the specified prop wasn't included).
     *
     */
    public String getProperty(String propName) {
        String rv = overrideProps.getProperty(propName);
        if (rv != null)
            return rv;
        return System.getProperty(propName);
    }

    /**
     * Access the configuration attributes of this context, using properties
     * provided during the context construction, or falling back on
     * System.getProperty if no properties were provided during construction
     * (or the specified prop wasn't included).
     *
     */
    public String getProperty(String propName, String defaultValue) {
        if (overrideProps.containsKey(propName))
            return overrideProps.getProperty(propName, defaultValue);
        return System.getProperty(propName, defaultValue);
    }

    /**
     * Return an int with an int default
     */
    public int getProperty(String propName, int defaultVal) {
        String val = overrideProps.getProperty(propName);
        if (val == null)
            val = System.getProperty(propName);
        int ival = defaultVal;
        if (val != null) {
            try {
                ival = Integer.parseInt(val);
            } catch (NumberFormatException nfe) {LOG.warning(nfe.getLocalizedMessage());}
        }
        return ival;
    }

    /**
     * Return a long with a long default
     */
    public long getProperty(String propName, long defaultVal) {
        String val  = overrideProps.getProperty(propName);
        if (val == null)
            val = System.getProperty(propName);
        long rv = defaultVal;
        if (val != null) {
            try {
                rv = Long.parseLong(val);
            } catch (NumberFormatException nfe) {LOG.warning(nfe.getLocalizedMessage());}
        }
        return rv;
    }

    /**
     * Return a boolean with a boolean default
     */
    public boolean getProperty(String propName, boolean defaultVal) {
        String val = getProperty(propName);
        if (val == null)
            return defaultVal;
        return Boolean.parseBoolean(val);
    }

    /**
     * Default false
     */
    public boolean getBooleanProperty(String propName) {
        return Boolean.parseBoolean(getProperty(propName));
    }

    public boolean getBooleanPropertyDefaultTrue(String propName) {
        return getProperty(propName, true);
    }

    /**
     * Access the configuration attributes of this context, listing the properties
     * provided during the context construction, as well as the ones included in
     * System.getProperties.
     *
     * WARNING - not overridden in ConsciousContext, doesn't contain router config settings,
     * use getProperties() instead.
     *
     * @return set of Strings containing the names of defined system properties
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Set<String> getPropertyNames() {
        // clone to avoid ConcurrentModificationException
        Set<String> names = new HashSet<String>((Set<String>) (Set) ((java.util.Properties) System.getProperties().clone()).keySet()); // TODO-Java6: s/keySet()/stringPropertyNames()/
        if (overrideProps != null)
            names.addAll((Set<String>) (Set) overrideProps.keySet()); // TODO-Java6: s/keySet()/stringPropertyNames()/
        return names;
    }

    /**
     * Access the configuration attributes of this context, listing the properties
     * provided during the context construction, as well as the ones included in
     * System.getProperties.
     *
     * @return new Properties with system and context properties
     */
    public Properties getProperties() {
        // clone to avoid ConcurrentModificationException
        Properties props = new Properties();
        props.putAll((java.util.Properties)System.getProperties().clone());
        props.putAll(overrideProps);
        return props;
    }

    /**
     *  WARNING - Shutdown tasks are not executed in an I2PAppContext.
     *  You must be in a RouterContext for the tasks to be executed
     *  at teardown.
     *  This method moved from Router in 0.7.1 so that clients
     *  may use it without depending on router.jar.
     */
    public void addShutdownTask(Runnable task) {
        shutdownTasks.add(task);
    }

    /**
     *  @return an unmodifiable Set
     */
    public Set<Runnable> getShutdownTasks() {
        return Collections.unmodifiableSet(shutdownTasks);
    }

    /**
     *  Use this instead of context instanceof CoreContext
     */
    public boolean isConsciousContext() {
        return false;
    }

    /**
     * The statistics component with which we can track various events
     * over time.
     */
    public StatManager statManager() {
        if (!statManagerInitialized)
            initializeStatManager();
        return statManager;
    }

    private void initializeStatManager() {
        synchronized (lock2) {
            if (statManager == null)
                statManager = new StatManager(this);
            statManagerInitialized = true;
        }
    }

    /**
     * Query the log manager for this context, which may in turn have its own
     * set of configuration settings (loaded from the context's properties).
     * Each context's logManager keeps its own isolated set of Log instances with
     * their own log levels, output locations, and rotation configuration.
     */
    public LogManager logManager() {
        if (!logManagerInitialized)
            initializeLogManager();
        return logManager;
    }

    private void initializeLogManager() {
        synchronized (lock4) {
            if (logManager == null)
                logManager = new LogManager(this);
            logManagerInitialized = true;
        }
    }

    /**
     *  Is the wrapper present?
     */
    public boolean hasWrapper() {
        return System.getProperty("wrapper.version") != null;
    }

    /**
     * Use instead of SimpleTimer2.getInstance()
     * @since 0.9 to replace static instance in the class
     */
    public SimpleTimer simpleTimer() {
        if (!simpleTimerInitialized)
            initializeSimpleTimer();
        return simpleTimer;
    }

    private void initializeSimpleTimer() {
        synchronized (lock3) {
            if (simpleTimer == null)
                simpleTimer = new SimpleTimer(this);
            simpleTimerInitialized = true;
        }
    }

}
