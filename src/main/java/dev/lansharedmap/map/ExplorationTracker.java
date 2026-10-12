package dev.lansharedmap.map;

import dev.lansharedmap.network.MapChunksPayload;
import dev.lansharedmap.network.MapHelloPayload;
import dev.lansharedmap.network.PlayerMarker;
import dev.lansharedmap.network.PlayerPositionsPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Records exploration from where players actually are, then pushes snapshots and deltas.
 * Clients cannot submit chunks; the server is the only writer.
 */
public final class ExplorationTracker {
	private ExplorationTracker() {
	}

	public static void onLevelTick(ServerLevel level) {
		ExplorationMapData data = ExplorationMapData.get(level);
		String dimension = Dimensions.id(level);
		List<ChunkTile> fresh = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			sampleAround(level, data, dimension, player, fresh);
		}
		MinecraftServer server = level.getServer();
		if (server != null && !fresh.isEmpty()) {
			broadcast(server, fresh);
		}
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTickCount() % MapLimits.POSITION_INTERVAL_TICKS != 0) {
			return;
		}
		List<PlayerMarker> markers = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			markers.add(new PlayerMarker(
				player.getUUID(),
				player.getName().getString(),
				Dimensions.id(level),
				player.getX(),
				player.getY(),
				player.getZ(),
				player.getYRot()
			));
		}
		PlayerPositionsPayload payload = new PlayerPositionsPayload(markers);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, PlayerPositionsPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	public static void sendSnapshot(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, MapHelloPayload.TYPE)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		ServerPlayNetworking.send(player, new MapHelloPayload(Dimensions.worldId(server)));
		List<ChunkTile> batch = new ArrayList<>(MapLimits.CHUNKS_PER_PACKET);
		int total = 0;
		for (ServerLevel level : server.getAllLevels()) {
			String dimension = Dimensions.id(level);
			for (ChunkTile tile : ExplorationMapData.get(level).toTiles(dimension)) {
				batch.add(tile);
				total++;
				if (batch.size() >= MapLimits.CHUNKS_PER_PACKET) {
					flush(player, batch);
				}
			}
		}
		flush(player, batch);
		dev.lansharedmap.LanSharedMap.LOGGER.debug("Sent shared map snapshot ({} chunks) to {}", total, player.getName().getString());
	}

	private static void sampleAround(ServerLevel level, ExplorationMapData data, String dimension, ServerPlayer player, List<ChunkTile> fresh) {
		BlockPos pos = player.blockPosition();
		int centerX = pos.getX() >> 4;
		int centerZ = pos.getZ() >> 4;
		int samples = 0;
		for (int dz = -MapLimits.EXPLORE_RADIUS; dz <= MapLimits.EXPLORE_RADIUS; dz++) {
			for (int dx = -MapLimits.EXPLORE_RADIUS; dx <= MapLimits.EXPLORE_RADIUS; dx++) {
				if (samples >= MapLimits.SAMPLES_PER_PLAYER_PER_TICK) {
					return;
				}
				int chunkX = centerX + dx;
				int chunkZ = centerZ + dz;
				if (!level.hasChunk(chunkX, chunkZ) || !data.needsSample(chunkX, chunkZ)) {
					continue;
				}
				int[] colors = SurfaceSampler.sample(level, chunkX, chunkZ);
				if (data.add(chunkX, chunkZ, colors)) {
					fresh.add(new ChunkTile(dimension, chunkX, chunkZ, colors));
					samples++;
				}
			}
		}
	}

	private static void broadcast(MinecraftServer server, List<ChunkTile> tiles) {
		for (int offset = 0; offset < tiles.size(); offset += MapLimits.CHUNKS_PER_PACKET) {
			int end = Math.min(tiles.size(), offset + MapLimits.CHUNKS_PER_PACKET);
			MapChunksPayload payload = new MapChunksPayload(tiles.subList(offset, end));
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (ServerPlayNetworking.canSend(player, MapChunksPayload.TYPE)) {
					ServerPlayNetworking.send(player, payload);
				}
			}
		}
	}

	private static void flush(ServerPlayer player, List<ChunkTile> batch) {
		if (batch.isEmpty()) {
			return;
		}
		ServerPlayNetworking.send(player, new MapChunksPayload(batch));
		batch.clear();
	}
}
