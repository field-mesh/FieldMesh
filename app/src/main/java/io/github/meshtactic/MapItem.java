package io.github.meshtactic;

public abstract class MapItem {
    private double latitude;
    private double longitude;
    private int elevation;
    private String description;
    private String uniqueId;

    public MapItem() {
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public int getElevation() {
        return elevation;
    }

    public void setElevation(int elevation) {
        this.elevation = elevation;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        if (description != null && description.matches("^[A-Z0-9]{0,10}$")) {
            this.description = description;
        } else {
            throw new IllegalArgumentException(String.valueOf(R.string.charMaxSizeWarning));
        }
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }
}