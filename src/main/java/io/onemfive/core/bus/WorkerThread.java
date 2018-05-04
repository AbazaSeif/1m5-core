package io.onemfive.core.bus;

import io.onemfive.core.BaseService;
import io.onemfive.core.MessageConsumer;
import io.onemfive.core.client.ClientAppManager;
import io.onemfive.core.orchestration.OrchestrationService;
import io.onemfive.core.orchestration.routes.SimpleRoute;
import io.onemfive.core.util.AppThread;
import io.onemfive.data.Envelope;
import io.onemfive.data.Route;

import java.util.Map;

/**
 * Worker Thread for moving messages from clients to the message channel and then to services and back.
 *
 * @author objectorange
 */
final class WorkerThread extends AppThread {

    private MessageChannel channel;
    private ClientAppManager clientAppManager;
    private Map<String, BaseService> services;

    public WorkerThread(MessageChannel channel, ClientAppManager clientAppManager, Map<String, BaseService> services) {
        super();
        this.channel = channel;
        this.clientAppManager = clientAppManager;
        this.services = services;
    }

    @Override
    public void run() {
        System.out.println(WorkerThread.class.getSimpleName()+": "+Thread.currentThread().getName() + ": Waiting for channel to return message...");
        Envelope envelope = channel.receive();
        System.out.println(WorkerThread.class.getSimpleName()+": "+Thread.currentThread().getName() + ": Envelope received from channel");
        if (envelope.getHeader(Envelope.CLIENT_REPLY) != null) {
            // Service Reply to client
            System.out.println(WorkerThread.class.getSimpleName()+": "+Thread.currentThread().getName() + ": Requesting client notify...");
            clientAppManager.notify(envelope);
        } else {
            MessageConsumer consumer = null;
            Route route = (Route)envelope.getHeader(Envelope.ROUTE);
            if(route == null) {
                // When no route is provided, forward to Orchestration service.
                System.out.println(WorkerThread.class.getSimpleName()+": "+Thread.currentThread().getName() + ": Route not found in header; forward to OrchestrationService to determine route.");
                envelope.setHeader(Envelope.ROUTE, new SimpleRoute(OrchestrationService.class.getName(), Envelope.NONE));
                consumer = services.get(OrchestrationService.class.getName());
            } else if(envelope.getHeader(Envelope.REPLY) != null) {
                // Reply from service -> forward to orchestration service to determine next route
                System.out.println(WorkerThread.class.getSimpleName()+": "+Thread.currentThread().getName() + ": Route found in header; reply from service; forward to OrchestrationService to determine next step...");
                consumer = services.get(OrchestrationService.class.getName());
            } else {
                System.out.println(WorkerThread.class.getSimpleName()+": "+Thread.currentThread().getName() + ": Route found in header; initial request; forward to Service specified in route...");
                consumer = services.get(route.getService());
                if (consumer == null) {
                    // Service name provided is not registered.
                    // Likely from a domain-specific api
                    // Send to Orchestration Service to determine.
                    System.out.println(WorkerThread.class.getSimpleName()+": "+Thread.currentThread().getName() + ": Route found in header; initial request; Service not found; forward to OrchestrationService to determine service...");
                    consumer = services.get(OrchestrationService.class.getName());
                }
            }
            boolean received = false;
            int maxSendAttempts = 3;
            int sendAttempts = 0;
            int waitBetweenMillis = 1000;
            while (!received && sendAttempts < maxSendAttempts) {
                if (consumer.receive(envelope)) {
                    System.out.println(WorkerThread.class.getSimpleName()+": "+Thread.currentThread().getName() + ": Envelope received by service, acknowledging with channel...");
                    channel.ack(envelope);
                    System.out.println(WorkerThread.class.getSimpleName()+": "+Thread.currentThread().getName() + ": Channel Acknowledged.");
                    received = true;
                } else {
                    synchronized (this) {
                        try {
                            this.wait(waitBetweenMillis);
                        } catch (InterruptedException e) {

                        }
                    }
                }
                sendAttempts++;
            }
        }
    }
}
