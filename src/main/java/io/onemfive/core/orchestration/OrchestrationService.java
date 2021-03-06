package io.onemfive.core.orchestration;

import io.onemfive.core.BaseService;
import io.onemfive.core.MessageProducer;
import io.onemfive.core.ServiceStatus;
import io.onemfive.core.ServiceStatusListener;
import io.onemfive.data.*;

import java.util.Properties;
import java.util.logging.Logger;

/**
 * Orchestrating services based on configurable route patterns.
 *
 * @author objectorange
 */
public class OrchestrationService extends BaseService {

    private static final Logger LOG = Logger.getLogger(OrchestrationService.class.getName());

    private int activeRoutes = 0;
    private int remainingRoutes = 0;

    private final Object lock = new Object();

    public OrchestrationService(MessageProducer producer, ServiceStatusListener serviceStatusListener) {
        super(producer, serviceStatusListener);
        orchestrator = true;
    }

    /**
     * If service is Orchestration and there is no Route, then the service needs to figure out what route to take.
     * If service is Orchestration and there is a Route, then the service calls the next Route
     *
     * @param e
     */
    @Override
    public void handleDocument(Envelope e) {
        LOG.fine("Received document by Orchestration Service; routing...");
        route(e);
    }

    @Override
    public void handleEvent(Envelope e) {
        LOG.fine("Received event by Orchestration Service; routing...");
        route(e);
    }

    @Override
    public void handleHeaders(Envelope e) {
        LOG.fine("Received headers by Orchestration Service; routing...");
        route(e);
    }

    private void route(Envelope e) {
        if(getServiceStatus() == ServiceStatus.RUNNING) {
            RoutingSlip rs = e.getDynamicRoutingSlip();
            Route route = e.getRoute();
            // Select Next Route and send to channel

            if(!rs.inProgress()) {
                // new slip
                remainingRoutes += rs.numberRemainingRoutes();
                rs.start();
            }
            if(rs.peekAtNextRoute() != null) {
                // slip has routes left, set next route
                route = rs.nextRoute();
                // TODO: Implement URL Router
//                if(OrchestrationService.class.getName().equals(route.getService())) {
                    // URL Router - need to determine what next route is
//                    String commandPath = e.getCommandPath();
//                    if(commandPath != null && !"".equals(commandPath)) {
//                        if(commandPath.startsWith("/ipfs")) {
//                            route = new SimpleRoute(IPFSService.class.getName(), null);
//                        }
//                    }
//                }
                e.setRoute(route);
                reply(e);
                activeRoutes++;
            } else if(route == null || route.routed() || OrchestrationService.class.getName().equals(route.getService())) {
                // no routes left
                if(e.getClient() != null) {
                    // is a client request so flag for reply to client
                    e.setReplyToClient(true);
                    reply(e);
                } else {
                    // not a client request so just end
                    endRoute(e);
                }
                activeRoutes--;
                remainingRoutes--;
            } else {
                // route is not null, hasn't been routed, and is not for Orchestration Service so one-way fire-and-forget -> Send on its way
                reply(e);
                activeRoutes++;
                remainingRoutes++;
            }
        } else {
            LOG.warning("Not running.");
            deadLetter(e);
        }
    }

    @Override
    public boolean start(Properties properties) {
        super.start(properties);
        LOG.info("Starting...");
        updateStatus(ServiceStatus.STARTING);
        activeRoutes = 0;
        remainingRoutes = 0;
        updateStatus(ServiceStatus.RUNNING);
        LOG.info("Started.");
        return true;
    }

    @Override
    public boolean shutdown() {
        super.shutdown();
        LOG.info("Shutting down...");
        updateStatus(ServiceStatus.SHUTTING_DOWN);
        // Give it 3 seconds
        int tries = 1;
        while(remainingRoutes > 0 && tries > 0) {
            waitABit(3 * 1000);
            tries--;
        }
        updateStatus(ServiceStatus.SHUTDOWN);
        LOG.info("Shutdown");
        return true;
    }

    @Override
    public boolean gracefulShutdown() {
        super.gracefulShutdown();
        LOG.info("Gracefully shutting down...");
        updateStatus(ServiceStatus.GRACEFULLY_SHUTTING_DOWN);
        // Give it 30 seconds
        int tries = 10;
        while(remainingRoutes > 0 && tries > 0) {
            waitABit(3 * 1000);
            tries--;
        }
        updateStatus(ServiceStatus.GRACEFULLY_SHUTDOWN);
        LOG.info("Gracefully Shutdown");
        return true;
    }

    private void waitABit(long waitTime) {
        synchronized (lock) {
            try {
                this.wait(waitTime);
            } catch (InterruptedException e) {

            }
        }
    }

}
