package dev.abdelrahman.rankcorex.models;

import dev.abdelrahman.rankcorex.utils.TimeUtils;

import java.util.List;
import java.util.UUID;

public class RankData {

    private final String name;
    private final String prefix;
    private final String suffix;
    private final int weight;
    private final boolean isDefault;
    private final List<String> permissions;

    public RankData(String name, String prefix, String suffix, int weight, boolean isDefault, List<String> permissions) {
        this.name = name;
        this.prefix = prefix;
        this.suffix = suffix;
        this.weight = weight;
        this.isDefault = isDefault;
        this.permissions = permissions;
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix != null ? prefix : "";
    }

    public String getSuffix() {
        return suffix != null ? suffix : "";
    }

    public int getWeight() {
        return weight;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    @Override
    public String toString() {
        return "RankData{" +
                "name='" + name + '\'' +
                ", weight=" + weight +
                ", default=" + isDefault +
                '}';
    }
}