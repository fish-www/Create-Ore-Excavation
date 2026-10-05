package com.tom.createores.jei;

import net.minecraft.world.item.crafting.RecipeHolder;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.recipe.DrillingRecipe;
import com.tom.createores.recipe.ExtractorRecipe;

import mezz.jei.api.recipe.RecipeType;

public class JEIRecipes {
	public static final RecipeType<RecipeHolder<DrillingRecipe>> DRILLING = RecipeType.createFromVanilla(CreateOreExcavation.DRILLING_RECIPES.getRecipeType());
	/** Ore clusters are mined by hand, so they get their own pages next to the machine drilling. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static final RecipeType<RecipeHolder<DrillingRecipe>> HANDHELD_DRILLING = RecipeType.create(CreateOreExcavation.MODID, "handheld_drilling", (Class) RecipeHolder.class);
	public static final RecipeType<RecipeHolder<ExtractorRecipe>> EXTRACTING = RecipeType.createFromVanilla(CreateOreExcavation.EXTRACTING_RECIPES.getRecipeType());
	public static final RecipeType<VeinDisplay> VEINS = RecipeType.create(CreateOreExcavation.MODID, "veins", VeinDisplay.class);
}
