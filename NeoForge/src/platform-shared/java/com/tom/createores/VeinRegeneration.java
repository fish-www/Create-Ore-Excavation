package com.tom.createores;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import com.tom.createores.network.OreVeinDiscoverPacket.Kind;

/**
 * Marks chunks whose vein or ore cluster has to be picked again, per dimension. Chunks are only
 * re-picked when they are loaded, so a command over a whole dimension stays cheap: it records an
 * epoch, and every chunk loaded afterwards compares its own stamp ({@code OreData.gen} for veins,
 * {@code OreData.clusterGen} for ore clusters) against it.
 * <p>
 * The two kinds are tracked apart, so a command can re-pick only one of them.
 */
public class VeinRegeneration extends SavedData {
	private static final String ID = CreateOreExcavation.MODID + "_vein_regeneration";
	private static final SavedData.Factory<VeinRegeneration> FACTORY = new SavedData.Factory<>(VeinRegeneration::new, VeinRegeneration::load);

	private int nextEpoch;
	private final List<Area> areas = new ArrayList<>();
	/**
	 * The seed new veins and ore clusters are picked from. {@code null} until a command sets another
	 * one, which means the world seed is used.
	 */
	private Long veinSeed, clusterSeed;

	public static VeinRegeneration get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(FACTORY, ID);
	}

	/**
	 * @return the epoch the chunk has to be picked at for that kind, 0 as long as nothing has been marked
	 */
	public static int epochFor(ServerLevel level, ChunkPos pos, Kind kind) {
		return get(level).epoch(pos, kind);
	}

	private int epoch(ChunkPos pos, Kind kind) {
		int epoch = 0;
		for (Area area : areas) {
			if (area.covers(kind) && area.epoch() > epoch && area.contains(pos))epoch = area.epoch();
		}
		return epoch;
	}

	/**
	 * @return the seed veins or ore clusters are picked from, the world seed until a command sets another
	 */
	public static long seedFor(ServerLevel level, Kind kind) {
		return get(level).seed(kind, level.getSeed());
	}

	private long seed(Kind kind, long fallback) {
		Long seed = kind == Kind.CLUSTER ? clusterSeed : veinSeed;
		return seed != null ? seed : fallback;
	}

	private void setSeed(Kind kind, long seed) {
		if (kind.veins())veinSeed = seed;
		if (kind.clusters())clusterSeed = seed;
	}

	/**
	 * Marks every chunk of the dimension. Chunks picked before this call are re-picked once they are
	 * loaded again, chunks that are never loaded again keep what they have.
	 *
	 * @param seed the seed the re-picked chunks generate from, so a new one shuffles the veins around
	 */
	public void markAll(Kind kind, long seed) {
		setSeed(kind, seed);
		if (kind.veins())areas.add(new Area(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, ++nextEpoch, Kind.VEIN));
		if (kind.clusters())areas.add(new Area(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, ++nextEpoch, Kind.CLUSTER));
		setDirty();
	}

	/**
	 * @param radius in chunks, around the centre chunk
	 * @return how many chunks the area covers
	 */
	public long markArea(ChunkPos center, int radius, Kind kind, long seed) {
		setSeed(kind, seed);
		if (kind.veins())areas.add(new Area(center.x - radius, center.z - radius, center.x + radius, center.z + radius, ++nextEpoch, Kind.VEIN));
		if (kind.clusters())areas.add(new Area(center.x - radius, center.z - radius, center.x + radius, center.z + radius, ++nextEpoch, Kind.CLUSTER));
		setDirty();
		return (long) (2 * radius + 1) * (2 * radius + 1);
	}

	/**
	 * Drops the pending markers and the custom seed of the kinds. The epoch counter keeps counting, so a
	 * later command still marks every chunk, while the chunks already picked stay untouched.
	 */
	public void clear(Kind kind) {
		areas.removeIf(a -> kind == Kind.BOTH || a.covers(kind));
		if (kind.veins())veinSeed = null;
		if (kind.clusters())clusterSeed = null;
		setDirty();
	}

	@Override
	public CompoundTag save(CompoundTag tag, Provider provider) {
		tag.putInt("nextEpoch", nextEpoch);
		if (veinSeed != null)tag.putLong("veinSeed", veinSeed);
		if (clusterSeed != null)tag.putLong("clusterSeed", clusterSeed);
		ListTag list = new ListTag();
		for (Area area : areas) {
			list.add(new IntArrayTag(new int[] { area.x1(), area.z1(), area.x2(), area.z2(), area.epoch(), area.kind().ordinal() }));
		}
		tag.put("areas", list);
		return tag;
	}

	public static VeinRegeneration load(CompoundTag tag, Provider provider) {
		VeinRegeneration data = new VeinRegeneration();
		data.nextEpoch = tag.getInt("nextEpoch");
		if (tag.contains("veinSeed"))data.veinSeed = tag.getLong("veinSeed");
		if (tag.contains("clusterSeed"))data.clusterSeed = tag.getLong("clusterSeed");
		ListTag list = tag.getList("areas", Tag.TAG_INT_ARRAY);
		for (int i = 0; i < list.size(); i++) {
			int[] a = list.getIntArray(i);
			// an area written before the kinds were tracked marked both
			if (a.length == 5)data.areas.add(new Area(a[0], a[1], a[2], a[3], a[4], Kind.BOTH));
			else if (a.length == 6 && a[5] >= 0 && a[5] < Kind.values().length)data.areas.add(new Area(a[0], a[1], a[2], a[3], a[4], Kind.values()[a[5]]));
		}
		return data;
	}

	private record Area(int x1, int z1, int x2, int z2, int epoch, Kind kind) {
		public boolean contains(ChunkPos pos) {
			return pos.x >= x1 && pos.x <= x2 && pos.z >= z1 && pos.z <= z2;
		}

		public boolean covers(Kind k) {
			return kind == Kind.BOTH || kind == k;
		}
	}
}
