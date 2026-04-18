package io.quarkiverse.fluss;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.fluss.client.Connection;
import org.apache.fluss.client.ConnectionFactory;
import org.apache.fluss.client.table.Table;
import org.apache.fluss.client.table.writer.AppendWriter;
import org.apache.fluss.config.Configuration;
import org.apache.fluss.metadata.TablePath;
import org.apache.fluss.row.InternalRow;
import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Writes messages to a Fluss Log Table via {@link AppendWriter}.
 * Implements {@link Flow.Subscriber} to receive messages from the reactive messaging framework.
 */
public class FlussOutgoingChannel implements Flow.Subscriber<Message<?>> {

    private final Connection connection;
    private final Table table;
    private final AppendWriter writer;
    private final int batchSize;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    private volatile Flow.Subscription subscription;

    public FlussOutgoingChannel(org.eclipse.microprofile.config.Config config) {
        String bootstrapServers = config.getOptionalValue("bootstrap-servers", String.class).orElse("localhost:9123");
        String database = config.getOptionalValue("database", String.class).orElse("fluss");
        String tableName = config.getValue("table", String.class);
        this.batchSize = config.getOptionalValue("batch-size", Integer.class).orElse(100);

        TablePath tablePath = TablePath.of(database, tableName);

        Configuration conf = new Configuration();
        conf.setString("bootstrap.servers", bootstrapServers);

        this.connection = ConnectionFactory.createConnection(conf);
        this.table = connection.getTable(tablePath);
        this.writer = table.newAppend().createWriter();
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(Message<?> message) {
        try {
            Object payload = message.getPayload();
            if (payload instanceof InternalRow row) {
                writer.append(row);
            } else {
                throw new IllegalArgumentException("Fluss outgoing connector expects InternalRow payload, got: "
                        + payload.getClass().getName());
            }

            int count = pendingCount.incrementAndGet();
            if (count >= batchSize) {
                writer.flush();
                pendingCount.set(0);
            }

            message.ack().toCompletableFuture().join();
        } catch (Exception e) {
            message.nack(e);
        } finally {
            subscription.request(1);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        close();
    }

    @Override
    public void onComplete() {
        close();
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                writer.flush();
            } catch (Exception e) {
                // best effort
            }
            try {
                table.close();
            } catch (Exception e) {
                // best effort
            }
            try {
                connection.close();
            } catch (Exception e) {
                // best effort
            }
        }
    }
}
