package dev.lansharedmap.network;

import dev.lansharedmap.LanSharedMap;
import dev.lansharedmap.map.ChunkTile;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MapChunksPayload(List<ChunkTile> chunks) implements CustomPacketPayload {
	public MapChunksPayload {
		chunks = List.copyOf(chunks);
	}

	public static final Identifier ID = LanSharedMap.id("map_chunks");
	public static final Type<MapChunksPayload> TYPE = new Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, MapChunksPayload> CODEC = StreamCodec.of(
		(buf, payload) -> PacketIO.writeTiles(buf, payload.chunks()),
		buf -> new MapChunksPayload(PacketIO.readTiles(buf))
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
