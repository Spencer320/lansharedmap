package dev.lansharedmap.network;

import dev.lansharedmap.map.ExplorationTracker;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class MapNetworking {
	private MapNetworking() {
	}

	public static void registerCommon() {
		PayloadTypeRegistry.clientboundPlay().register(MapHelloPayload.TYPE, MapHelloPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(MapChunksPayload.TYPE, MapChunksPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PlayerPositionsPayload.TYPE, PlayerPositionsPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestSnapshotPayload.TYPE, RequestSnapshotPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(RequestSnapshotPayload.TYPE, (payload, context) ->
			ExplorationTracker.sendSnapshot(context.player())
		);
		ServerPlayerEvents.JOIN.register(ExplorationTracker::sendSnapshot);
		ServerTickEvents.END_LEVEL_TICK.register(ExplorationTracker::onLevelTick);
		ServerTickEvents.END_SERVER_TICK.register(ExplorationTracker::onServerTick);
	}
}
