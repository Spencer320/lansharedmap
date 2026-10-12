package dev.lansharedmap.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.lansharedmap.LanSharedMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Explored chunks for one dimension. Minecraft stores this next to the dimension's other saved data.
 */
public final class ExplorationMapData extends SavedData {
	private static final Codec<int[]> COLORS = Codec.INT.listOf().flatXmap(
		list -> {
			if (list.size() != MapLimits.COLORS_PER_CHUNK) {
				return DataResult.error(() -> "expected " + MapLimits.COLORS_PER_CHUNK + " colors, got " + list.size());
			}
			int[] colors = new int[list.size()];
			for (int i = 0; i < list.size(); i++) {
				colors[i] = list.get(i);
			}
			return DataResult.success(colors);
		},
		colors -> {
			if (colors.length != MapLimits.COLORS_PER_CHUNK) {
				return DataResult.error(() -> "expected " + MapLimits.COLORS_PER_CHUNK + " colors, got " + colors.length);
			}
			List<Integer> list = new ArrayList<>(colors.length);
			for (int color : colors) {
				list.add(color);
			}
			return DataResult.success(list);
		}
	);

	private static final Codec<StoredChunk> CHUNK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.INT.fieldOf("x").forGetter(StoredChunk::x),
		Codec.INT.fieldOf("z").forGetter(StoredChunk::z),
		COLORS.fieldOf("colors").forGetter(StoredChunk::colors),
		Codec.INT.optionalFieldOf("v", 0).forGetter(StoredChunk::version)
	).apply(instance, StoredChunk::new));

	public static final Codec<ExplorationMapData> CODEC = CHUNK_CODEC.listOf().xmap(
		ExplorationMapData::fromChunks,
		ExplorationMapData::toChunks
	);

	private static final SavedDataType<ExplorationMapData> TYPE = new SavedDataType<>(
		LanSharedMap.id("explored"),
		ExplorationMapData::new,
		CODEC,
		null
	);

	private final Map<Long, ChunkSample> chunks = new HashMap<>();

	public ExplorationMapData() {
	}

	public static ExplorationMapData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean contains(int chunkX, int chunkZ) {
		return this.chunks.containsKey(ChunkPos.pack(chunkX, chunkZ));
	}

	public boolean needsSample(int chunkX, int chunkZ) {
		ChunkSample sample = this.chunks.get(ChunkPos.pack(chunkX, chunkZ));
		return sample == null || sample.version() < MapLimits.COLOR_VERSION;
	}

	public boolean add(int chunkX, int chunkZ, int[] colors) {
		long key = ChunkPos.pack(chunkX, chunkZ);
		ChunkSample existing = this.chunks.get(key);
		if (existing != null && existing.version() >= MapLimits.COLOR_VERSION) {
			return false;
		}
		this.chunks.put(key, new ChunkSample(MapLimits.COLOR_VERSION, colors));
		this.setDirty();
		return true;
	}

	public List<ChunkTile> toTiles(String dimension) {
		List<ChunkTile> tiles = new ArrayList<>(this.chunks.size());
		for (Map.Entry<Long, ChunkSample> entry : this.chunks.entrySet()) {
			tiles.add(new ChunkTile(dimension, ChunkPos.getX(entry.getKey()), ChunkPos.getZ(entry.getKey()), entry.getValue().colors()));
		}
		return tiles;
	}

	private static ExplorationMapData fromChunks(List<StoredChunk> stored) {
		ExplorationMapData data = new ExplorationMapData();
		for (StoredChunk chunk : stored) {
			data.chunks.put(ChunkPos.pack(chunk.x(), chunk.z()), new ChunkSample(chunk.version(), chunk.colors()));
		}
		return data;
	}

	private List<StoredChunk> toChunks() {
		List<StoredChunk> stored = new ArrayList<>(this.chunks.size());
		for (Map.Entry<Long, ChunkSample> entry : this.chunks.entrySet()) {
			ChunkSample sample = entry.getValue();
			stored.add(new StoredChunk(ChunkPos.getX(entry.getKey()), ChunkPos.getZ(entry.getKey()), sample.colors(), sample.version()));
		}
		return stored;
	}

	private record ChunkSample(int version, int[] colors) {
		private ChunkSample {
			colors = colors.clone();
		}
	}

	private record StoredChunk(int x, int z, int[] colors, int version) {
		private StoredChunk {
			colors = colors.clone();
		}
	}
}
