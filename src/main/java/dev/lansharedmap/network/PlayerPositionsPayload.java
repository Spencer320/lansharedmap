package dev.lansharedmap.network;

import dev.lansharedmap.LanSharedMap;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PlayerPositionsPayload(List<PlayerMarker> players) implements CustomPacketPayload {
	public PlayerPositionsPayload {
		players = List.copyOf(players);
	}

	public static final Identifier ID = LanSharedMap.id("player_positions");
	public static final Type<PlayerPositionsPayload> TYPE = new Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPositionsPayload> CODEC = StreamCodec.of(
		(buf, payload) -> PacketIO.writeMarkers(buf, payload.players()),
		buf -> new PlayerPositionsPayload(PacketIO.readMarkers(buf))
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
