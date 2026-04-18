# Quarkus Fluss

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.fluss/quarkus-messaging-fluss?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.fluss/quarkus-messaging-fluss-parent)

A [Quarkus](https://quarkus.io/) extension that provides
a [SmallRye Reactive Messaging](https://smallrye.io/smallrye-reactive-messaging/)
connector for [Apache Fluss](https://fluss.apache.org/) (Incubating), a
streaming storage system built for real-time analytics.

Use standard `@Incoming` and `@Outgoing` annotations to consume from and produce
to Fluss Log Tables.

## Prerequisites

- Java 17+
- Maven 3.9+
- A running Apache Fluss cluster (default: `localhost:9123`)

> **JVM flag required:** The Fluss client uses Apache Arrow internally, which
> requires `--add-opens=java.base/java.nio=ALL-UNNAMED` at JVM startup. Add this
> to your Quarkus Maven plugin config:
> ```xml
> <plugin>
>     <groupId>io.quarkus</groupId>
>     <artifactId>quarkus-maven-plugin</artifactId>
>     <configuration>
>         <jvmArgs>--add-opens=java.base/java.nio=ALL-UNNAMED</jvmArgs>
>     </configuration>
> </plugin>
> ```

## Installation

Add the runtime dependency to your Quarkus application:

```xml
<dependency>
    <groupId>io.quarkiverse.fluss</groupId>
    <artifactId>quarkus-messaging-fluss</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Configuration

Configure channels in your `application.properties` using the `smallrye-fluss`
connector name.

### Incoming (consuming from Fluss)

```properties
mp.messaging.incoming.my-channel.connector=smallrye-fluss
mp.messaging.incoming.my-channel.bootstrap-servers=localhost:9123
mp.messaging.incoming.my-channel.database=my_db
mp.messaging.incoming.my-channel.table=events
```

### Outgoing (producing to Fluss)

```properties
mp.messaging.outgoing.my-channel.connector=smallrye-fluss
mp.messaging.outgoing.my-channel.bootstrap-servers=localhost:9123
mp.messaging.outgoing.my-channel.database=my_db
mp.messaging.outgoing.my-channel.table=events
```

### Configuration Reference

| Property            | Type   | Default          | Direction | Description                                             |
|---------------------|--------|------------------|-----------|---------------------------------------------------------|
| `connector`         | String |                  | Both      | Must be `smallrye-fluss`                                |
| `bootstrap-servers` | String | `localhost:9123` | Both      | Fluss cluster bootstrap address                         |
| `database`          | String | `fluss`          | Both      | Fluss database name                                     |
| `table`             | String | *(required)*     | Both      | Fluss table name                                        |
| `offset`            | String | `full`           | Incoming  | Startup mode: `full`, `earliest`, `latest`, `timestamp` |
| `offset.timestamp`  | long   |                  | Incoming  | Epoch millis, required when `offset=timestamp`          |
| `columns`           | String |                  | Incoming  | Comma-separated column names for projection             |
| `poll-timeout`      | int    | `100`            | Incoming  | Poll timeout in milliseconds                            |
| `batch-size`        | int    | `100`            | Outgoing  | Number of records before flushing                       |

## Usage

### Consuming messages

Incoming messages carry an `InternalRow` payload representing a row from a Fluss table.

```java
@ApplicationScoped
public class FlussConsumer {

    @Incoming("events")
    public void consume(InternalRow row) {
        String id    = row.getString(0).toString();
        int    value = row.getInt(1);
        System.out.println("Received: id=" + id + ", value=" + value);
    }
}
```

### Accessing Fluss metadata

Each message includes `FlussMessageMetadata` with the table path, bucket, offset
and change type.

```java
@ApplicationScoped
public class FlussConsumerWithMetadata {

    @Incoming("events")
    public CompletionStage<Void> consume(Message<InternalRow> message) {
        message.getMetadata(FlussMessageMetadata.class).ifPresent(meta -> {
            System.out.println("Table: " + meta.getTablePath());
            System.out.println("Bucket: " + meta.getBucketId());
            System.out.println("Offset: " + meta.getOffset());
            System.out.println("ChangeType: " + meta.getChangeType());
        });
        return message.ack();
    }
}
```

### Producing messages

Outgoing messages must carry a `GenericRow` (or any `InternalRow`) payload
matching the target table schema.

```java
@ApplicationScoped
public class FlussProducer {

    @Outgoing("output")
    public Multi<GenericRow> produce() {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(1))
                .map(tick -> {
                    GenericRow row = new GenericRow(3);
                    row.setField(0, BinaryString.fromString("event-" + tick));
                    row.setField(1, tick.intValue());
                    row.setField(2, System.currentTimeMillis());
                    return row;
                });
    }
}
```

### Start reading position

The `offset` property controls where the connector begins reading.

| Mode        | Description                                                                                                   |
|-------------|---------------------------------------------------------------------------------------------------------------|
| `full`      | **Default.** Reads from the earliest offset. For PK tables, reads from the earliest changelog offset.         |
| `earliest`  | Starts reading from the earliest available offset.                                                            |
| `latest`    | Starts reading from the latest offset -- only new records arriving after startup will be consumed.            |
| `timestamp` | Starts reading from the offset closest to a given timestamp. Requires `offset.timestamp` (epoch milliseconds).|

```properties
# Read only new records
mp.messaging.incoming.events.offset=latest

# Read from a specific point in time
mp.messaging.incoming.events.offset=timestamp
mp.messaging.incoming.events.offset.timestamp=1713200000000
```

### Column projection

Reduce network overhead by fetching only the columns you need:

```properties
mp.messaging.incoming.events.columns=sensor_id,temperature,timestamp
```

The `InternalRow` you receive will only contain the projected columns (indexed
starting at 0 in projection order).

## How It Works

- **Incoming:** The connector creates a `LogScanner` that subscribes to all
  buckets of the configured table at the position determined by the `offset`
  property. It polls for `ScanRecords` on a worker thread and emits each row as a
  `Message<InternalRow>`.

- **Outgoing:** The connector creates an `AppendWriter` for the configured table.
  Each incoming message payload (`InternalRow`/`GenericRow`) is appended and
  flushed in batches based on `batch-size`.

## Current Limitations

- **No full snapshot read for PK Tables** -- the `full` offset mode does not yet
  read the initial snapshot for Primary Key Tables; it falls back to `earliest`
  (changelog only)
- **No consumer offset tracking** -- offsets are not persisted between restarts
- **No health checks** -- MicroProfile Health integration is not yet implemented
- **No dev services** -- no automatic Fluss container startup in dev mode

## Documentation

The documentation for this extension is published at <https://docs.quarkiverse.io/quarkus-fluss/dev/>.

## License

Apache License 2.0
