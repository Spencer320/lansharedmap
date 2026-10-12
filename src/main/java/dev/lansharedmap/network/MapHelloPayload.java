package dev.lansharedmap.network;

import dev.lansharedmap.LanSharedMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MapHelloPayload(String worldId) implements CustomPacketPayload {
	public static final Identifier ID = LanSharedMap.id("map_hello");
	public static final Type<MapHelloPayload> TYPE = new Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, MapHelloPayload> CODEC = StreamCodec.of(
		(buf, payload) -> buf.writeUtf(payload.worldId(), 160),
		buf -> new MapHelloPayload(PacketIO.readWorldId(buf))
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
