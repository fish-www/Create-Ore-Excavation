package com.tom.createores.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.Registration;
import com.tom.createores.recipe.DrillingRecipe;
import com.tom.createores.recipe.ExcavatingRecipe;
import com.tom.createores.recipe.VeinRecipe;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

@JeiPlugin
public class JEIHandler implements IModPlugin {

	@Override
	public ResourceLocation getPluginUid() {
		return ResourceLocation.tryBuild(CreateOreExcavation.MODID, "jei");
	}

	@Override
	public void registerCategories(IRecipeCategoryRegistration registration) {
		registration.addRecipeCategories(new DrillingCategory(), new HandheldDrillingCategory(), new ExtractingCategory(), new VeinCategory());
	}

	@Override
	public void registerRecipes(IRecipeRegistration registration) {
		RecipeManager mngr = Minecraft.getInstance().getConnection().getRecipeManager();
		List<RecipeHolder<DrillingRecipe>> drilling = mngr.getAllRecipesFor(CreateOreExcavation.DRILLING_RECIPES.getRecipeType());
		registration.addRecipes(JEIRecipes.DRILLING, drilling.stream().filter(r -> !isCluster(r.value())).toList());
		registration.addRecipes(JEIRecipes.HANDHELD_DRILLING, drilling.stream().filter(r -> isCluster(r.value())).toList());
		registration.addRecipes(JEIRecipes.EXTRACTING, mngr.getAllRecipesFor(CreateOreExcavation.EXTRACTING_RECIPES.getRecipeType()));
		registration.addRecipes(JEIRecipes.VEINS, buildVeinDisplays(mngr));
	}

	/**
	 * Ore clusters are mined by hand, every other vein by a machine.
	 */
	public static boolean isCluster(ExcavatingRecipe recipe) {
		RecipeManager mngr = Minecraft.getInstance().getConnection().getRecipeManager();
		return mngr.byKey(recipe.veinId).map(rec -> rec.value() instanceof VeinRecipe vein && vein.isCluster()).orElse(false);
	}

	/**
	 * One page per distribution of a vein. The list is ordered by mineral so that the veins of a single
	 * mineral end up next to each other, the default distribution first.
	 */
	public static List<VeinDisplay> buildVeinDisplays(RecipeManager mngr) {
		VeinInfoUtil.clearCache();
		List<RecipeHolder<VeinRecipe>> recipes = new ArrayList<>(mngr.getAllRecipesFor(CreateOreExcavation.VEIN_RECIPES.getRecipeType()));
		recipes.sort(Comparator.<RecipeHolder<VeinRecipe>, String>comparing(h -> basePath(h.id()))
				.thenComparing(h -> h.value().isCluster())
				.thenComparing(RecipeHolder::id));
		List<VeinDisplay> out = new ArrayList<>();
		int cluster = 0;
		for (RecipeHolder<VeinRecipe> holder : recipes) {
			if (holder.value().isCluster())cluster++;
			for (int entry = 0; entry < holder.value().entryCount(); entry++) {
				// a distribution that generates nowhere has no page
				if (!holder.value().entryGenerates(entry))continue;
				out.add(new VeinDisplay(holder, entry));
			}
		}
		CreateOreExcavation.LOGGER.info("[JEI] {} ore types ({} veins, {} clusters) -> {} distributions",
				recipes.size(), recipes.size() - cluster, cluster, out.size());
		return out;
	}

	private static String basePath(ResourceLocation id) {
		String path = id.getPath();
		if (path.endsWith("_cluster"))path = path.substring(0, path.length() - "_cluster".length());
		return id.getNamespace() + ":" + path;
	}

	@Override
	public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
		registration.addRecipeCatalyst(Registration.DRILL_BLOCK.asStack(), JEIRecipes.DRILLING);
		registration.addRecipeCatalyst(Registration.EXTRACTOR_BLOCK.asStack(), JEIRecipes.EXTRACTING);
		registration.addRecipeCatalyst(Registration.VEIN_FINDER_ITEM.asStack(), JEIRecipes.VEINS);
		registration.addRecipeCatalyst(Registration.HANDHELD_DRILL_ITEM.asStack(), JEIRecipes.VEINS);
	}

	@Override
	public void registerIngredients(IModIngredientRegistration registration) {
		new VeinIngredient(registration);
	}
}
