package com.tom.createores.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import com.tom.createores.recipe.VeinRecipe;
import com.tom.createores.util.NumberFormatter;

/**
 * The lines describing one distribution of a vein. Shared by the ingredient tooltip (shown when
 * hovering the vein slot) and the recipe page, because JEI only ever displays one of the two.
 */
public class VeinInfoUtil {
	private static final Map<String, List<ResourceLocation>> BIOMES = new HashMap<>();

	private VeinInfoUtil() {
	}

	/**
	 * @return the page title, left uncoloured so it reads as the tooltip name. The default distribution
	 *         goes by the plain vein name, every other one names the biomes it covers.
	 */
	public static Component title(VeinRecipe vein, int entry) {
		if (entry <= 0)return vein.getName();
		return Component.translatable("vein.coe.name.biome", vein.getName(), vein.entryName(entry));
	}

	/**
	 * @return the detail lines, greyed like the body of a normal tooltip
	 */
	public static List<Component> details(VeinRecipe vein, int entry) {
		List<Component> tooltip = new ArrayList<>();
		tooltip.add(line("tooltip.coe.rarity." + vein.getRarity().getSerializedName()));
		VeinRecipe.Layer layer = vein.layerForEntry(entry);
		if (!vein.isInfiniteClient() && layer.reserve() != null) {
			tooltip.add(line("tooltip.coe.reserve",
					NumberFormatter.formatNumber(Math.round(layer.reserve().mean())),
					NumberFormatter.formatNumber(layer.reserve().min()),
					NumberFormatter.formatNumber(layer.reserve().max())));
		}
		if (layer.spacing() > 0)tooltip.add(line("jei.coe.vein_density", layer.spacing()));
		if (!vein.getChunks().isEmpty())tooltip.add(line("jei.coe.vein_fixed_chunks"));
		tooltip.add(vein.getRegenDescription().withStyle(ChatFormatting.GRAY));
		return tooltip;
	}

	/**
	 * The lists are cached because they are rebuilt for every frame the tooltip is open.
	 */
	public static void clearCache() {
		BIOMES.clear();
	}

	/**
	 * @return the biomes the distribution takes effect in, or the ones it leaves out
	 */
	public static List<ResourceLocation> biomes(Vein vein, boolean generating) {
		return BIOMES.computeIfAbsent(vein.id() + "#" + vein.entry() + (generating ? "/in" : "/out"),
				k -> findBiomes(vein, generating));
	}

	private static List<ResourceLocation> findBiomes(Vein vein, boolean generating) {
		RegistryAccess registries = registries();
		if (registries == null)return List.of();
		Registry<Biome> biomeRegistry = registries.registryOrThrow(Registries.BIOME);
		List<ResourceLocation> out = new ArrayList<>();
		for (ResourceLocation id : biomeRegistry.keySet()) {
			Holder.Reference<Biome> holder = biomeRegistry.getHolder(id).orElse(null);
			if (holder != null && vein.recipe0().entryApplies(holder, vein.entry(), registries) == generating)out.add(id);
		}
		out.sort(Comparator.naturalOrder());
		return out;
	}

	private static RegistryAccess registries() {
		var connection = Minecraft.getInstance().getConnection();
		return connection != null ? connection.registryAccess() : null;
	}

	private static Component line(String key, Object... args) {
		return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
	}
}
