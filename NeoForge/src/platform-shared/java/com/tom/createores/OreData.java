package com.tom.createores;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.tom.createores.biome.ReserveRange;
import com.tom.createores.block.entity.IDrill;
import com.tom.createores.recipe.VeinRecipe;
import com.tom.createores.util.RandomSpreadGenerator;
import com.tom.createores.util.ThreeState;

/**
 * Per chunk vein storage. A chunk can hold one regular vein (mined by machines, slowly regenerates)
 * and one ultra small vein (mined by the handheld drill, disappears once depleted).
 */
public class OreData {
	private boolean loaded;
	/**
	 * Regeneration epoch this chunk was last picked at, see {@code VeinRegeneration}. A chunk whose
	 * stamp is older than the epoch of its position is re-picked from the world seed. Veins and ore
	 * clusters are stamped apart, so a command can re-pick only one of them.
	 */
	private int gen;
	private int clusterGen;
	private final List<VeinInstance> veins = new ArrayList<>();
	private Set<BlockPos> extractors;

	public OreData() {
	}

	public OreData(Serialized data) {
		load(data);
	}

	public void load(Serialized data) {
		this.loaded = data.loaded();
		this.gen = data.gen();
		this.clusterGen = data.clusterGen();
		this.veins.clear();
		this.veins.addAll(data.veins());
	}

	public Serialized save() {
		return new Serialized(loaded, List.copyOf(veins), gen, clusterGen);
	}

	public int getGen() {
		return gen;
	}

	public void setGen(int gen) {
		this.gen = gen;
	}

	public int getClusterGen() {
		return clusterGen;
	}

	public void setClusterGen(int gen) {
		this.clusterGen = gen;
	}

	public void populate(LevelChunk chunk, boolean veins, boolean clusters, long veinSeed, long clusterSeed) {
		ServerLevel level = (ServerLevel) chunk.getLevel();
		long time = level.getGameTime();
		if (veins)addVein(chunk, level, time, false, veinSeed);
		if (clusters)addVein(chunk, level, time, true, clusterSeed);
		loaded = true;
	}

	private void addVein(LevelChunk chunk, ServerLevel level, long time, boolean cluster, long seed) {
		RandomSpreadGenerator.PickResult pick = OreVeinGenerator.pick(chunk, cluster, seed);
		if (pick == null)return;
		RandomSource rng = OreVeinGenerator.rngFromChunk(chunk, cluster, seed);
		VeinRecipe recipe = pick.recipe().value();
		long total = computeTotal(recipe, pick.layer(), rng);
		veins.add(new VeinInstance(pick.recipe().id(), total, 0, time, 0, cluster, recipe.regenTicks));
	}

	/**
	 * @return how much ore the vein holds in that chunk, 0 when it is infinite
	 */
	public static long computeTotal(VeinRecipe recipe, VeinRecipe.Layer layer, RandomSource rng) {
		if (recipe.isFinite() == ThreeState.NEVER)return 0L;
		if (recipe.isFinite() == ThreeState.DEFAULT && Config.defaultInfinite)return 0L;
		ReserveRange range = layer.reserve();
		// the reserve range always ships with the recipe, a missing one would otherwise read as infinite
		return range != null ? range.sample(rng) : 1L;
	}

	public VeinInstance getInstance(boolean cluster) {
		for (VeinInstance v : veins) {
			if (v.cluster == cluster)return v;
		}
		return null;
	}

	public VeinInstance getInstance(ResourceLocation recipe) {
		for (VeinInstance v : veins) {
			if (v.recipe.equals(recipe))return v;
		}
		return null;
	}

	public RecipeHolder<VeinRecipe> getRecipe(RecipeManager mngr) {
		VeinInstance v = getInstance(false);
		return v != null ? recipe(mngr, v.recipe) : null;
	}

	/**
	 * Ore clusters vanish once they are depleted, veins regenerate over time.
	 */
	public RecipeHolder<VeinRecipe> getClusterRecipe(RecipeManager mngr) {
		VeinInstance v = getInstance(true);
		if (v == null || isDepleted(v))return null;
		return recipe(mngr, v.recipe);
	}

	@SuppressWarnings("unchecked")
	private static RecipeHolder<VeinRecipe> recipe(RecipeManager mngr, ResourceLocation id) {
		return mngr.byKey(id).filter(r -> r.value() instanceof VeinRecipe).map(r -> (RecipeHolder<VeinRecipe>) r).orElse(null);
	}

	public boolean isLoaded() {
		return loaded;
	}

	public boolean isDepleted(VeinInstance v) {
		return v.total > 0 && v.extracted >= v.total;
	}

	public long getResourcesRemaining(ResourceLocation recipe, long gameTime) {
		VeinInstance v = getInstance(recipe);
		if (v == null)return -1L;
		if (v.total <= 0L)return 0L;
		applyRegen(v, gameTime);
		if (v.extracted >= v.total)return -1L;
		return v.total - v.extracted;
	}

	/**
	 * @return how long a depleted vein still needs to fill back up, 0 when it is already full, -1 when
	 *         it never regenerates (infinite veins and ore clusters)
	 */
	public long ticksUntilRegen(ResourceLocation recipe, long gameTime) {
		VeinInstance v = getInstance(recipe);
		if (v == null || v.cluster || v.total <= 0L || v.regenTicks <= 0)return -1L;
		applyRegen(v, gameTime);
		if (v.extracted <= 0L)return 0L;
		return Math.max(1L, (long) Math.ceil((double) v.extracted * v.regenTicks / v.total));
	}

	private void applyRegen(VeinInstance v, long gameTime) {
		if (v.cluster || v.total <= 0L || v.regenTicks <= 0) {
			v.lastRegen = gameTime;
			return;
		}
		long dt = gameTime - v.lastRegen;
		v.lastRegen = gameTime;
		if (dt <= 0L || v.extracted <= 0L)return;
		double regen = (double) v.total * dt / v.regenTicks + v.regenBuffer;
		long whole = (long) regen;
		v.regenBuffer = regen - whole;
		if (whole > 0L)v.extracted = Math.max(0L, v.extracted - whole);
	}

	/**
	 * @return how much ore this vein holds in total, 0 when it is infinite
	 */
	public long getTotal(ResourceLocation recipe) {
		VeinInstance v = getInstance(recipe);
		return v != null ? v.total : 0L;
	}

	public void extract(ResourceLocation recipe, int a, long gameTime) {
		VeinInstance v = getInstance(recipe);
		if (v != null) {
			applyRegen(v, gameTime);
			v.extracted += a;
		}
	}

	public boolean canExtract(Level lvl, BlockPos pos) {
		if(Config.maxExtractorsPerVein == 0)return true;
		if(extractors == null) {
			extractors = new HashSet<>();
			extractors.add(pos);
			return true;
		}
		if(extractors.contains(pos))
			return true;
		extractors.removeIf(p -> !(lvl.getBlockEntity(p) instanceof IDrill));
		if(extractors.size() < Config.maxExtractorsPerVein) {
			extractors.add(pos);
			return true;
		}
		return false;
	}

	public void setLoaded(boolean loaded) {
		this.loaded = loaded;
	}

	public void clear() {
		veins.clear();
	}

	/**
	 * Drops the veins of one kind, so that only they are picked again.
	 */
	public void clear(boolean cluster) {
		veins.removeIf(v -> v.cluster == cluster);
	}

	public void setVein(ResourceLocation id, VeinRecipe recipe, Holder<Biome> biome, RegistryAccess registries, RandomSource rng, float scale, long gameTime) {
		veins.removeIf(v -> v.cluster == recipe.isCluster());
		long total = computeTotal(recipe, recipe.layers(biome, registries).get(0), rng);
		if (total > 0L && scale != 1F)total = Math.max(1L, Math.round(total * scale));
		veins.add(new VeinInstance(id, total, 0, gameTime, 0, recipe.isCluster(), recipe.getRegenTicks()));
	}

	public static final Codec<OreData> CODEC = Serialized.CODEC.xmap(OreData::new, OreData::save);

	public static record Serialized(boolean loaded, List<VeinInstance> veins, int gen, int clusterGen) {
		public static final Codec<Serialized> CODEC = RecordCodecBuilder.<Serialized>mapCodec(b -> {
			return b.group(
					Codec.BOOL.fieldOf("loaded").forGetter(Serialized::loaded),
					VeinInstance.CODEC.listOf().optionalFieldOf("veins", List.of()).forGetter(Serialized::veins),
					Codec.INT.optionalFieldOf("gen", 0).forGetter(Serialized::gen),
					Codec.INT.optionalFieldOf("clusterGen", 0).forGetter(Serialized::clusterGen)
					).apply(b, Serialized::new);
		}).codec();
	}

	public static class VeinInstance {
		public ResourceLocation recipe;
		public long total;
		public long extracted;
		public long lastRegen;
		public double regenBuffer;
		public boolean cluster;
		public int regenTicks;

		public VeinInstance(ResourceLocation recipe, long total, long extracted, long lastRegen, double regenBuffer, boolean cluster, int regenTicks) {
			this.recipe = recipe;
			this.total = total;
			this.extracted = extracted;
			this.lastRegen = lastRegen;
			this.regenBuffer = regenBuffer;
			this.cluster = cluster;
			this.regenTicks = regenTicks;
		}

		public static final Codec<VeinInstance> CODEC = RecordCodecBuilder.<VeinInstance>mapCodec(b -> {
			return b.group(
					ResourceLocation.CODEC.fieldOf("recipe").forGetter(v -> v.recipe),
					Codec.LONG.fieldOf("total").forGetter(v -> v.total),
					Codec.LONG.fieldOf("extracted").forGetter(v -> v.extracted),
					Codec.LONG.optionalFieldOf("regen", 0L).forGetter(v -> v.lastRegen),
					Codec.DOUBLE.optionalFieldOf("regenBuffer", 0D).forGetter(v -> v.regenBuffer),
					Codec.BOOL.optionalFieldOf("cluster", false).forGetter(v -> v.cluster),
					Codec.INT.optionalFieldOf("regenTicks", 0).forGetter(v -> v.regenTicks)
					).apply(b, VeinInstance::new);
		}).codec();
	}
}
