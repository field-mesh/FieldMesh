package io.github.meshtactic;

public class DecodedDBHash {
    public final long timestamp;
    public final String dbHash;
    public final long databaseSize;

    public DecodedDBHash(long timestamp, String dbHash, long databaseSize) {
        this.timestamp = timestamp;
        this.dbHash = dbHash;
        this.databaseSize = databaseSize;
    }

    public String getDbHash() {
        return dbHash;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getDatabaseSize() { // Getter for the new field
        return databaseSize;
    }
}