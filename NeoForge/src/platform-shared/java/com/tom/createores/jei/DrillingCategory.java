package com.tom.createores.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.createmod.catnip.layout.LayoutHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import com.simibubi.create.compat.jei.DoubleItemIcon;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.Registration;
import com.tom.createores.recipe.DrillingRecipe;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;

public class DrillingCategory extends ExcavatingCategory<DrillingRecipe> {
	/** How many ore stacks one row of the result list holds. */
	protected static final int RESULTS_PER_ROW = 7;
	/** One slot plus the gap to the next one. */
	protected static final int SLOT = 19;
	/** A short list sits at the bottom of the page, several rows start right below the drill. */
	protected static final int RESULTS_BOTTOM = 86;
	protected static final int RESULTS_TOP = 64;

	private int height = -1;

	public DrillingCategory() {
		block = new AnimatedBlock(Registration.DRILL_BLOCK.getDefaultState(), 11);
		icon = new DoubleItemIcon(Registration.DRILL_BLOCK::asStack, () -> new ItemStack(Registration.NORMAL_DRILL_ITEM.get()));
	}

	@Override
	public int getHeight() {
		if (height < 0)height = heightFor(clusters());
		return height;
	}

	/**
	 * A page has to hold the longest result list among the recipes it shows.
	 */
	protected int heightFor(boolean clusters) {
		int longest = 0;
		for (RecipeHolder<DrillingRecipe> recipe : drillingRecipes()) {
			if (JEIHandler.isCluster(recipe.value()) != clusters)continue;
			longest = Math.max(longest, recipe.value().getOutput().size());
		}
		return Math.max(super.getHeight(), RESULTS_TOP + rows(longest) * SLOT + 2);
	}

	/** Ore clusters are mined by hand and get their own pages. */
	protected boolean clusters() {
		return false;
	}

	protected static List<RecipeHolder<DrillingRecipe>> drillingRecipes() {
		RecipeManager mngr = Minecraft.getInstance().getConnection().getRecipeManager();
		return mngr.getAllRecipesFor(CreateOreExcavation.DRILLING_RECIPES.getRecipeType());
	}

	protected static int rows(int results) {
		return Math.max(1, (results + RESULTS_PER_ROW - 1) / RESULTS_PER_ROW);
	}

	@Override
	public Component getTitle() {
		return Component.translatable("jei.coe.recipe.drilling");
	}

	@Override
	public RecipeType<RecipeHolder<DrillingRecipe>> getRecipeType() {
		return JEIRecipes.DRILLING;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<DrillingRecipe> recipe, IFocusGroup focuses) {
		super.setRecipe(builder, recipe, focuses);
		addResults(builder, recipe.value());
	}

	/**
	 * The most likely drops come first, so the list can be read without opening every tooltip.
	 */
	protected void addResults(IRecipeLayoutBuilder builder, DrillingRecipe recipe) {
		List<ProcessingOutput> results = new ArrayList<>(recipe.getOutput());
		results.sort(Comparator.comparingDouble(ProcessingOutput::getChance).reversed());
		int rowCount = rows(results.size());
		int yOffset = rowCount > 1 ? RESULTS_TOP + (rowCount * SLOT - 1) / 2 : RESULTS_BOTTOM;
		int xOffset = getWidth() / 2;

		LayoutHelper layout = LayoutHelper.centeredHorizontal(results.size(), rowCount, 18, 18, 1);
		for (ProcessingOutput result : results) {
			builder.addSlot(RecipeIngredientRole.OUTPUT, xOffset + layout.getX() + 1, yOffset + layout.getY() + 1)
					.setBackground(CreateRecipeCategory.getRenderedSlot(result), -1, -1)
					.addItemStack(result.getStack())
					.addRichTooltipCallback(CreateRecipeCategory.addStochasticTooltip(result));
			layout.next();
		}
	}
}
