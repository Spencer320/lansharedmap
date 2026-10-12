package dev.lansharedmap;

import dev.lansharedmap.network.MapNetworking;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LanSharedMap implements ModInitializer {
	public static final String MOD_ID = "lansharedmap";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		MapNetworking.registerCommon();
		LOGGER.info("Lan Shared Map ready (server-side exploration sync)");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
