package dev.lansharedmap.client.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.lansharedmap.LanSharedMap;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class WaypointStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private WaypointStore() {
	}

	public static List<Waypoint> load(String worldId) {
		Path path = path(worldId);
		if (!Files.exists(path)) {
			return new ArrayList<>();
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			Waypoint[] array = GSON.fromJson(reader, Waypoint[].class);
			List<Waypoint> waypoints = new ArrayList<>();
			if (array != null) {
				for (Waypoint waypoint : array) {
					if (waypoint != null && waypoint.name() != null && waypoint.dimension() != null) {
						waypoints.add(waypoint);
					}
				}
			}
			return waypoints;
		} catch (IOException | JsonSyntaxException exception) {
			LanSharedMap.LOGGER.warn("Could not read waypoints from {}", path, exception);
			return new ArrayList<>();
		}
	}

	public static void save(String worldId, List<Waypoint> waypoints) {
		if (worldId == null || worldId.isBlank()) {
			return;
		}
		Path path = path(worldId);
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(waypoints));
		} catch (IOException exception) {
			LanSharedMap.LOGGER.warn("Could not write waypoints to {}", path, exception);
		}
	}

	private static Path path(String worldId) {
		String safe = worldId.replaceAll("[^a-zA-Z0-9._-]", "_");
		if (safe.length() > 64) {
			safe = safe.substring(0, 64);
		}
		String file = Integer.toUnsignedString(worldId.hashCode()) + "-" + safe + ".json";
		return FabricLoader.getInstance().getConfigDir().resolve("lansharedmap").resolve(file);
	}
}
