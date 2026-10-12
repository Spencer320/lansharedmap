package dev.lansharedmap.client.map;

import dev.lansharedmap.map.ChunkTile;
import dev.lansharedmap.network.PlayerMarker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

/**
 * Client copy of the server's explored-chunk set. Cleared when leaving a world.
 */
public final class ClientMapState {
	public static final ClientMapState INSTANCE = new ClientMapState();

	private String worldId = "";
	private boolean hello;
	private boolean snapshotRequested;
	private final Map<String, Map<Long, int[]>> dimensions = new HashMap<>();
	private List<PlayerMarker> players = List.of();
	private final List<Waypoint> waypoints = new ArrayList<>();

	private ClientMapState() {
	}

	public void clear() {
		this.worldId = "";
		this.hello = false;
		this.snapshotRequested = false;
		this.dimensions.clear();
		this.players = List.of();
		this.waypoints.clear();
	}

	public void onHello(String worldId) {
		boolean sameWorld = worldId.equals(this.worldId);
		this.worldId = worldId;
		this.hello = true;
		this.dimensions.clear();
		if (!sameWorld) {
			this.waypoints.clear();
			this.waypoints.addAll(WaypointStore.load(worldId));
		}
	}

	public void addChunks(List<ChunkTile> tiles) {
		for (ChunkTile tile : tiles) {
			this.dimensions
				.computeIfAbsent(tile.dimension(), ignored -> new HashMap<>())
				.put(ChunkPos.pack(tile.chunkX(), tile.chunkZ()), tile.colors());
		}
	}

	public void setPlayers(List<PlayerMarker> players) {
		this.players = List.copyOf(players);
	}

	public boolean hasHello() {
		return this.hello;
	}

	public boolean snapshotRequested() {
		return this.snapshotRequested;
	}

	public void markSnapshotRequested() {
		this.snapshotRequested = true;
	}

	public String worldId() {
		return this.worldId;
	}

	public Map<Long, int[]> chunks(String dimension) {
		return this.dimensions.getOrDefault(dimension, Map.of());
	}

	public int chunkCount(String dimension) {
		return this.chunks(dimension).size();
	}

	public List<PlayerMarker> players() {
		return this.players;
	}

	public List<Waypoint> waypoints() {
		return this.waypoints;
	}

	public void addWaypoint(String dimension, int x, int z) {
		int index = 1;
		for (Waypoint waypoint : this.waypoints) {
			if (dimension.equals(waypoint.dimension())) {
				index++;
			}
		}
		String name = Component.translatable("lansharedmap.waypoint.default", index).getString();
		this.waypoints.add(new Waypoint(name, dimension, x, z));
		WaypointStore.save(this.worldId, this.waypoints);
	}

	public void removeWaypoint(Waypoint waypoint) {
		if (this.waypoints.remove(waypoint)) {
			WaypointStore.save(this.worldId, this.waypoints);
		}
	}

	public Waypoint nearest(String dimension, double x, double z, double maxDistance) {
		Waypoint best = null;
		double bestDistance = maxDistance * maxDistance;
		for (Waypoint waypoint : this.waypoints) {
			if (!dimension.equals(waypoint.dimension())) {
				continue;
			}
			double dx = waypoint.x() + 0.5 - x;
			double dz = waypoint.z() + 0.5 - z;
			double distance = dx * dx + dz * dz;
			if (distance <= bestDistance) {
				bestDistance = distance;
				best = waypoint;
			}
		}
		return best;
	}
}
