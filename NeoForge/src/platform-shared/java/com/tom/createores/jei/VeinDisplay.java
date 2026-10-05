package com.tom.createores.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.tom.createores.recipe.VeinRecipe;

/**
 * One guide page per distribution of a vein, so a vein gets a page for its default distribution and one
 * for every biome entry that adds another one.
 */
public record VeinDisplay(RecipeHolder<VeinRecipe> recipe, int entry) {

	public ResourceLocation id() {
		ResourceLocation rl = recipe.id();
		return ResourceLocation.tryBuild(rl.getNamespace(), rl.getPath() + "/" + entry);
	}
}
