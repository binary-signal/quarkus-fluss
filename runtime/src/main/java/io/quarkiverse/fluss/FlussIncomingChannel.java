package io.quarkiverse.fluss;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.fluss.client.Connection;
import org.apache.fluss.client.ConnectionFactory;
import org.apache.fluss.client.admin.OffsetSpec;
import org.apache.fluss.client.table.Table;
import org.apache.fluss.client.table.scanner.ScanRecord;
import org.apache.fluss.client.table.scanner.log.LogScanner;
import org.apache.fluss.client.table.scanner.log.ScanRecords;
import org.apache.fluss.config.Configuration;
import org.apache.fluss.metadata.TableBucket;
import org.apache.fluss.metadata.TablePath;
import org.eclipse.microprofile.reactive.messaging.Message;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;

/**
 * Reads records from a Fluss Log Table and emits them as a {@link Multi} of {@link Message}.
 */
public class FlussIncomingChannel {

    private final Connection connection;
    private final Table table;
    private final LogScanner scanner;
    private final TablePath tablePath;
    private final Duration pollTimeout;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Multi<Message<?>> stream;

    public FlussIncomingChannel(org.eclipse.microprofile.config.Config config) {
        String bootstrapServers = config.getOptionalValue("bootstrap-servers", String.class).orElse("localhost:9123");
        String database = config.getOptionalValue("database", String.class).orElse("fluss");
        String tableName = config.getValue("table", String.class);
        int pollTimeoutMs = config.getOptionalValue("poll-timeout", Integer.class).orElse(100);
        String offsetMode = config.getOptionalValue("offset", String.class).orElse("full");

        this.pollTimeout = Duration.ofMillis(pollTimeoutMs);
        this.tablePath = TablePath.of(database, tableName);

        Configuration conf = new Configuration();
        conf.setString("bootstrap.servers", bootstrapServers);

        this.connection = ConnectionFactory.createConnection(conf);
        this.table = connection.getTable(tablePath);

        // Create scanner with optional column projection
        var scanBuilder = table.newScan();
        config.getOptionalValue("columns", String.class).ifPresent(cols -> {
            String[] columnArray = cols.split(",");
            List<String> columnList = new ArrayList<>();
            for (String col : columnArray) {
                columnList.add(col.trim());
            }
            scanBuilder.project(columnList);
        });
        this.scanner = scanBuilder.createLogScanner();

        // Subscribe to all buckets from the configured starting position
        int numBuckets = table.getTableInfo().getNumBuckets();
        switch (offsetMode.toLowerCase()) {
            case "full":
            case "earliest":
                for (int i = 0; i < numBuckets; i++) {
                    scanner.subscribeFromBeginning(i);
                }
                break;
            case "latest":
                subscribeBucketsAtOffsets(numBuckets, new OffsetSpec.LatestSpec());
                break;
            case "timestamp":
                long timestamp = config.getValue("offset.timestamp", Long.class);
                subscribeBucketsAtOffsets(numBuckets, new OffsetSpec.TimestampSpec(timestamp));
                break;
            default:
                throw new IllegalArgumentException("Invalid offset mode: '" + offsetMode
                        + "'. Supported values: full, earliest, latest, timestamp");
        }

        this.stream = Multi.createBy()
                .repeating()
                .supplier(this::pollRecords)
                .until(ignored -> closed.get())
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem()
                .disjoint();
    }

    private void subscribeBucketsAtOffsets(int numBuckets, OffsetSpec offsetSpec) {
        List<Integer> buckets = new ArrayList<>();
        for (int i = 0; i < numBuckets; i++) {
            buckets.add(i);
        }
        try {
            Map<Integer, Long> offsets = connection
                    .getAdmin()
                    .listOffsets(tablePath, buckets, offsetSpec)
                    .all()
                    .get();
            offsets.forEach(scanner::subscribe);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching offsets", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to fetch offsets", e.getCause());
        }
    }

    private List<Message<?>> pollRecords() {
        if (closed.get()) {
            return List.of();
        }
        ScanRecords scanRecords = scanner.poll(pollTimeout);
        List<Message<?>> messages = new ArrayList<>();
        for (TableBucket bucket : scanRecords.buckets()) {
            for (ScanRecord record : scanRecords.records(bucket)) {
                FlussMessageMetadata metadata = new FlussMessageMetadata(tablePath, bucket, record.logOffset(),
                        record.getChangeType());
                messages.add(new FlussMessage(record.getRow(), metadata));
            }
        }
        return messages;
    }

    public Multi<Message<?>> getStream() {
        return stream;
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                scanner.close();
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
