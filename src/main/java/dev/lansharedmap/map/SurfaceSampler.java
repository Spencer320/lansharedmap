package dev.lansharedmap.map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * One color per surface block. Grass, leaves, and water pick up the biome tint,
 * and brightness follows the slope toward the north column so hills read as
 * terrain instead of a height checkerboard.
 */
public final class SurfaceSampler {
	private static final int VOID = 0xFF1B1F24;
	private static final int BLOCKS = MapLimits.BLOCKS_PER_CHUNK;

	private SurfaceSampler() {
	}

	public static int[] sample(ServerLevel level, int chunkX, int chunkZ) {
		int[] colors = new int[MapLimits.COLORS_PER_CHUNK];
		int originX = chunkX << 4;
		int originZ = chunkZ << 4;
		int[] height = new int[BLOCKS * BLOCKS];
		int[] northEdge = new int[BLOCKS];
		boolean northLoaded = level.hasChunk(chunkX, chunkZ - 1);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dz = 0; dz < BLOCKS; dz++) {
			for (int dx = 0; dx < BLOCKS; dx++) {
				height[dx + dz * BLOCKS] = level.getHeight(Heightmap.Types.WORLD_SURFACE, originX + dx, originZ + dz);
			}
		}
		if (northLoaded) {
			for (int dx = 0; dx < BLOCKS; dx++) {
				northEdge[dx] = level.getHeight(Heightmap.Types.WORLD_SURFACE, originX + dx, originZ - 1);
			}
		}
		for (int dz = 0; dz < BLOCKS; dz++) {
			for (int dx = 0; dx < BLOCKS; dx++) {
				int x = originX + dx;
				int z = originZ + dz;
				int here = height[dx + dz * BLOCKS];
				int north = dz > 0 ? height[dx + (dz - 1) * BLOCKS] : (northLoaded ? northEdge[dx] : here);
				colors[dx + dz * BLOCKS] = sampleColumn(level, pos, x, z, here, north);
			}
		}
		return colors;
	}

	private static int sampleColumn(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int z, int marker, int northHeight) {
		pos.set(x, marker, z);
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			pos.setY(marker - 1);
			state = level.getBlockState(pos);
		}
		if (state.isAir()) {
			return VOID;
		}
		MapColor mapColor = state.getMapColor(level, pos);
		if (mapColor == MapColor.NONE) {
			return VOID;
		}
		boolean water = mapColor == MapColor.WATER || state.getFluidState().is(FluidTags.WATER);
		Biome biome = level.getBiome(pos).value();
		int rgb = baseColor(state, mapColor, biome, x, z, water);
		int shade = water ? waterShade(level, x, z, pos.getY()) : slopeShade(pos.getY(), northHeight);
		return ARGB.scaleRGB(ARGB.opaque(rgb), shade);
	}

	private static int baseColor(BlockState state, MapColor mapColor, Biome biome, int x, int z, boolean water) {
		if (water) {
			return biome.getWaterColor();
		}
		if (state.is(BlockTags.LEAVES)) {
			return biome.getFoliageColor();
		}
		if (mapColor == MapColor.GRASS) {
			return biome.getGrassColor(x, z);
		}
		if (mapColor == MapColor.PLANT) {
			return mix(biome.getFoliageColor(), mapColor.col, 0.35);
		}
		return mapColor.col;
	}

	private static int slopeShade(int height, int northHeight) {
		int diff = height - northHeight;
		if (diff >= 2) {
			return MapColor.Brightness.HIGH.modifier;
		}
		if (diff <= -4) {
			return MapColor.Brightness.LOWEST.modifier;
		}
		if (diff <= -1) {
			return MapColor.Brightness.LOW.modifier;
		}
		return MapColor.Brightness.NORMAL.modifier;
	}

	private static int waterShade(ServerLevel level, int x, int z, int surfaceY) {
		int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
		int depth = Math.max(0, surfaceY - floor);
		return Math.max(MapColor.Brightness.LOWEST.modifier, 210 - depth * 8);
	}

	private static int mix(int biomeRgb, int mapRgb, double mapWeight) {
		double biomeWeight = 1.0 - mapWeight;
		int red = (int) (((biomeRgb >> 16) & 255) * biomeWeight + ((mapRgb >> 16) & 255) * mapWeight);
		int green = (int) (((biomeRgb >> 8) & 255) * biomeWeight + ((mapRgb >> 8) & 255) * mapWeight);
		int blue = (int) ((biomeRgb & 255) * biomeWeight + (mapRgb & 255) * mapWeight);
		return (red << 16) | (green << 8) | blue;
	}
}
