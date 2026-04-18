package io.quarkiverse.fluss;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.apache.fluss.row.InternalRow;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;

/**
 * A {@link Message} implementation wrapping an {@link InternalRow} received from a Fluss table.
 */
public class FlussMessage implements Message<InternalRow> {

    private final InternalRow payload;
    private final Metadata metadata;
    private final Supplier<CompletionStage<Void>> ack;

    public FlussMessage(InternalRow payload, FlussMessageMetadata flussMetadata) {
        this(payload, flussMetadata, () -> CompletableFuture.completedFuture(null));
    }

    public FlussMessage(InternalRow payload, FlussMessageMetadata flussMetadata, Supplier<CompletionStage<Void>> ack) {
        this.payload = payload;
        this.metadata = Metadata.of(flussMetadata);
        this.ack = ack;
    }

    @Override
    public InternalRow getPayload() {
        return payload;
    }

    @Override
    public Metadata getMetadata() {
        return metadata;
    }

    @Override
    public Supplier<CompletionStage<Void>> getAck() {
        return ack;
    }
}
