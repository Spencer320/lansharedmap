package dev.lansharedmap.map;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class Dimensions {
	private Dimensions() {
	}

	public static String id(Level level) {
		return level.dimension().identifier().toString();
	}

	/**
	 * Stable id for waypoint files. The server sends the same string to every client.
	 */
	public static String worldId(MinecraftServer server) {
		String name = server.getWorldData().getLevelName();
		long seed = 0L;
		ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
		if (overworld != null) {
			seed = overworld.getSeed();
		}
		if (name == null || name.isBlank()) {
			name = "world";
		}
		return name + "#" + Long.toUnsignedString(seed);
	}
}
