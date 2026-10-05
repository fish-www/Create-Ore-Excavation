package com.tom.createores.recipe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.tom.createores.Config;
import com.tom.createores.CreateOreExcavation;
import com.tom.createores.biome.ReserveRange;
import com.tom.createores.util.ThreeState;
import com.tom.createores.util.TimeFormatter;

public class VeinRecipe implements Recipe<CraftingInput> {
	/** Ticks a depleted vein takes to fill back up by default, unless the recipe says otherwise: 3 real days. */
	public static final int DEFAULT_REGEN_TICKS = 3 * 24 * 60 * 60 * 20;
	/**
	 * Chunk coordinates the vein is pinned to, on top of the grids its distributions describe. A pinned
	 * chunk ignores the biome filters and always picks this vein.
	 */
	public static final Codec<ChunkPos> CHUNK_CODEC = Codec.INT.listOf(2, 2)
			.xmap(l -> new ChunkPos(l.get(0), l.get(1)), pos -> List.of(pos.x, pos.z));
	public static final Codec<List<ChunkPos>> CHUNKS_CODEC = CHUNK_CODEC.listOf();

	public int priority;
	public Component veinName;
	public TagKey<Biome> biomeWhitelist, biomeBlacklist;
	public ThreeState finite;
	/**
	 * The grid the vein lays over every biome: the spacing in chunks ({@code 0} puts one in every
	 * chunk, a negative value keeps it off the grid entirely), the reserve range one holds and how
	 * many ticks a depleted vein takes to fill back up ({@code 0} = never).
	 */
	public int density;
	public ReserveRange reserve;
	public int regenTicks;
	public RandomSpreadStructurePlacement placement;
	public ItemStack icon;
	public VeinRarity rarity = VeinRarity.COMMON;
	public boolean cluster;
	/**
	 * Name of the colour the map marker of this vein uses, e.g. {@code gold}. Left empty the colour
	 * falls back to the rarity for a vein and to grey for a cluster. Read by the Xaero integration,
	 * which is why it is only a name here.
	 */
	public String waypointColor = "";
	public List<BiomeOverride> biomeOverrides = List.of();
	public List<ChunkPos> chunks = List.of();
	private LongSet chunkIndex;
	protected boolean isNet;

	public VeinRecipe() {
	}

	public VeinRecipe(Component veinName, int priority, Optional<TagKey<Biome>> biomeWhitelist, Optional<TagKey<Biome>> biomeBlacklist,
			ThreeState finite, int density, ReserveRange reserve, int regenTicks,
			RandomSpreadStructurePlacement placement, ItemStack icon, VeinRarity rarity, boolean cluster,
			List<BiomeOverride> biomeOverrides, List<ChunkPos> chunks, String waypointColor) {
		this.veinName = veinName;
		this.priority = priority;
		this.biomeWhitelist = biomeWhitelist.orElse(null);
		this.biomeBlacklist = biomeBlacklist.orElse(null);
		this.finite = finite;
		this.density = density;
		this.reserve = reserve;
		this.regenTicks = regenTicks;
		this.placement = placement;
		this.icon = icon;
		this.rarity = rarity;
		this.cluster = cluster;
		this.biomeOverrides = biomeOverrides;
		this.chunks = chunks;
		this.waypointColor = waypointColor;
	}

	public List<ChunkPos> getChunks() {
		return chunks;
	}

	/**
	 * @return whether the chunk is pinned to this vein. Checked for every vein on every chunk load, so
	 *         the list is turned into an index the first time it is used.
	 */
	public boolean isFixedChunk(ChunkPos pos) {
		if (chunks.isEmpty())return false;
		LongSet index = chunkIndex;
		if (index == null) {
			LongOpenHashSet set = new LongOpenHashSet(chunks.size());
			for (ChunkPos c : chunks)set.add(c.toLong());
			chunkIndex = index = set;
		}
		return index.contains(pos.toLong());
	}

	@Override
	public boolean matches(CraftingInput pContainer, Level pLevel) {
		return false;
	}

	@Override
	public ItemStack assemble(CraftingInput p_44001_, HolderLookup.Provider p_267165_) {
		return getResultItem(p_267165_);
	}

	@Override
	public boolean canCraftInDimensions(int pWidth, int pHeight) {
		return true;
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider p_267052_) {
		return ItemStack.EMPTY;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return CreateOreExcavation.VEIN_RECIPES.getSerializer();
	}

	@Override
	public RecipeType<?> getType() {
		return CreateOreExcavation.VEIN_RECIPES.getRecipeType();
	}

	public Component getName() {
		return veinName;
	}

	@Override
	public String getGroup() {
		return "ore_vein_type";
	}

	public boolean canGenerate(Holder<Biome> b, RegistryAccess registries) {
		if(biomeBlacklist != null && isInTag(biomeBlacklist, registries, b))return false;
		if(biomeWhitelist != null) {
			return isInTag(biomeWhitelist, registries, b);
		} else
			return true;
	}

	private static boolean isInTag(TagKey<Biome> tag, RegistryAccess registries, Holder<Biome> b) {
		return registries.registryOrThrow(Registries.BIOME).getTag(tag).map(t -> t.contains(b)).orElse(false);
	}

	public ThreeState isFinite() {
		return cluster ? ThreeState.ALWAYS : finite;
	}

	public VeinRarity getRarity() {
		return rarity;
	}

	public boolean isRare() {
		return rarity == VeinRarity.RARE;
	}

	/**
	 * Ultra small veins are only minable by hand, deplete for good and never regenerate.
	 */
	public boolean isCluster() {
		return cluster;
	}

	/**
	 * One distribution of this vein: how far apart the veins of its grid are, and how much one holds.
	 * A spacing of {@code 0} means the layer is not on a grid at all.
	 */
	public record Layer(int spacing, ReserveRange reserve) {
	}

	/**
	 * Maps the density a recipe declares to the grid spacing it means: a negative density keeps the
	 * vein off the grid, {@code 0} puts one in every chunk and a positive value is the spacing in
	 * chunks.
	 */
	public static int gridSpacing(int density) {
		return density < 0 ? 0 : Math.max(1, density);
	}

	/**
	 * @return the base distribution of the vein, active in every biome the vein generates in
	 */
	public Layer defaultLayer() {
		return new Layer(gridSpacing(density), reserve);
	}

	/**
	 * The distributions this vein has in the biome. The default distribution is the base grid the vein
	 * has everywhere, and every entry naming the biome stacks one more grid on top of it, so a biome can
	 * carry the default distribution and several special ones at the same time. The list order decides
	 * which one wins a chunk that more than one grid claims, the special entries come first.
	 */
	public List<Layer> layers(Holder<Biome> biome, RegistryAccess registries) {
		List<Layer> layers = new ArrayList<>();
		Layer def = defaultLayer();
		ResourceLocation biomeId = biome != null ? biome.unwrapKey().map(k -> k.location()).orElse(null) : null;
		for (BiomeOverride override : biomeOverrides) {
			if (override.matches(biomeId, biome, registries))layers.add(layerOf(override, def));
		}
		layers.add(def);
		return layers;
	}

	/**
	 * The game guide lists one entry per distribution of a vein: entry 0 is the default distribution
	 * every biome of the vein gets, entry {@code i} is the distribution of {@code biomeOverrides[i - 1]}.
	 */
	public int entryCount() {
		return 1 + biomeOverrides.size();
	}

	/**
	 * @return the name of the biome entry behind a guide entry, shown as the suffix of the page so the
	 *         special distributions of a vein can be told apart from the default one
	 */
	public Component entryName(int entry) {
		if (entry <= 0 || entry > biomeOverrides.size())return Component.empty();
		return biomeOverrides.get(entry - 1).getDisplayName();
	}

	/**
	 * @return the distribution of one guide entry, entry 0 being the default one
	 */
	public Layer layerForEntry(int entry) {
		Layer def = defaultLayer();
		if (entry <= 0 || entry > biomeOverrides.size())return def;
		return layerOf(biomeOverrides.get(entry - 1), def);
	}

	/**
	 * @return whether that distribution takes effect in the biome. That is what the guide lists as the
	 *         whitelist of the entry, every other biome being its blacklist.
	 */
	/**
	 * @return whether the distribution generates in the world, either on a grid or pinned to chunks.
	 *         Distributions that never generate are left out of JEI.
	 */
	public boolean entryGenerates(int entry) {
		return layerForEntry(entry).spacing() > 0 || !chunks.isEmpty();
	}

	public boolean entryApplies(Holder<Biome> biome, int entry, RegistryAccess registries) {
		if (layerForEntry(entry).spacing() <= 0 || !canGenerate(biome, registries))return false;
		if (entry <= 0)return true;
		ResourceLocation biomeId = biome != null ? biome.unwrapKey().map(k -> k.location()).orElse(null) : null;
		return biomeOverrides.get(entry - 1).matches(biomeId, biome, registries);
	}

	private static Layer layerOf(BiomeOverride override, Layer def) {
		return new Layer(
				override.density().map(VeinRecipe::gridSpacing).orElse(def.spacing()),
				override.reserve().orElse(def.reserve()));
	}

	/**
	 * @return every grid spacing this vein uses in some biome, the candidate chunks of a search have
	 *         to be enumerated once per grid
	 */
	public Set<Integer> spacings() {
		Set<Integer> spacings = new LinkedHashSet<>();
		int own = gridSpacing(density);
		if (own > 0)spacings.add(own);
		for (BiomeOverride override : biomeOverrides) {
			if (override.density().isEmpty())continue;
			int s = gridSpacing(override.density().get());
			if (s > 0)spacings.add(s);
		}
		if (spacings.isEmpty())spacings.add(placement.spacing());
		return spacings;
	}

	public List<BiomeOverride> getBiomeOverrides() {
		return biomeOverrides;
	}

	/**
	 * A second distribution of a vein in specific biomes. {@code target} is a biome tag
	 * ({@code #ns:tag}) or a biome id ({@code ns:biome}), every entry matching the biome adds a grid,
	 * and the entries are tested in order. An entry leaves out the density or the reserve to keep the
	 * default of the vein for it.
	 */
	public static record BiomeOverride(String target, Optional<Integer> density, Optional<ReserveRange> reserve) {
		public static final Codec<BiomeOverride> CODEC = RecordCodecBuilder.create(b -> {
			return b.group(
					Codec.STRING.fieldOf("target").forGetter(BiomeOverride::target),
					Codec.INT.optionalFieldOf("density").forGetter(BiomeOverride::density),
					ReserveRange.CODEC.optionalFieldOf("reserve").forGetter(BiomeOverride::reserve)
					).apply(b, BiomeOverride::new);
		});

		/**
		 * @return the name of the biomes the entry covers: the name of the single biome, or the name of the
		 *         tag, which a pack gives with {@code biome_tag.<namespace>.<path>} and which falls back to
		 *         the tag id itself when no such key exists
		 */
		public Component getDisplayName() {
			ResourceLocation id = ResourceLocation.tryParse(target.startsWith("#") ? target.substring(1) : target);
			if (id == null)return Component.literal(target);
			if (target.startsWith("#"))return Component.translatableWithFallback("biome_tag." + id.getNamespace() + "." + id.getPath(), id.toString());
			return Component.translatable("biome." + id.getNamespace() + "." + id.getPath());
		}

		public boolean matches(ResourceLocation biomeId, Holder<Biome> biome, RegistryAccess registries) {
			if (target.startsWith("#")) {
				if (biome == null || registries == null)return false;
				ResourceLocation rl = ResourceLocation.tryParse(target.substring(1));
				if (rl == null)return false;
				TagKey<Biome> tag = TagKey.create(Registries.BIOME, rl);
				return registries.registryOrThrow(Registries.BIOME).getTag(tag).map(t -> t.contains(biome)).orElse(false);
			}
			ResourceLocation rl = ResourceLocation.tryParse(target);
			return rl != null && rl.equals(biomeId);
		}
	}

	public int getDensity() {
		return density;
	}

	public ReserveRange getReserve() {
		return reserve;
	}

	public int getRegenTicks() {
		return regenTicks;
	}

	/**
	 * @return the line describing how this vein regenerates, meant for tooltips
	 */
	public MutableComponent getRegenDescription() {
		if (cluster)return Component.translatable("jei.coe.cluster_depleted");
		if (regenTicks <= 0)return Component.translatable("tooltip.coe.regen.none");
		return Component.translatable("tooltip.coe.regen", TimeFormatter.formatTicks(regenTicks));
	}

	public boolean isInfiniteClient() {
		if (cluster)return false;
		return isNet ? finite == ThreeState.NEVER : finite == ThreeState.DEFAULT ? Config.defaultInfinite : finite == ThreeState.NEVER;
	}

	public int getNegGenerationPriority() {
		return -priority;
	}

	public int getPriority() {
		return priority;
	}

	public RandomSpreadStructurePlacement getPlacement() {
		return placement;
	}

	public Optional<TagKey<Biome>> biomeWhitelist() {
		return Optional.ofNullable(biomeWhitelist);
	}

	public Optional<TagKey<Biome>> biomeBlacklist() {
		return Optional.ofNullable(biomeBlacklist);
	}

	public ItemStack getIcon() {
		return icon;
	}

	public String getWaypointColor() {
		return waypointColor;
	}

	public static class Serializer implements RecipeSerializer<VeinRecipe> {
		private static final ResourceLocation NULL = ResourceLocation.tryParse("coe:null");

		private static VeinRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
			VeinRecipe r = new VeinRecipe();
			r.priority = buffer.readVarInt();
			r.veinName = ComponentSerialization.STREAM_CODEC.decode(buffer);
			r.biomeWhitelist = create(buffer);
			r.biomeBlacklist = create(buffer);
			r.finite = buffer.readBoolean() ? ThreeState.ALWAYS : ThreeState.NEVER;
			r.density = buffer.readVarInt();
			r.reserve = readReserve(buffer);
			r.regenTicks = buffer.readVarInt();
			r.icon = ItemStack.STREAM_CODEC.decode(buffer);
			r.rarity = buffer.readBoolean() ? VeinRarity.RARE : VeinRarity.COMMON;
			r.cluster = buffer.readBoolean();
			int chunkCount = buffer.readVarInt();
			List<ChunkPos> chunks = new ArrayList<>(chunkCount);
			for (int i = 0; i < chunkCount; i++) {
				chunks.add(new ChunkPos(buffer.readVarInt(), buffer.readVarInt()));
			}
			r.chunks = chunks;
			int spacing = Math.max(1, buffer.readVarInt());
			int separation = Math.max(0, buffer.readVarInt());
			r.waypointColor = buffer.readUtf(32);
			int overrideCount = buffer.readVarInt();
			List<BiomeOverride> overrides = new ArrayList<>(overrideCount);
			for (int i = 0; i < overrideCount; i++) {
				overrides.add(new BiomeOverride(buffer.readUtf(256),
						buffer.readBoolean() ? Optional.of(buffer.readVarInt()) : Optional.empty(),
						buffer.readBoolean() ? Optional.of(readReserve(buffer)) : Optional.empty()));
			}
			r.biomeOverrides = overrides;
			r.isNet = true;
			r.placement = new RandomSpreadStructurePlacement(spacing, Math.min(separation, spacing - 1), RandomSpreadType.LINEAR, 0);
			return r;
		}

		private static TagKey<Biome> create(FriendlyByteBuf buffer) {
			ResourceLocation rl = buffer.readResourceLocation();
			if(NULL.equals(rl))return null;
			else return TagKey.create(Registries.BIOME, rl);
		}

		private static ReserveRange readReserve(FriendlyByteBuf buffer) {
			return new ReserveRange(buffer.readVarLong(), buffer.readVarLong(), buffer.readDouble(), buffer.readDouble());
		}

		private static void toNetwork(RegistryFriendlyByteBuf buffer, VeinRecipe recipe) {
			buffer.writeVarInt(recipe.priority);
			ComponentSerialization.STREAM_CODEC.encode(buffer, recipe.veinName);
			write(recipe.biomeWhitelist, buffer);
			write(recipe.biomeBlacklist, buffer);
			buffer.writeBoolean(recipe.finite == ThreeState.DEFAULT ? !Config.defaultInfinite : recipe.finite == ThreeState.ALWAYS);
			buffer.writeVarInt(recipe.density);
			writeReserve(recipe.reserve, buffer);
			buffer.writeVarInt(recipe.regenTicks);
			ItemStack.STREAM_CODEC.encode(buffer, recipe.icon);
			buffer.writeBoolean(recipe.rarity == VeinRarity.RARE);
			buffer.writeBoolean(recipe.cluster);
			buffer.writeVarInt(recipe.chunks.size());
			for (ChunkPos c : recipe.chunks) {
				buffer.writeVarInt(c.x);
				buffer.writeVarInt(c.z);
			}
			buffer.writeVarInt(recipe.placement.spacing());
			buffer.writeVarInt(recipe.placement.separation());
			buffer.writeUtf(recipe.waypointColor, 32);
			buffer.writeVarInt(recipe.biomeOverrides.size());
			for (BiomeOverride override : recipe.biomeOverrides) {
				buffer.writeUtf(override.target(), 256);
				buffer.writeBoolean(override.density().isPresent());
				if (override.density().isPresent())buffer.writeVarInt(override.density().get());
				buffer.writeBoolean(override.reserve().isPresent());
				if (override.reserve().isPresent())writeReserve(override.reserve().get(), buffer);
			}
		}

		private static void writeReserve(ReserveRange range, FriendlyByteBuf buffer) {
			buffer.writeVarLong(range.min());
			buffer.writeVarLong(range.max());
			buffer.writeDouble(range.mean());
			buffer.writeDouble(range.sigma());
		}

		private static void write(TagKey<Biome> tag, FriendlyByteBuf buffer) {
			buffer.writeResourceLocation(tag != null ? tag.location() : NULL);
		}

		public static final MapCodec<VeinRecipe> CODEC = RecordCodecBuilder.<VeinRecipe>mapCodec(b -> {
			return b.group(
					ComponentSerialization.FLAT_CODEC.fieldOf("name").forGetter(VeinRecipe::getName),
					Codec.INT.fieldOf("priority").forGetter(VeinRecipe::getPriority),
					TagKey.codec(Registries.BIOME).optionalFieldOf("biomeWhitelist").forGetter(VeinRecipe::biomeWhitelist),
					TagKey.codec(Registries.BIOME).optionalFieldOf("biomeBlacklist").forGetter(VeinRecipe::biomeBlacklist),
					ThreeState.CODEC.fieldOf("finite").forGetter(VeinRecipe::isFinite),
					Codec.INT.fieldOf("density").forGetter(VeinRecipe::getDensity),
					ReserveRange.CODEC.fieldOf("reserve").forGetter(VeinRecipe::getReserve),
					Codec.INT.optionalFieldOf("regenTicks", DEFAULT_REGEN_TICKS).forGetter(VeinRecipe::getRegenTicks),
					RandomSpreadStructurePlacement.CODEC.fieldOf("placement").forGetter(VeinRecipe::getPlacement),
					ItemStack.CODEC.fieldOf("icon").forGetter(VeinRecipe::getIcon),
					VeinRarity.CODEC.optionalFieldOf("rarity", VeinRarity.COMMON).forGetter(VeinRecipe::getRarity),
					Codec.BOOL.optionalFieldOf("cluster", false).forGetter(VeinRecipe::isCluster),
					BiomeOverride.CODEC.listOf().optionalFieldOf("biomeOverrides", List.of()).forGetter(VeinRecipe::getBiomeOverrides),
					CHUNKS_CODEC.optionalFieldOf("chunks", List.of()).forGetter(VeinRecipe::getChunks),
					Codec.STRING.optionalFieldOf("waypointColor", "").forGetter(VeinRecipe::getWaypointColor)
					).apply(b, VeinRecipe::new);
		});

		public static final StreamCodec<RegistryFriendlyByteBuf, VeinRecipe> STREAM_CODEC = StreamCodec.of(
				VeinRecipe.Serializer::toNetwork, VeinRecipe.Serializer::fromNetwork
				);

		@Override
		public MapCodec<VeinRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, VeinRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}

	@FunctionalInterface
	public static interface RecipeFactory<T extends VeinRecipe> {
		T create(ResourceLocation id, RecipeType<?> type, RecipeSerializer<?> serializer);
	}
}
