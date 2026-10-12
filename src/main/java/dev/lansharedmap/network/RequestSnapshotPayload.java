package dev.lansharedmap.network;

import dev.lansharedmap.LanSharedMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestSnapshotPayload() implements CustomPacketPayload {
	public static final RequestSnapshotPayload INSTANCE = new RequestSnapshotPayload();
	public static final Identifier ID = LanSharedMap.id("request_snapshot");
	public static final Type<RequestSnapshotPayload> TYPE = new Type<>(ID);
	public static final StreamCodec<RegistryFriendlyByteBuf, RequestSnapshotPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
