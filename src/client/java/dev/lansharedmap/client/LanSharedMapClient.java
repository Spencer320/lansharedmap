package dev.lansharedmap.client;

import dev.lansharedmap.LanSharedMap;
import dev.lansharedmap.client.map.ClientMapState;
import dev.lansharedmap.client.screen.WorldMapScreen;
import dev.lansharedmap.network.MapChunksPayload;
import dev.lansharedmap.network.MapHelloPayload;
import dev.lansharedmap.network.PlayerPositionsPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

public final class LanSharedMapClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(LanSharedMap.id("map"));
	public static final KeyMapping OPEN_MAP = KeyMappingHelper.registerKeyMapping(new KeyMapping(
		"key.lansharedmap.open_map",
		InputConstants.Type.KEYBOARD,
		InputConstants.KEY_M,
		CATEGORY
	));

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(MapHelloPayload.TYPE, (payload, context) ->
			context.client().execute(() -> ClientMapState.INSTANCE.onHello(payload.worldId()))
		);
		ClientPlayNetworking.registerGlobalReceiver(MapChunksPayload.TYPE, (payload, context) ->
			context.client().execute(() -> ClientMapState.INSTANCE.addChunks(payload.chunks()))
		);
		ClientPlayNetworking.registerGlobalReceiver(PlayerPositionsPayload.TYPE, (payload, context) ->
			context.client().execute(() -> ClientMapState.INSTANCE.setPlayers(payload.players()))
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientMapState.INSTANCE::clear));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_MAP.consumeClick()) {
				if (client.gui.screen() instanceof WorldMapScreen) {
					client.gui.setScreen(null);
				} else if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(new WorldMapScreen());
				}
			}
		});
	}
}
