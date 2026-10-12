package dev.lansharedmap.network;

import dev.lansharedmap.map.ChunkTile;
import dev.lansharedmap.map.MapLimits;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

final class PacketIO {
	private PacketIO() {
	}

	static void writeTile(FriendlyByteBuf buf, ChunkTile tile) {
		buf.writeUtf(tile.dimension(), 128);
		buf.writeVarInt(tile.chunkX());
		buf.writeVarInt(tile.chunkZ());
		for (int color : tile.colors()) {
			buf.writeInt(color);
		}
	}

	static ChunkTile readTile(FriendlyByteBuf buf) {
		String dimension = readDimension(buf);
		int chunkX = readChunkCoord(buf);
		int chunkZ = readChunkCoord(buf);
		int[] colors = new int[MapLimits.COLORS_PER_CHUNK];
		for (int i = 0; i < colors.length; i++) {
			colors[i] = buf.readInt();
		}
		return new ChunkTile(dimension, chunkX, chunkZ, colors);
	}

	static void writeTiles(FriendlyByteBuf buf, List<ChunkTile> tiles) {
		if (tiles.size() > MapLimits.MAX_CHUNKS_PER_PACKET) {
			throw new IllegalArgumentException("too many chunks in one packet");
		}
		buf.writeVarInt(tiles.size());
		for (ChunkTile tile : tiles) {
			writeTile(buf, tile);
		}
	}

	static List<ChunkTile> readTiles(FriendlyByteBuf buf) {
		int count = buf.readVarInt();
		if (count < 0 || count > MapLimits.MAX_CHUNKS_PER_PACKET) {
			throw new DecoderException("chunk count " + count);
		}
		List<ChunkTile> tiles = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			tiles.add(readTile(buf));
		}
		return tiles;
	}

	static void writeMarker(FriendlyByteBuf buf, PlayerMarker marker) {
		buf.writeUUID(marker.id());
		buf.writeUtf(marker.name(), 64);
		buf.writeUtf(marker.dimension(), 128);
		buf.writeDouble(marker.x());
		buf.writeDouble(marker.y());
		buf.writeDouble(marker.z());
		buf.writeFloat(marker.yaw());
	}

	static PlayerMarker readMarker(FriendlyByteBuf buf) {
		UUID id = buf.readUUID();
		String name = buf.readUtf(64);
		if (name.isBlank()) {
			name = "?";
		}
		String dimension = readDimension(buf);
		double x = buf.readDouble();
		double y = buf.readDouble();
		double z = buf.readDouble();
		float yaw = buf.readFloat();
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
			throw new DecoderException("non-finite player position");
		}
		return new PlayerMarker(id, name, dimension, x, y, z, yaw);
	}

	static void writeMarkers(FriendlyByteBuf buf, List<PlayerMarker> markers) {
		if (markers.size() > MapLimits.MAX_PLAYERS_PER_PACKET) {
			throw new IllegalArgumentException("too many players in one packet");
		}
		buf.writeVarInt(markers.size());
		for (PlayerMarker marker : markers) {
			writeMarker(buf, marker);
		}
	}

	static List<PlayerMarker> readMarkers(FriendlyByteBuf buf) {
		int count = buf.readVarInt();
		if (count < 0 || count > MapLimits.MAX_PLAYERS_PER_PACKET) {
			throw new DecoderException("player count " + count);
		}
		List<PlayerMarker> markers = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			markers.add(readMarker(buf));
		}
		return markers;
	}

	static String readWorldId(FriendlyByteBuf buf) {
		String worldId = buf.readUtf(160);
		if (worldId.isBlank() || worldId.length() > 160) {
			throw new DecoderException("world id");
		}
		return worldId;
	}

	private static String readDimension(FriendlyByteBuf buf) {
		String dimension = buf.readUtf(128);
		int colon = dimension.indexOf(':');
		if (colon <= 0 || colon == dimension.length() - 1) {
			throw new DecoderException("dimension");
		}
		return dimension;
	}

	private static int readChunkCoord(FriendlyByteBuf buf) {
		int coord = buf.readVarInt();
		if (Math.abs(coord) > MapLimits.MAX_CHUNK_COORD) {
			throw new DecoderException("chunk coord");
		}
		return coord;
	}
}
