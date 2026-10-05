package com.tom.createores.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.gui.builder.ITooltipBuilder;

/**
 * Lists a set of biomes, chunk positions or similar in a tooltip, paging through it when there are more
 * than fit.
 */
public class BiomeTooltip {
	/** How many biomes one page of the list holds. */
	private static final int PAGE_SIZE = 16;
	private static long lastBiomeChangeTime;
	private static int biomePage;

	public static void listBiomes(Component header, List<ResourceLocation> ids, ITooltipBuilder tooltip) {
		list(header, ids.stream().map(BiomeTooltip::name).collect(Collectors.toList()), tooltip);
	}

	/**
	 * Lists ready made lines, paged the same way the biomes are.
	 */
	public static void list(Component header, List<Component> lines, ITooltipBuilder tooltip) {
		tooltip.add(header);
		show(lines, tooltip);
	}
	private static void show(List<Component> lines, ITooltipBuilder tooltip) {
		boolean isShift = Screen.hasShiftDown();
		int size = lines.size();
		Component pg = null;
		List<Component> comps;
		if (size > PAGE_SIZE) {
			if (!isShift && System.currentTimeMillis() - lastBiomeChangeTime > 2000) {
				biomePage++;
				if (biomePage * PAGE_SIZE >= size)biomePage = 0;
				lastBiomeChangeTime = System.currentTimeMillis();
			}
			pg = Component.translatable("tooltip.coe.page", biomePage + 1, (size / PAGE_SIZE) + 1);
			comps = lines.stream().skip(biomePage * PAGE_SIZE).limit(PAGE_SIZE).collect(Collectors.toList());
		} else {
			comps = new ArrayList<>(lines);
		}
		if (pg != null)comps.add(pg);
		tooltip.addAll(comps);
	}

	private static Component name(ResourceLocation biome) {
		return Component.translatable("biome." + biome.getNamespace() + "." + biome.getPath());
	}

	public static void resetPage() {
		biomePage = 0;
		lastBiomeChangeTime = 0;
	}
}
