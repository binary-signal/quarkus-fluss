package io.quarkiverse.fluss;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;

import io.smallrye.reactive.messaging.connector.InboundConnector;
import io.smallrye.reactive.messaging.connector.OutboundConnector;

/**
 * SmallRye Reactive Messaging connector for Apache Fluss.
 * <p>
 * Supports consuming from Fluss Log Tables ({@code @Incoming}) and producing
 * to Fluss Log Tables ({@code @Outgoing}).
 * <p>
 * Configuration prefix: {@code mp.messaging.incoming.<channel>.connector=smallrye-fluss}
 */
@ApplicationScoped
@Connector("smallrye-fluss")
public class FlussConnector implements InboundConnector, OutboundConnector {

    private final List<FlussIncomingChannel> incomingChannels = new CopyOnWriteArrayList<>();
    private final List<FlussOutgoingChannel> outgoingChannels = new CopyOnWriteArrayList<>();

    @Override
    public Flow.Publisher<? extends Message<?>> getPublisher(Config config) {
        FlussIncomingChannel channel = new FlussIncomingChannel(config);
        incomingChannels.add(channel);
        return channel.getStream();
    }

    @Override
    public Flow.Subscriber<? extends Message<?>> getSubscriber(Config config) {
        FlussOutgoingChannel channel = new FlussOutgoingChannel(config);
        outgoingChannels.add(channel);
        return channel;
    }

    @PreDestroy
    void shutdown() {
        for (FlussIncomingChannel channel : incomingChannels) {
            channel.close();
        }
        for (FlussOutgoingChannel channel : outgoingChannels) {
            channel.close();
        }
    }
}
