package io.github.meshtactic.ui;

public class UuidData {
    // Instance fields (removed 'static')
    public String uuid;
    public int isDeleted; // 0 for not deleted, 1 for soft-deleted

    public UuidData(String uuid, int isDeleted) {
        this.uuid = uuid;
        this.isDeleted = isDeleted;
    }

    public String getUuid() {
        return this.uuid;
    }

    public int getIsDeleted() {
        return this.isDeleted;
    }

    public boolean isMarkedAsDeleted() {
        return this.isDeleted == 1;
    }

}
