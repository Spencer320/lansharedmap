package dev.lansharedmap.map;

import java.util.Arrays;
import java.util.Objects;

/**
 * One explored chunk: dimension id plus a 16×16 surface color, row-major in X then Z.
 * Colors are opaque ARGB.
 */
public record ChunkTile(String dimension, int chunkX, int chunkZ, int[] colors) {
	public ChunkTile {
		Objects.requireNonNull(dimension, "dimension");
		Objects.requireNonNull(colors, "colors");
		if (colors.length != MapLimits.COLORS_PER_CHUNK) {
			throw new IllegalArgumentException("expected " + MapLimits.COLORS_PER_CHUNK + " colors, got " + colors.length);
		}
		colors = colors.clone();
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ChunkTile other)) {
			return false;
		}
		return this.chunkX == other.chunkX
			&& this.chunkZ == other.chunkZ
			&& this.dimension.equals(other.dimension)
			&& Arrays.equals(this.colors, other.colors);
	}

	@Override
	public int hashCode() {
		int result = this.dimension.hashCode();
		result = 31 * result + this.chunkX;
		result = 31 * result + this.chunkZ;
		result = 31 * result + Arrays.hashCode(this.colors);
		return result;
	}

	@Override
	public String toString() {
		return "ChunkTile[" + this.dimension + " " + this.chunkX + "," + this.chunkZ + "]";
	}
}
