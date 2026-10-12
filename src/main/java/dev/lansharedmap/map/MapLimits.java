package dev.lansharedmap.map;

public final class MapLimits {
	/** Bump when {@code SurfaceSampler} output should replace previously saved tiles. */
	public static final int COLOR_VERSION = 2;

	public static final int BLOCKS_PER_CHUNK = 16;
	public static final int COLORS_PER_CHUNK = BLOCKS_PER_CHUNK * BLOCKS_PER_CHUNK;
	/** Chebyshev distance, in chunks, around a player that counts as explored. */
	public static final int EXPLORE_RADIUS = 3;
	public static final int SAMPLES_PER_PLAYER_PER_TICK = 6;
	public static final int CHUNKS_PER_PACKET = 20;
	public static final int POSITION_INTERVAL_TICKS = 10;
	public static final int MAX_CHUNKS_PER_PACKET = 64;
	public static final int MAX_PLAYERS_PER_PACKET = 256;
	public static final int MAX_CHUNK_COORD = 3_000_000;

	private MapLimits() {
	}
}
