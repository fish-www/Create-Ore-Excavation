package com.tom.createores.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.OreData;
import com.tom.createores.OreDataAttachment;
import com.tom.createores.recipe.VeinRecipe;

public class RandomSpreadGenerator {
	private static final Codec<RandomSpreadStructurePlacement> PLACEMENT_CODEC = RandomSpreadStructurePlacement.CODEC.codec();

	private final List<RecipeHolder<VeinRecipe>> recipes = new ArrayList<>();
	private final List<RecipeHolder<VeinRecipe>> clusterRecipes = new ArrayList<>();
	/**
	 * Keyed by placement identity, then by density. The salt of a placement cannot be read back, so
	 * two different placements must never share a cache entry: they would end up with the same grid
	 * and the first vein of the two would shadow the other everywhere.
	 */
	private final Map<RandomSpreadStructurePlacement, Map<Integer, RandomSpreadStructurePlacement>> placementCache = new IdentityHashMap<>();

	public void loadAll(ServerLevel level) {
		recipes.clear();
		clusterRecipes.clear();
		Comparator<RecipeHolder<VeinRecipe>> cmp = Comparator.comparingInt(RandomSpreadGenerator::getPriority).thenComparing(RecipeHolder::id);
		for (RecipeHolder<VeinRecipe> r : level.getRecipeManager().getAllRecipesFor(CreateOreExcavation.VEIN_RECIPES.getRecipeType())) {
			(r.value().isCluster() ? clusterRecipes : recipes).add(r);
		}
		recipes.sort(cmp);
		clusterRecipes.sort(cmp);
		CreateOreExcavation.LOGGER.info("Loaded {} ore vein types and {} ore cluster types", recipes.size(), clusterRecipes.size());
	}

	private static int getPriority(RecipeHolder<VeinRecipe> h) {
		return h.value().getNegGenerationPriority();
	}

	public record PickResult(RecipeHolder<VeinRecipe> recipe, Holder<Biome> biome, VeinRecipe.Layer layer) {
	}

	public PickResult pick(ServerLevel level, ChunkPos pos, boolean cluster, long seed) {
		List<RecipeHolder<VeinRecipe>> list = cluster ? clusterRecipes : recipes;
		if (list.isEmpty())return null;
		Holder<Biome> biome = sampleBiome(level, pos, seed);
		RegistryAccess registries = level.registryAccess();
		for (RecipeHolder<VeinRecipe> recipe : list) {
			VeinRecipe vein = recipe.value();
			// a pinned chunk carries the vein whatever the biome and the grids say
			if (vein.isFixedChunk(pos))return new PickResult(recipe, biome, vein.defaultLayer());
			if (!vein.canGenerate(biome, registries))continue;
			for (VeinRecipe.Layer layer : vein.layers(biome, registries)) {
				if (layer.spacing() <= 0)continue;
				RandomSpreadStructurePlacement placement = placementFor(vein, layer.spacing());
				ChunkPos at = placement.getPotentialStructureChunk(seed, pos.x, pos.z);
				if (at.x == pos.x && at.z == pos.z) {
					return new PickResult(recipe, biome, layer);
				}
			}
		}
		return null;
	}

	public static Holder<Biome> sampleBiome(ServerLevel level, ChunkPos pos, long seed) {
		int minY = QuartPos.fromBlock(level.getMinBuildHeight());
		int maxY = minY + QuartPos.fromBlock(level.getHeight()) - 1;
		WorldgenRandom rng = new WorldgenRandom(new LegacyRandomSource(0L));
		rng.setLargeFeatureSeed(seed, pos.x, pos.z);
		return level.getNoiseBiome(QuartPos.fromSection(pos.x) + rng.nextInt(4), minY + rng.nextInt(maxY), QuartPos.fromSection(pos.z) + rng.nextInt(4));
	}

	/**
	 * The vein carries the salt of its grid and a fallback spacing, the density level names the spacing
	 * actually used. The rescaled placements are cached per placement object, because the salt of a
	 * placement cannot be read back: two veins sharing a cache entry would share a grid and the first
	 * one would shadow the other everywhere.
	 */
	public RandomSpreadStructurePlacement placementFor(VeinRecipe recipe, int spacing) {
		RandomSpreadStructurePlacement base = recipe.getPlacement();
		if (spacing <= 0 || spacing == base.spacing())return base;
		return placementCache.computeIfAbsent(base, b -> new HashMap<>()).computeIfAbsent(spacing, s -> {
			int separation = Math.min(base.separation(), Math.max(0, s - 1));
			return rescale(base, s, separation);
		});
	}

	/**
	 * The salt of a placement is not readable, so re-scale the placement through its codec.
	 */
	private static RandomSpreadStructurePlacement rescale(RandomSpreadStructurePlacement base, int spacing, int separation) {
		try {
			JsonElement encoded = PLACEMENT_CODEC.encodeStart(JsonOps.INSTANCE, base).getOrThrow();
			if (!(encoded instanceof JsonObject obj))return base;
			obj.addProperty("spacing", spacing);
			obj.addProperty("separation", separation);
			return PLACEMENT_CODEC.parse(JsonOps.INSTANCE, obj).getOrThrow();
		} catch (Exception e) {
			CreateOreExcavation.LOGGER.warn("Failed to rescale vein placement to {} chunks, density ignored", spacing, e);
			return base;
		}
	}

	public BlockPos locate(ResourceLocation id, BlockPos pPos, ServerLevel level, int radius, long seed) {
		Pair<BlockPos, RecipeHolder<VeinRecipe>> found = locate(pPos, level, radius, false, h -> h.id().equals(id), seed);
		return found != null ? found.getFirst() : null;
	}

	public Pair<BlockPos, RecipeHolder<VeinRecipe>> locate(BlockPos pPos, ServerLevel level, int radius, Predicate<RecipeHolder<VeinRecipe>> filter, long seed) {
		return locate(pPos, level, radius, false, filter, seed);
	}

	public Pair<BlockPos, RecipeHolder<VeinRecipe>> locate(BlockPos pPos, ServerLevel level, int radius, boolean cluster, Predicate<RecipeHolder<VeinRecipe>> filter, long seed) {
		List<RecipeHolder<VeinRecipe>> list = (cluster ? clusterRecipes : recipes).stream().filter(filter).toList();
		if (list.isEmpty())return null;
		int cx = SectionPos.blockToSectionCoord(pPos.getX());
		int cz = SectionPos.blockToSectionCoord(pPos.getZ());
		Set<ChunkPos> candidates = new HashSet<>();
		for (RecipeHolder<VeinRecipe> h : list) {
			// one grid per spacing the vein uses: a grid of another spacing is not a subset of this one
			for (ChunkPos pinned : h.value().getChunks()) {
				if (distance2d(pinned.getMiddleBlockPosition(0), pPos) <= radius)candidates.add(pinned);
			}
			for (int spacing : h.value().spacings()) {
				int r = Math.max(0, (radius + spacing - 1) / spacing);
				RandomSpreadStructurePlacement placement = placementFor(h.value(), spacing);
				for (int j = -r; j <= r; j++) {
					for (int k = -r; k <= r; k++) {
						candidates.add(placement.getPotentialStructureChunk(seed, cx + spacing * j, cz + spacing * k));
					}
				}
			}
		}
		BlockPos best = null;
		RecipeHolder<VeinRecipe> bestRecipe = null;
		float bestDist = Float.MAX_VALUE;
		for (ChunkPos cp : candidates) {
			RecipeHolder<VeinRecipe> match = matches(level, cp, list, seed);
			if (match == null)continue;
			BlockPos pos = cp.getMiddleBlockPosition(0);
			if (level.isLoaded(pos)) {
				OreData data = OreDataAttachment.getData(level.getChunkAt(pos));
				RecipeHolder<VeinRecipe> actual = cluster ? data.getClusterRecipe(level.getRecipeManager()) : data.getRecipe(level.getRecipeManager());
				if (actual == null || !filter.test(actual))continue;
				match = actual;
			}
			float d = distance2d(pos, pPos);
			if (d < bestDist) {
				bestDist = d;
				best = pos;
				bestRecipe = match;
			}
		}
		return best != null ? Pair.of(best, bestRecipe) : null;
	}

	private RecipeHolder<VeinRecipe> matches(ServerLevel level, ChunkPos pos, List<RecipeHolder<VeinRecipe>> list, long seed) {
		Holder<Biome> biome = sampleBiome(level, pos, seed);
		RegistryAccess registries = level.registryAccess();
		for (RecipeHolder<VeinRecipe> h : list) {
			VeinRecipe vein = h.value();
			if (vein.isFixedChunk(pos))return h;
			if (!vein.canGenerate(biome, registries))continue;
			for (VeinRecipe.Layer layer : vein.layers(biome, registries)) {
				if (layer.spacing() <= 0)continue;
				RandomSpreadStructurePlacement placement = placementFor(vein, layer.spacing());
				ChunkPos at = placement.getPotentialStructureChunk(seed, pos.x, pos.z);
				if (at.x == pos.x && at.z == pos.z)return h;
			}
		}
		return null;
	}

	public static float distance2d(BlockPos a, BlockPos b) {
		int i = b.getX() - a.getX();
		int j = b.getZ() - a.getZ();
		return Mth.sqrt(i * i + j * j);
	}
}
