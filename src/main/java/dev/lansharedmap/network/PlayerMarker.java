package dev.lansharedmap.network;

import java.util.UUID;

public record PlayerMarker(UUID id, String name, String dimension, double x, double y, double z, float yaw) {
}
