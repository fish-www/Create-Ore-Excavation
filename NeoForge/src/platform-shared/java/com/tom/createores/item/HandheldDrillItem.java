package com.tom.createores.item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import com.tom.createores.Config;
import com.tom.createores.client.DrillBars;
import com.tom.createores.components.OreVeinAtlasDataComponent;
import com.tom.createores.item.OreVeinAtlasItem;
import com.tom.createores.util.DimChunkPos;
import com.tom.createores.CreateOreExcavation;
import com.tom.createores.OreData;
import com.tom.createores.OreDataAttachment;
import com.tom.createores.VeinRegeneration;
import com.tom.createores.recipe.DrillingRecipe;
import com.tom.createores.recipe.VeinRecipe;
import com.tom.createores.OreVeinGenerator;
import com.tom.createores.Registration;
import com.tom.createores.network.OreVeinDiscoverPacket;
import com.tom.createores.network.VeinMarkers;
import com.tom.createores.util.NumberFormatter;
import com.tom.createores.util.RandomSpreadGenerator;

import com.mojang.datafixers.util.Pair;

/**
 * Mines ultra small veins by hand. Hold it in either hand and right click to mine, hold a fuel item
 * in the other hand and right click to load it. Fuel is stored on the item in fuel units.
 */
public class HandheldDrillItem extends Item {
	public static final int USE_DURATION = 72000;

	public HandheldDrillItem(Properties properties) {
		super(properties);
	}

	public static int getFuel(ItemStack stack) {
		return stack.getOrDefault(CreateOreExcavation.HAND_DRILL_FUEL, 0);
	}

	public static void setFuel(ItemStack stack, int fuel) {
		stack.set(CreateOreExcavation.HAND_DRILL_FUEL, Math.max(0, fuel));
	}

	public static int burnTimeOf(ItemStack stack) {
		return stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (addFuel(level, player, stack, player.getItemInHand(otherHand(hand))))return InteractionResultHolder.success(stack);
		// Nothing to drill against, so the drill acts as a cluster finder instead
		if (!level.isClientSide)scanClusters((ServerLevel) level, (ServerPlayer) player);
		return InteractionResultHolder.success(stack);
	}

	/**
	 * Loading fuel runs before whatever block is clicked, otherwise a chest or a crafting table would
	 * swallow the click instead of taking the fuel.
	 */
	@Override
	public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
		Player player = ctx.getPlayer();
		if (player == null)return InteractionResult.PASS;
		if (!addFuel(ctx.getLevel(), player, stack, player.getItemInHand(otherHand(ctx.getHand()))))return InteractionResult.PASS;
		return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Player player = ctx.getPlayer();
		if (player == null)return InteractionResult.PASS;
		ItemStack stack = ctx.getItemInHand();
		if (!isDrillSurface(ctx.getLevel().getBlockState(ctx.getClickedPos()))) {
			// Nothing to drill against, so the drill looks for ore clusters instead. The block still gets
			// its own interaction, opening a chest with the drill in hand keeps working.
			if (!ctx.getLevel().isClientSide)scanClusters((ServerLevel) ctx.getLevel(), (ServerPlayer) player);
			return InteractionResult.PASS;
		}
		// The drill has to complain before it starts swinging, not after the first extraction
		if (getFuel(stack) < Config.handDrillFuelPerUnit) {
			if (!ctx.getLevel().isClientSide)player.displayClientMessage(Component.translatable("chat.coe.handDrill.noFuel"), true);
			return InteractionResult.FAIL;
		}
		if (!ctx.getLevel().isClientSide) {
			ServerLevel serverLevel = (ServerLevel) ctx.getLevel();
			RecipeHolder<VeinRecipe> cluster = findCluster(serverLevel, player);
			if (cluster == null) {
				boolean depleted = OreDataAttachment.getData(serverLevel.getChunkAt(player.blockPosition())).getInstance(true) != null;
				player.displayClientMessage(Component.translatable(depleted ? "chat.coe.cluster.depleted" : "chat.coe.cluster.none"), true);
				return InteractionResult.FAIL;
			}
		}
		player.startUsingItem(ctx.getHand());
		return InteractionResult.CONSUME;
	}

	private static InteractionHand otherHand(InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	/**
	 * The drill needs something solid to work against, any block in
	 * {@link CreateOreExcavation#HANDHELD_DRILL_SURFACE} (stone and bedrock by default).
	 */
	private static boolean isDrillSurface(BlockState state) {
		return state.is(CreateOreExcavation.HANDHELD_DRILL_SURFACE);
	}

	/**
	 * Burns one item of the other hand into the drill.
	 */
	private static boolean addFuel(Level level, Player player, ItemStack stack, ItemStack fuel) {
		int burn = burnTimeOf(fuel);
		if (burn <= 0)return false;
		if (getFuel(stack) >= Config.handDrillFuelCapacity) {
			if (!level.isClientSide)player.displayClientMessage(Component.translatable("chat.coe.handDrill.full"), true);
			return true;
		}
		if (!level.isClientSide) {
			setFuel(stack, Math.min(Config.handDrillFuelCapacity, getFuel(stack) + burn));
			fuel.shrink(1);
			player.displayClientMessage(Component.translatable("chat.coe.handDrill.fuel", NumberFormatter.formatNumber(getFuel(stack)), NumberFormatter.formatNumber(Config.handDrillFuelCapacity)), true);
		}
		return true;
	}

	@Override
	public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {
		if (level.isClientSide || !(living instanceof Player player))return;
		int ticks = getUseDuration(stack, living) - remaining;
		if (ticks <= 0 || ticks % Math.max(1, Config.handDrillTicks) != 0)return;
		if (!mineOne(level, player, stack))player.stopUsingItem();
	}

	private boolean mineOne(Level level, Player player, ItemStack stack) {
		RecipeHolder<VeinRecipe> vein = findCluster(level, player);
		if (vein == null) {
			boolean depleted = OreDataAttachment.getData(level.getChunkAt(player.blockPosition())).getInstance(true) != null;
			player.displayClientMessage(Component.translatable(depleted ? "chat.coe.cluster.depleted" : "chat.coe.cluster.none"), true);
			return false;
		}
		if (getFuel(stack) < Config.handDrillFuelPerUnit) {
			player.displayClientMessage(Component.translatable("chat.coe.handDrill.noFuel"), true);
			return false;
		}
		setFuel(stack, getFuel(stack) - Config.handDrillFuelPerUnit);
		OreData data = OreDataAttachment.getData(level.getChunkAt(player.blockPosition()));
		data.extract(vein.id(), 1, level.getGameTime());
		level.getChunkAt(player.blockPosition()).setUnsaved(true);
		addToAtlas(player, (ServerLevel) level, vein, data.getInstance(vein.id()));
		showReserves(player, vein, data);
		if (data.getResourcesRemaining(vein.id(), level.getGameTime()) == -1L)cleanUpDepletedCluster((ServerLevel) level, (ServerPlayer) player, vein);

		for (RecipeHolder<DrillingRecipe> rec : level.getRecipeManager().getAllRecipesFor(CreateOreExcavation.DRILLING_RECIPES.getRecipeType())) {
			if (rec.value().veinId.equals(vein.id())) {
				rec.value().getOutput().stream().map(o -> o.rollOutput(level.random)).filter(i -> !i.isEmpty()).forEach(i -> {
					if (!player.getInventory().add(i))player.drop(i, false);
				});
				break;
			}
		}
		return true;
	}

	private static RecipeHolder<VeinRecipe> findCluster(Level level, Player player) {
		OreData data = OreDataAttachment.getData(level.getChunkAt(player.blockPosition()));
		return data.getClusterRecipe(level.getRecipeManager());
	}

	/**
	 * A depleted cluster stops existing: its marker and its atlas position go away.
	 */
	private static void cleanUpDepletedCluster(ServerLevel level, ServerPlayer player, RecipeHolder<VeinRecipe> cluster) {
		ChunkPos pos = new ChunkPos(player.blockPosition());
		BlockPos at = pos.getMiddleBlockPosition(player.getBlockY());
		VeinMarkers.sendRemove(player, level.dimension(), List.of(new OreVeinDiscoverPacket.Entry(at, cluster.id(), true)));
		removeAtlasPosition(player, level, pos);
		player.displayClientMessage(Component.translatable("chat.coe.cluster.cleared", cluster.value().getName()), false);
	}

	/**
	 * Keeps the player informed about how much is left while mining.
	 */
	private static void showReserves(Player player, RecipeHolder<VeinRecipe> cluster, OreData data) {
		OreData.VeinInstance instance = data.getInstance(cluster.id());
		if (instance == null)return;
		long total = instance.total;
		long remaining = Math.max(0L, total - instance.extracted);
		player.displayClientMessage(Component.translatable("chat.coe.cluster.remaining", cluster.value().getName(),
				NumberFormatter.formatNumber(remaining), NumberFormatter.formatNumber(total)), true);
	}

	/**
	 * Looks for ore clusters around the player: marks the ones within {@link Config#handDrillRadius}
	 * chunks on the map, drops the markers of depleted ones, and reports the closest cluster when the chunk
	 * the player stands in has none. Only ever runs when the player right clicks the drill.
	 */
	public static void scanClusters(ServerLevel level, ServerPlayer player) {
		int radius = Config.handDrillRadius;
		ChunkPos center = new ChunkPos(player.blockPosition());
		RandomSpreadGenerator picker = OreVeinGenerator.getPicker(level);
		long seed = VeinRegeneration.seedFor(level, OreVeinDiscoverPacket.Kind.CLUSTER);
		Predicate<RecipeHolder<VeinRecipe>> filter = clusterFilter(player);
		RecipeManager recipeManager = level.getRecipeManager();
		RecipeHolder<VeinRecipe> here = findCluster(level, player);
		List<OreVeinDiscoverPacket.Entry> found = new ArrayList<>();
		List<OreVeinDiscoverPacket.Entry> gone = new ArrayList<>();
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				ChunkPos cp = new ChunkPos(center.x + x, center.z + z);
				RandomSpreadGenerator.PickResult pick = picker.pick(level, cp, true, seed);
				if (pick == null || !filter.test(pick.recipe()))continue;
				OreVeinDiscoverPacket.Entry entry = new OreVeinDiscoverPacket.Entry(cp.getMiddleBlockPosition(player.getBlockY()), pick.recipe().id(), true);
				// Only loaded chunks can tell whether their cluster still has anything in it
				LevelChunk chunk = level.getChunkSource().getChunkNow(cp.x, cp.z);
				if (chunk != null && isDepleted(OreDataAttachment.getData(chunk), recipeManager, level.getGameTime())) {
					gone.add(entry);
					removeAtlasPosition(player, level, cp);
					continue;
				}
				found.add(entry);
			}
		}
		if (!gone.isEmpty())VeinMarkers.sendRemove(player, level.dimension(), gone);
		int marked = VeinMarkers.sendNew(player, level.dimension(), found);

		if (here != null) {
			OreData data = OreDataAttachment.getData(level.getChunkAt(player.blockPosition()));
			OreData.VeinInstance instance = data.getInstance(here.id());
			if (instance != null) {
				player.displayClientMessage(Component.translatable("chat.coe.cluster.found", here.value().getName(),
						NumberFormatter.formatNumber(Math.max(0L, instance.total - instance.extracted)),
						NumberFormatter.formatNumber(instance.total)), false);
			} else {
				player.displayClientMessage(Component.translatable("chat.coe.cluster.foundNoAmount", here.value().getName()), false);
			}
		} else {
			if (isDepleted(OreDataAttachment.getData(level.getChunkAt(player.blockPosition())), recipeManager, level.getGameTime()))
				player.displayClientMessage(Component.translatable("chat.coe.cluster.depleted"), false);
			// No cluster here, so point at the closest one the way the vein finder does
			Pair<BlockPos, RecipeHolder<VeinRecipe>> nearest = picker.locate(player.blockPosition(), level, Config.handDrillSearchRadius, true, filter, seed);
			if (nearest != null) {
				int distance = Math.round(RandomSpreadGenerator.distance2d(nearest.getFirst(), player.blockPosition()) / Config.veinFinderFar) * Config.veinFinderFar;
				player.displayClientMessage(Component.translatable("chat.coe.cluster.far",
						Component.translatable("chat.coe.veinFinder.distance", nearest.getSecond().value().getName(), distance)), false);
			} else {
				player.displayClientMessage(Component.translatable("chat.coe.cluster.farNone"), false);
			}
		}

		if (!found.isEmpty()) {
			player.displayClientMessage(Component.translatable(marked > 0 ? "chat.coe.cluster.nearby" : "chat.coe.cluster.nearbyNone",
					marked > 0 ? marked : found.size()), false);
		}
	}

	/**
	 * The atlas only excludes cluster types, a vein target does not hide clusters.
	 */
	private static Predicate<RecipeHolder<VeinRecipe>> clusterFilter(Player player) {
		OreVeinAtlasDataComponent atlas = OreVeinAtlasItem.findAtlas(player).get(CreateOreExcavation.ORE_VEIN_ATLAS_DATA_COMPONENT);
		if (atlas == null || atlas.exclude().isEmpty())return r -> true;
		Set<ResourceLocation> exclude = new HashSet<>(atlas.exclude());
		return r -> !exclude.contains(r.id());
	}

	private static boolean isDepleted(OreData data, RecipeManager recipeManager, long gameTime) {
		OreData.VeinInstance instance = data.getInstance(true);
		if (instance == null)return false;
		return data.getResourcesRemaining(instance.recipe, gameTime) == -1L;
	}

	private static void removeAtlasPosition(Player player, ServerLevel level, ChunkPos pos) {
		OreVeinAtlasItem.removeVein(player, new DimChunkPos(level, pos));
	}

	/**
	 * Puts the cluster into the atlas of the player, if they carry one.
	 */
	private static void addToAtlas(Player player, ServerLevel level, RecipeHolder<VeinRecipe> cluster, OreData.VeinInstance instance) {
		ItemStack atlas = OreVeinAtlasItem.findAtlas(player);
		if (atlas.isEmpty())return;
		DimChunkPos pos = new DimChunkPos(level, new ChunkPos(player.blockPosition()));
		OreVeinAtlasDataComponent tag = atlas.get(CreateOreExcavation.ORE_VEIN_ATLAS_DATA_COMPONENT);
		if (tag != null) {
			boolean known = tag.veins().stream().anyMatch(e -> e.getFirst().equals(pos) && e.getSecond().id().equals(cluster.id()));
			if (known)return;
		}
		Registration.VEIN_ATLAS_ITEM.get().addVein(player, atlas, cluster, pos, instance.total, "chat.coe.cluster.addedToAtlas");
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return USE_DURATION;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	/**
	 * The item bar shows the mining progress while the drill is in use and the fuel otherwise.
	 */
	@Override
	public boolean isBarVisible(ItemStack stack) {
		return DrillBars.getMiningProgress(stack) > 0 || getFuel(stack) > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		float progress = DrillBars.getMiningProgress(stack);
		if (progress > 0)return Math.max(1, Math.round(13F * progress));
		return Math.min(13, Math.round(13F * getFuel(stack) / Math.max(1, Config.handDrillFuelCapacity)));
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return DrillBars.getMiningProgress(stack) > 0 ? 0x00E000 : 0x00A0FF;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.coe.handDrill.fuel", NumberFormatter.formatNumber(getFuel(stack)), NumberFormatter.formatNumber(Config.handDrillFuelCapacity)));
		tooltip.add(Component.translatable("tooltip.coe.handDrill.hint"));
	}
}
