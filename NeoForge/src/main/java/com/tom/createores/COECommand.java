package com.tom.createores;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.client.ClientCommandSourceStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import com.google.common.base.Stopwatch;

import com.tom.createores.network.NetworkHandler;
import com.tom.createores.network.OreVeinDiscoverPacket;
import com.tom.createores.recipe.VeinRecipe;
import com.tom.createores.util.RandomSpreadGenerator;

public class COECommand {
	private static final DynamicCommandExceptionType ERROR_VEIN_NOT_FOUND = new DynamicCommandExceptionType((p_214514_) -> {
		return Component.translatable("command.coe.locate.failed", p_214514_);
	});

	public static void init() {}

	public static void register(RegisterCommandsEvent evt) {
		LiteralArgumentBuilder<CommandSourceStack> l = Commands.literal("coe");
		l.then(Commands.literal("setvein").requires(s -> s.hasPermission(2)).
				then(Commands.argument("pos", BlockPosArgument.blockPos()).
						then(Commands.argument("recipe", ResourceLocationArgument.id()).suggests(ALL_RECIPES).
								executes(c -> {
									BlockPos p = BlockPosArgument.getLoadedBlockPos(c, "pos");
									RecipeHolder<?> rl = ResourceLocationArgument.getRecipe(c, "recipe");
									if(rl.value() instanceof VeinRecipe) {
										setVein(c.getSource(), p, rl.id(), (VeinRecipe) rl.value(), 1F);
										c.getSource().sendSuccess(() -> Component.translatable("command.coe.setvein.success", rl.id().toString()), true);
										return 1;
									}
									return 0;
								}).
								then(Commands.argument("multiplier", FloatArgumentType.floatArg(0, 1000)).
										executes(c -> {
											float mul = FloatArgumentType.getFloat(c, "multiplier");
											BlockPos p = BlockPosArgument.getLoadedBlockPos(c, "pos");
											RecipeHolder<?> rl = ResourceLocationArgument.getRecipe(c, "recipe");
											if(rl.value() instanceof VeinRecipe) {
												setVein(c.getSource(), p, rl.id(), (VeinRecipe) rl.value(), mul);
												c.getSource().sendSuccess(() -> Component.translatable("command.coe.setvein.success", rl.id().toString()), true);
												return 1;
											}
											return 0;
										})
										)
								)
						)
				);
		l.then(Commands.literal("removevein").requires(s -> s.hasPermission(2)).
				then(Commands.argument("pos", BlockPosArgument.blockPos()).
						executes(c -> {
							BlockPos p = BlockPosArgument.getLoadedBlockPos(c, "pos");
							setVein(c.getSource(), p, null, null, 0F);
							c.getSource().sendSuccess(() -> Component.translatable("command.coe.setvein.success", Component.translatable("chat.coe.veinFinder.nothing")), true);
							return 1;
						})
						)
				);
		l.then(Commands.literal("locate").requires(s -> s.hasPermission(2)).
				then(Commands.argument("recipe", ResourceLocationArgument.id()).suggests(ALL_RECIPES).
						executes(c -> {
							RecipeHolder<?> rl = ResourceLocationArgument.getRecipe(c, "recipe");
							if(rl.value() instanceof VeinRecipe) {
								BlockPos blockpos = BlockPos.containing(c.getSource().getPosition());
								Stopwatch stopwatch = Stopwatch.createStarted(Util.TICKER);
								BlockPos at;
				var nearest = OreVeinGenerator.getPicker(c.getSource().getLevel()).locate(blockpos, c.getSource().getLevel(), 100, h -> h.id().equals(rl.id()), VeinRegeneration.seedFor(c.getSource().getLevel(), OreVeinDiscoverPacket.Kind.VEIN));
				at = nearest != null ? nearest.getFirst() : null;
								stopwatch.stop();
								if(at != null) {
									int i = Mth.floor(RandomSpreadGenerator.distance2d(at, blockpos));
									Component component = ComponentUtils.wrapInSquareBrackets(Component.translatable("chat.coordinates", at.getX(), "~", at.getZ())).withStyle(tc -> {
										return tc.withColor(ChatFormatting.GREEN).
												withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + at.getX() + " ~ " + at.getZ())).
												withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.coordinates.tooltip")));
									});
									c.getSource().sendSuccess(() -> {
										return Component.translatable("command.coe.locate.success", rl.id().toString(), component, i);
									}, false);
					markDiscovered(c.getSource(), at, rl.id(), false);
									CreateOreExcavation.LOGGER.info("Locating element " + rl.id() + " took " + stopwatch.elapsed().toMillis() + " ms");
									return i;
								} else {
									throw ERROR_VEIN_NOT_FOUND.create(rl.id().toString());
								}
							} else {
								throw ERROR_VEIN_NOT_FOUND.create(rl.id().toString());
							}
						})
						)
				);
		l.then(Commands.literal("discover").requires(s -> s.hasPermission(2)).
				then(Commands.literal("clear").
						executes(c -> clearDiscovered(c.getSource(), OreVeinDiscoverPacket.Kind.BOTH)).
						then(Commands.literal("vein").executes(c -> clearDiscovered(c.getSource(), OreVeinDiscoverPacket.Kind.VEIN))).
						then(Commands.literal("cluster").executes(c -> clearDiscovered(c.getSource(), OreVeinDiscoverPacket.Kind.CLUSTER)))).
				then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_DISCOVER_RADIUS)).
						executes(c -> discoverAround(c.getSource(), new ChunkPos(BlockPos.containing(c.getSource().getPosition())), IntegerArgumentType.getInteger(c, "radius"), OreVeinDiscoverPacket.Kind.BOTH))).
				then(Commands.argument("pos", BlockPosArgument.blockPos()).
						then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_DISCOVER_RADIUS)).
								executes(c -> discoverAround(c.getSource(), new ChunkPos(BlockPosArgument.getLoadedBlockPos(c, "pos")), IntegerArgumentType.getInteger(c, "radius"), OreVeinDiscoverPacket.Kind.BOTH)))).
				then(discoverKind("vein", OreVeinDiscoverPacket.Kind.VEIN)).
				then(discoverKind("cluster", OreVeinDiscoverPacket.Kind.CLUSTER))
		);
		LiteralArgumentBuilder<CommandSourceStack> regenerate = Commands.literal("regenerate").requires(s -> s.hasPermission(2));
		regenerate.then(clearBranch(null));
		regenerateTargets(regenerate, null, OreVeinDiscoverPacket.Kind.BOTH);
		regenerate.then(regenerateKind("vein", OreVeinDiscoverPacket.Kind.VEIN));
		regenerate.then(regenerateKind("cluster", OreVeinDiscoverPacket.Kind.CLUSTER));
		l.then(regenerate);
		evt.getDispatcher().register(l);
	}

	/**
	 * {@code /coe regenerate vein ...} only re-picks the veins, the plain form re-picks both kinds.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> regenerateKind(String name, OreVeinDiscoverPacket.Kind kind) {
		LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(name);
		regenerateTargets(node, null, kind);
		return node;
	}

	/**
	 * The ways of naming what to re-pick: everything, a radius, a spot and a radius, or another dimension.
	 *
	 * @param dimensionArg the dimension the branch works on, or {@code null} for the one the source is in
	 */
	private static void regenerateTargets(LiteralArgumentBuilder<CommandSourceStack> node, String dimensionArg, OreVeinDiscoverPacket.Kind kind) {
		node.then(withSeed(Commands.literal("all"), (c, seed) -> regenerateAll(c.getSource(), levelOf(c, dimensionArg), kind, seed)));
		node.then(withSeed(Commands.argument("radius", regenerateRadius()),
				(c, seed) -> regenerateArea(c.getSource(), levelOf(c, dimensionArg), new ChunkPos(BlockPos.containing(c.getSource().getPosition())), IntegerArgumentType.getInteger(c, "radius"), kind, seed)));
		node.then(Commands.argument("pos", BlockPosArgument.blockPos()).
				then(withSeed(Commands.argument("radius", regenerateRadius()),
						(c, seed) -> regenerateArea(c.getSource(), levelOf(c, dimensionArg), new ChunkPos(BlockPosArgument.getBlockPos(c, "pos")), IntegerArgumentType.getInteger(c, "radius"), kind, seed))));
		node.then(Commands.argument("dimension", DimensionArgument.dimension()).
				then(withSeed(Commands.literal("all"), (c, seed) -> regenerateAll(c.getSource(), DimensionArgument.getDimension(c, "dimension"), kind, seed))).
				then(clearBranch("dimension")).
				then(withSeed(Commands.argument("radius", regenerateRadius()),
						(c, seed) -> regenerateArea(c.getSource(), DimensionArgument.getDimension(c, "dimension"), new ChunkPos(BlockPos.containing(c.getSource().getPosition())), IntegerArgumentType.getInteger(c, "radius"), kind, seed))).
				then(Commands.argument("pos", BlockPosArgument.blockPos()).
						then(withSeed(Commands.argument("radius", regenerateRadius()),
								(c, seed) -> regenerateArea(c.getSource(), DimensionArgument.getDimension(c, "dimension"), new ChunkPos(BlockPosArgument.getBlockPos(c, "pos")), IntegerArgumentType.getInteger(c, "radius"), kind, seed)))));
	}

	private interface RegenerateAction {
		void run(CommandContext<CommandSourceStack> c, Long seed) throws CommandSyntaxException;
	}

	/**
	 * A target runs on its own, from the world seed, and takes an optional seed after it, so that
	 * {@code /coe regenerate cluster all 12345} re-picks the chunks from that seed instead.
	 */
	private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withSeed(T node, RegenerateAction action) {
		node.executes(c -> {
			action.run(c, null);
			return 1;
		});
		node.then(Commands.argument("seed", LongArgumentType.longArg()).executes(c -> {
			action.run(c, LongArgumentType.getLong(c, "seed"));
			return 1;
		}));
		return node;
	}

	/**
	 * The kind comes after {@code clear}, the same way {@code /coe discover clear vein} takes it.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> clearBranch(String dimensionArg) {
		return Commands.literal("clear").
				executes(c -> regenerateClear(c.getSource(), levelOf(c, dimensionArg), OreVeinDiscoverPacket.Kind.BOTH)).
				then(Commands.literal("vein").executes(c -> regenerateClear(c.getSource(), levelOf(c, dimensionArg), OreVeinDiscoverPacket.Kind.VEIN))).
				then(Commands.literal("cluster").executes(c -> regenerateClear(c.getSource(), levelOf(c, dimensionArg), OreVeinDiscoverPacket.Kind.CLUSTER)));
	}

	/**
	 * @param dimensionArg the name of the dimension argument of the branch, or {@code null} when the
	 *        command works on the dimension the source is in
	 */
	private static ServerLevel levelOf(CommandContext<CommandSourceStack> c, String dimensionArg) throws CommandSyntaxException {
		return dimensionArg == null ? c.getSource().getLevel() : DimensionArgument.getDimension(c, dimensionArg);
	}

	/**
	 * Scanning a chunk costs about a hundred random draws, keep the radius sane.
	 */
	public static final int MAX_DISCOVER_RADIUS = 128;

	/**
	 * A radius only records a marker, the chunks that are never loaded again are never re-picked, so a
	 * generous radius costs nothing but one box in the saved data.
	 */
	public static final int MAX_REGENERATE_RADIUS = 100000;

	private static IntegerArgumentType regenerateRadius() {
		return IntegerArgumentType.integer(0, MAX_REGENERATE_RADIUS);
	}

	private static Component dimensionName(ServerLevel level) {
		return Component.literal(level.dimension().location().toString());
	}

	private static int regenerateAll(CommandSourceStack css, ServerLevel level, OreVeinDiscoverPacket.Kind kind, Long seed) {
		long used = resolveSeed(level, seed);
		VeinRegeneration.get(level).markAll(kind, used);
		css.sendSuccess(() -> Component.translatable("command.coe.regenerate.all", dimensionName(level), kindName(kind)), true);
		css.sendSuccess(() -> Component.translatable("command.coe.regenerate.seed", used), false);
		css.sendSuccess(() -> Component.translatable("command.coe.regenerate.hint"), false);
		return 1;
	}

	private static int regenerateArea(CommandSourceStack css, ServerLevel level, ChunkPos center, int radius, OreVeinDiscoverPacket.Kind kind, Long seed) {
		long used = resolveSeed(level, seed);
		long count = VeinRegeneration.get(level).markArea(center, radius, kind, used);
		css.sendSuccess(() -> Component.translatable("command.coe.regenerate.area", count, dimensionName(level), kindName(kind)), true);
		css.sendSuccess(() -> Component.translatable("command.coe.regenerate.seed", used), false);
		css.sendSuccess(() -> Component.translatable("command.coe.regenerate.hint"), false);
		return 1;
	}

	/**
	 * @return the seed to re-pick from, the world seed when the command was run without one
	 */
	private static long resolveSeed(ServerLevel level, Long seed) {
		return seed != null ? seed : level.getSeed();
	}

	private static int regenerateClear(CommandSourceStack css, ServerLevel level, OreVeinDiscoverPacket.Kind kind) {
		VeinRegeneration.get(level).clear(kind);
		css.sendSuccess(() -> Component.translatable("command.coe.regenerate.clear", kindName(kind), dimensionName(level)), true);
		return 1;
	}

	private static Component kindName(OreVeinDiscoverPacket.Kind kind) {
		return Component.translatable("command.coe.regenerate.kind." + kind.name().toLowerCase(Locale.ROOT));
	}

	/**
	 * One vein and one cluster can share a chunk, so the kind can be given to look at only one of them.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> discoverKind(String name, OreVeinDiscoverPacket.Kind kind) {
		return Commands.literal(name).
				then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_DISCOVER_RADIUS)).
						executes(c -> discoverAround(c.getSource(), new ChunkPos(BlockPos.containing(c.getSource().getPosition())), IntegerArgumentType.getInteger(c, "radius"), kind))).
				then(Commands.argument("pos", BlockPosArgument.blockPos()).
						then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_DISCOVER_RADIUS)).
								executes(c -> discoverAround(c.getSource(), new ChunkPos(BlockPosArgument.getLoadedBlockPos(c, "pos")), IntegerArgumentType.getInteger(c, "radius"), kind))));
	}

	private static void setVein(CommandSourceStack css, BlockPos pos, ResourceLocation id, VeinRecipe recipe, float mul) {
		ChunkPos p = new ChunkPos(pos);
		var chunk = css.getLevel().getChunk(p.x, p.z);
		OreData data = OreDataAttachment.getData(chunk);
		ServerLevel level = css.getLevel();
		if (recipe == null) {
			data.clear();
		} else {
			Holder<Biome> biome = RandomSpreadGenerator.sampleBiome(level, p, VeinRegeneration.seedFor(level, OreVeinDiscoverPacket.Kind.VEIN));
			data.setVein(id, recipe, biome, level.registryAccess(), RandomSource.create(), mul, level.getGameTime());
		}
		// a hand placed vein is current, only a marker written after this command picks the chunk again
		data.setGen(VeinRegeneration.epochFor(level, p, OreVeinDiscoverPacket.Kind.VEIN));
		data.setClusterGen(VeinRegeneration.epochFor(level, p, OreVeinDiscoverPacket.Kind.CLUSTER));
		data.setLoaded(true);
		chunk.setUnsaved(true);
	}

	private static int discoverAround(CommandSourceStack css, ChunkPos center, int radius, OreVeinDiscoverPacket.Kind kind) {
		ServerLevel level = css.getLevel();
		RandomSpreadGenerator picker = OreVeinGenerator.getPicker(level);
		long veinSeed = VeinRegeneration.seedFor(level, OreVeinDiscoverPacket.Kind.VEIN);
		long clusterSeed = VeinRegeneration.seedFor(level, OreVeinDiscoverPacket.Kind.CLUSTER);
		int y = Mth.floor(css.getPosition().y);
		List<OreVeinDiscoverPacket.Entry> found = new ArrayList<>();
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				ChunkPos cp = new ChunkPos(center.x + x, center.z + z);
				if (kind.veins())addFound(picker, level, cp, false, found, y, veinSeed);
				if (kind.clusters())addFound(picker, level, cp, true, found, y, clusterSeed);
			}
		}
		ServerPlayer player = css.getPlayer();
		if (player != null)NetworkHandler.sendTo(player, new OreVeinDiscoverPacket(level.dimension(), found, OreVeinDiscoverPacket.Mode.ADD, kind));
		switch (kind) {
		case VEIN:
			css.sendSuccess(() -> Component.translatable("command.coe.discover.success.vein", found.size(), radius), false);
			break;
		case CLUSTER:
			css.sendSuccess(() -> Component.translatable("command.coe.discover.success.cluster", found.size(), radius), false);
			break;
		default: {
			long clusters = found.stream().filter(OreVeinDiscoverPacket.Entry::small).count();
			css.sendSuccess(() -> Component.translatable("command.coe.discover.success", found.size(), radius, clusters), false);
			break;
		}
		}
		return found.size();
	}

	private static void addFound(RandomSpreadGenerator picker, ServerLevel level, ChunkPos cp, boolean small, List<OreVeinDiscoverPacket.Entry> out, int y, long seed) {
		RandomSpreadGenerator.PickResult pick = picker.pick(level, cp, small, seed);
		if (pick == null)return;
		out.add(new OreVeinDiscoverPacket.Entry(cp.getMiddleBlockPosition(y), pick.recipe().id(), small));
	}

	private static int clearDiscovered(CommandSourceStack css, OreVeinDiscoverPacket.Kind kind) {
		ServerPlayer player = css.getPlayer();
		if (player != null)NetworkHandler.sendTo(player, new OreVeinDiscoverPacket(css.getLevel().dimension(), List.of(), OreVeinDiscoverPacket.Mode.CLEAR, kind));
		return 1;
	}

	private static void markDiscovered(CommandSourceStack css, BlockPos pos, ResourceLocation id, boolean small) {
		ServerPlayer player = css.getPlayer();
		if (player == null)return;
		BlockPos at = new BlockPos(pos.getX(), Mth.floor(css.getPosition().y), pos.getZ());
		NetworkHandler.sendTo(player, new OreVeinDiscoverPacket(css.getLevel().dimension(), List.of(new OreVeinDiscoverPacket.Entry(at, id, small)), OreVeinDiscoverPacket.Mode.ADD));
	}

	public static final SuggestionProvider<CommandSourceStack> ALL_RECIPES = SuggestionProviders.register(ResourceLocation.tryBuild(CreateOreExcavation.MODID, "all_recipes"), (ctx, builder) -> {
		Stream<ResourceLocation> rl;
		RecipeManager rm;
		if(ctx.getSource() instanceof ClientCommandSourceStack || ctx.getSource() instanceof ClientSuggestionProvider) {
			rm = Minecraft.getInstance().getConnection().getRecipeManager();
		} else if(ctx.getSource() instanceof CommandSourceStack css) {
			rm = css.getServer().getRecipeManager();
		} else {
			rm = null;
		}
		if(rm != null) {
			rl = rm.getAllRecipesFor(CreateOreExcavation.VEIN_RECIPES.getRecipeType()).stream().map(RecipeHolder::id);
		} else rl = Stream.empty();

		return SharedSuggestionProvider.suggestResource(rl, builder);
	});
}
