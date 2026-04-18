package io.quarkiverse.fluss;

import org.apache.fluss.metadata.TableBucket;
import org.apache.fluss.metadata.TablePath;
import org.apache.fluss.record.ChangeType;

/**
 * Metadata associated with a message received from a Fluss table.
 */
public class FlussMessageMetadata {

    private final TablePath tablePath;
    private final TableBucket tableBucket;
    private final long offset;
    private final ChangeType changeType;

    public FlussMessageMetadata(TablePath tablePath, TableBucket tableBucket, long offset, ChangeType changeType) {
        this.tablePath = tablePath;
        this.tableBucket = tableBucket;
        this.offset = offset;
        this.changeType = changeType;
    }

    public TablePath getTablePath() {
        return tablePath;
    }

    public TableBucket getTableBucket() {
        return tableBucket;
    }

    public int getBucketId() {
        return tableBucket.getBucket();
    }

    public long getOffset() {
        return offset;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    @Override
    public String toString() {
        return "FlussMessageMetadata{" + "tablePath="
                + tablePath + ", bucket="
                + tableBucket.getBucket() + ", offset="
                + offset + ", changeType=" + changeType + '}';
    }
}
