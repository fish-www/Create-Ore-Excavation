package com.tom.createores.kubejs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement.FrequencyReductionMethod;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.biome.ReserveRange;
import com.tom.createores.recipe.VeinRarity;
import com.tom.createores.recipe.VeinRecipe;
import com.tom.createores.recipe.VeinRecipe.BiomeOverride;
import com.tom.createores.util.ThreeState;

import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.component.BooleanComponent;
import dev.latvian.mods.kubejs.recipe.component.EnumComponent;
import dev.latvian.mods.kubejs.recipe.component.ItemStackComponent;
import dev.latvian.mods.kubejs.recipe.component.ListRecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.NumberComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.StringComponent;
import dev.latvian.mods.kubejs.recipe.schema.KubeRecipeFactory;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import dev.latvian.mods.kubejs.recipe.schema.function.RecipeFunctionInstance;
import dev.latvian.mods.kubejs.util.IntBounds;

public class VeinRecipeJS extends KubeRecipe {
	public static final KubeRecipeFactory RECIPE_FACTORY = new KubeRecipeFactory(CreateOreExcavation.VEIN_RECIPES.getId(), VeinRecipeJS.class, VeinRecipeJS::new);

	public static final RecipeKey<ItemStack> ICON = ItemStackComponent.ITEM_STACK.otherKey("icon");
	public static final RecipeKey<Component> NAME = ComponentComponent.INSTANCE.otherKey("name");
	public static final RecipeKey<RandomSpreadStructurePlacementJS> PLACEMENT = PlacementJS.INSTANCE.otherKey("placement");
	public static final RecipeKey<Integer> PRIORITY = NumberComponent.INT.range(0, Integer.MAX_VALUE).otherKey("priority").defaultOptional();
	public static final RecipeKey<ThreeState> FINITE = EnumComponent.of(ResourceLocation.tryBuild(CreateOreExcavation.MODID, "three_state"), ThreeState.class, ThreeState.CODEC).otherKey("finite").defaultOptional();
	public static final RecipeKey<Integer> DENSITY = NumberComponent.INT.otherKey("density").defaultOptional();
	public static final RecipeKey<ReserveRange> RESERVE = ReserveRangeComponent.INSTANCE.otherKey("reserve").defaultOptional();
	public static final RecipeKey<Integer> REGEN_TICKS = NumberComponent.INT.otherKey("regenTicks").defaultOptional();
	public static final RecipeKey<String> BIOME_WHITELIST = StringComponent.ID.otherKey("biomeWhitelist").defaultOptional();
	public static final RecipeKey<String> BIOME_BLACKLIST = StringComponent.ID.otherKey("biomeBlacklist").defaultOptional();
	public static final RecipeKey<VeinRarity> RARITY = EnumComponent.of(ResourceLocation.tryBuild(CreateOreExcavation.MODID, "vein_rarity"), VeinRarity.class, VeinRarity.CODEC).otherKey("rarity").defaultOptional();
	public static final RecipeKey<Boolean> CLUSTER = BooleanComponent.BOOLEAN.instance().otherKey("cluster").defaultOptional();
	/**
	 * Empty lists are only allowed by the {@code OPTIONAL} bounds. An empty one is left out of the
	 * written recipe, which is exactly what a missing field decodes to.
	 */
	private static final RecipeComponent<List<BiomeOverride>> BIOME_OVERRIDE_LIST = ListRecipeComponent.create(BiomeOverrideComponent.INSTANCE, false, false, IntBounds.OPTIONAL, Optional.empty());
	private static final RecipeComponent<List<ChunkPos>> CHUNK_LIST = ListRecipeComponent.create(ChunkPosComponent.INSTANCE, false, false, IntBounds.OPTIONAL, Optional.empty());
	public static final RecipeKey<List<BiomeOverride>> BIOME_OVERRIDES = BIOME_OVERRIDE_LIST.otherKey("biomeOverrides").defaultOptional();
	public static final RecipeKey<List<ChunkPos>> CHUNKS = CHUNK_LIST.otherKey("chunks").defaultOptional();
	public static final RecipeKey<String> WAYPOINT_COLOR = StringComponent.STRING.otherKey("waypointColor").defaultOptional();
	public static final RecipeComponent<RandomSpreadType> RANDOM_SPREAD = EnumComponent.of(ResourceLocation.tryBuild(CreateOreExcavation.MODID, "random_spread"), RandomSpreadType.class, RandomSpreadType.CODEC).instance();
	public static final RecipeComponent<FrequencyReductionMethod> FREQ_REDUCTION = EnumComponent.of(ResourceLocation.tryBuild(CreateOreExcavation.MODID, "freq_reduction"), FrequencyReductionMethod.class, FrequencyReductionMethod.CODEC).instance();

	public static final RecipeSchema SCHEMA = new RecipeSchema(NAME, ICON, PLACEMENT, PRIORITY,
			FINITE, DENSITY, RESERVE, REGEN_TICKS, BIOME_WHITELIST, BIOME_BLACKLIST, RARITY, CLUSTER,
			BIOME_OVERRIDES, CHUNKS, WAYPOINT_COLOR).
			uniqueIds(List.of(PLACEMENT, PRIORITY)).constructor(NAME, ICON).
			function(new RecipeFunctionInstance("alwaysInfinite", KubeJSUtil.wrapFunc(VeinRecipeJS::alwaysInfinite))).
			function(new RecipeFunctionInstance("alwaysFinite", KubeJSUtil.wrapFunc(VeinRecipeJS::alwaysFinite))).
			function(new RecipeFunctionInstance("defaultFinite", KubeJSUtil.wrapFunc(VeinRecipeJS::defaultFinite))).
			function(new RecipeFunctionInstance("density", KubeJSUtil.wrapFunc(NumberComponent.INT, VeinRecipeJS::density))).
			function(new RecipeFunctionInstance("reserve", KubeJSUtil.wrapFunc(NumberComponent.INT, NumberComponent.INT, NumberComponent.INT, VeinRecipeJS::reserve))).
			function(new RecipeFunctionInstance("reserveSigma", KubeJSUtil.wrapFunc(NumberComponent.INT, VeinRecipeJS::reserveSigma))).
			function(new RecipeFunctionInstance("regenTicks", KubeJSUtil.wrapFunc(NumberComponent.INT, VeinRecipeJS::regenTicks))).
			function(new RecipeFunctionInstance("biomeWhitelist", KubeJSUtil.wrapFunc(StringComponent.ID.instance(), VeinRecipeJS::biomeWhitelist))).
			function(new RecipeFunctionInstance("biomeBlacklist", KubeJSUtil.wrapFunc(StringComponent.ID.instance(), VeinRecipeJS::biomeBlacklist))).
			function(new RecipeFunctionInstance("cluster", KubeJSUtil.wrapFunc(VeinRecipeJS::cluster))).
			function(new RecipeFunctionInstance("rare", KubeJSUtil.wrapFunc(VeinRecipeJS::rare))).
			function(new RecipeFunctionInstance("common", KubeJSUtil.wrapFunc(VeinRecipeJS::common))).
			function(new RecipeFunctionInstance("placement", KubeJSUtil.wrapFunc(NumberComponent.INT, NumberComponent.INT, NumberComponent.INT, VeinRecipeJS::placement))).
			function(new RecipeFunctionInstance("spread", KubeJSUtil.wrapFunc(RANDOM_SPREAD, VeinRecipeJS::spread))).
			function(new RecipeFunctionInstance("reduction", KubeJSUtil.wrapFunc(FREQ_REDUCTION, VeinRecipeJS::reduction))).
			function(new RecipeFunctionInstance("priority", KubeJSUtil.wrapFunc(NumberComponent.INT, VeinRecipeJS::priority))).
			function(new RecipeFunctionInstance("biomeOverride", KubeJSUtil.wrapFunc(List.<RecipeComponent<?>>of(
					StringComponent.STRING.instance(), NumberComponent.INT, NumberComponent.INT,
					NumberComponent.INT, NumberComponent.INT, NumberComponent.INT),
					(VeinRecipeJS recipe, List<Object> args) -> recipe.biomeOverride((String) args.get(0),
							((Number) args.get(1)).intValue(), ((Number) args.get(2)).intValue(), ((Number) args.get(3)).intValue(),
							((Number) args.get(4)).intValue(), ((Number) args.get(5)).intValue())))).
			function(new RecipeFunctionInstance("biomeOverrideDensity", KubeJSUtil.wrapFunc(StringComponent.STRING.instance(), NumberComponent.INT, VeinRecipeJS::biomeOverrideDensity))).
			function(new RecipeFunctionInstance("chunk", KubeJSUtil.wrapFunc(NumberComponent.INT, NumberComponent.INT, VeinRecipeJS::chunk))).
			function(new RecipeFunctionInstance("waypointColor", KubeJSUtil.wrapFunc(StringComponent.STRING.instance(), VeinRecipeJS::waypointColor))).
			factory(RECIPE_FACTORY);

	@Override
	public void initValues(boolean created) {
		super.initValues(created);
		if(created) {
			setValue(PLACEMENT, new RandomSpreadStructurePlacementJS(64, 8, 0));
			setValue(PRIORITY, 0);
			setValue(DENSITY, 256);
			setValue(RESERVE, new ReserveRange(20000, 200000, 40000, 10000));
			setValue(REGEN_TICKS, VeinRecipe.DEFAULT_REGEN_TICKS);
			setValue(FINITE, ThreeState.DEFAULT);
			setValue(RARITY, VeinRarity.COMMON);
			setValue(CLUSTER, false);
			setValue(BIOME_OVERRIDES, List.of());
			setValue(CHUNKS, List.of());
		}
	}

	public VeinRecipeJS cluster() {
		setValue(CLUSTER, true);
		return this;
	}

	public VeinRecipeJS rare() {
		setValue(RARITY, VeinRarity.RARE);
		return this;
	}

	public VeinRecipeJS common() {
		setValue(RARITY, VeinRarity.COMMON);
		return this;
	}

	public VeinRecipeJS alwaysInfinite() {
		setValue(FINITE, ThreeState.NEVER);
		return this;
	}

	public VeinRecipeJS alwaysFinite() {
		setValue(FINITE, ThreeState.ALWAYS);
		return this;
	}

	public VeinRecipeJS defaultFinite() {
		setValue(FINITE, ThreeState.DEFAULT);
		return this;
	}

	public VeinRecipeJS density(int spacing) {
		setValue(DENSITY, spacing);
		return this;
	}

	public VeinRecipeJS reserve(int min, int max, int mean) {
		setValue(RESERVE, new ReserveRange(min, max, mean, 0));
		return this;
	}

	public VeinRecipeJS reserveSigma(int sigma) {
		ReserveRange r = getValue(RESERVE);
		if (r != null)setValue(RESERVE, new ReserveRange(r.min(), r.max(), r.mean(), sigma));
		return this;
	}

	public VeinRecipeJS regenTicks(int ticks) {
		setValue(REGEN_TICKS, ticks);
		return this;
	}

	public VeinRecipeJS biomeWhitelist(String tag) {
		setValue(BIOME_WHITELIST, tag);
		return this;
	}

	public VeinRecipeJS biomeBlacklist(String tag) {
		setValue(BIOME_BLACKLIST, tag);
		return this;
	}

	public VeinRecipeJS placement(int spacing, int separation, int salt) {
		var p = getValue(PLACEMENT);
		p.spacing = spacing;
		p.separation = separation;
		p.salt = salt;
		save();
		return this;
	}

	public VeinRecipeJS spread(RandomSpreadType spread) {
		getValue(PLACEMENT).spreadType = spread;
		save();
		return this;
	}

	public VeinRecipeJS reduction(FrequencyReductionMethod freqReduction) {
		getValue(PLACEMENT).frequencyReductionMethod = freqReduction;
		save();
		return this;
	}

	public VeinRecipeJS priority(int priority) {
		setValue(PRIORITY, priority);
		return this;
	}

	/**
	 * Adds a second distribution of this vein that only applies in the biomes of the target, e.g.
	 * {@code .biomeOverride('#minecraft:is_mountain', 128, 20000, 200000, 150000, 25000)}. The entries
	 * are tested in the order they are added, the first one matching the biome wins a chunk.
	 *
	 * @param target a biome tag ({@code #ns:tag}) or a single biome id ({@code ns:biome})
	 * @param density grid spacing in chunks, negative to keep the entry off the grid
	 */
	public VeinRecipeJS biomeOverride(String target, int density, int min, int max, int mean, int sigma) {
		putOverride(new BiomeOverride(target, Optional.of(density), Optional.of(new ReserveRange(min, max, mean, sigma))));
		return this;
	}

	/**
	 * Same as {@link #biomeOverride} but keeps the reserve range of the vein itself.
	 */
	public VeinRecipeJS biomeOverrideDensity(String target, int density) {
		BiomeOverride existing = findOverride(target);
		putOverride(new BiomeOverride(target, Optional.of(density), existing != null ? existing.reserve() : Optional.empty()));
		return this;
	}

	private BiomeOverride findOverride(String target) {
		List<BiomeOverride> list = getValue(BIOME_OVERRIDES);
		if (list == null)return null;
		for (BiomeOverride override : list) {
			if (override.target().equals(target))return override;
		}
		return null;
	}

	private void putOverride(BiomeOverride override) {
		List<BiomeOverride> list = new ArrayList<>(getValue(BIOME_OVERRIDES));
		list.removeIf(o -> o.target().equals(override.target()));
		list.add(override);
		setValue(BIOME_OVERRIDES, list);
	}

	/**
	 * Pins the vein to a chunk: the chunk gets this vein no matter the biome or the grids, and a vein
	 * without a grid ({@code density} negative) only shows up in the chunks pinned this way. Call once
	 * per chunk, the coordinates are chunk coordinates, not block coordinates.
	 */
	public VeinRecipeJS chunk(int x, int z) {
		List<ChunkPos> list = new ArrayList<>(getValue(CHUNKS));
		list.add(new ChunkPos(x, z));
		setValue(CHUNKS, list);
		return this;
	}

	/**
	 * Colour of this vein's marker on Xaero's map, e.g. {@code gold}. Left out, a vein takes the colour
	 * of its rarity and a cluster the plain cluster grey.
	 */
	public VeinRecipeJS waypointColor(String color) {
		setValue(WAYPOINT_COLOR, color);
		return this;
	}
}
