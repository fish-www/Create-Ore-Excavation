package com.tom.createores.jei;

import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.simibubi.create.compat.jei.ItemIcon;

import com.tom.createores.Config;
import com.tom.createores.Registration;
import com.tom.createores.recipe.DrillingRecipe;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;

/**
 * Mining an ore cluster is done by hand, so those pages have no machine, no drill head and no drilling
 * fluid, just the vein, the result list and the drill the player has to hold.
 */
public class HandheldDrillingCategory extends DrillingCategory {

	public HandheldDrillingCategory() {
		icon = new ItemIcon(Registration.HANDHELD_DRILL_ITEM::asStack);
	}

	@Override
	protected boolean clusters() {
		return true;
	}

	@Override
	public Component getTitle() {
		return Component.translatable("jei.coe.recipe.handheld_drilling");
	}

	@Override
	public RecipeType<RecipeHolder<DrillingRecipe>> getRecipeType() {
		return JEIRecipes.HANDHELD_DRILLING;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<DrillingRecipe> recipe, IFocusGroup focuses) {
		addVeinSlot(builder, recipe.value());
		addResults(builder, recipe.value());
	}

	@Override
	public void draw(RecipeHolder<DrillingRecipe> recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics stack,
			double mouseX, double mouseY) {
		stack.pose().pushPose();
		stack.pose().translate(48, 36, 100);
		stack.pose().scale(1.5F, 1.5F, 1.5F);
		GuiGameElement.of(new ItemStack(Registration.HANDHELD_DRILL_ITEM.get())).render(stack);
		stack.pose().popPose();
	}

	@Override
	public void getTooltip(ITooltipBuilder tooltip, RecipeHolder<DrillingRecipe> recipe, IRecipeSlotsView recipeSlotsView,
			double mouseX, double mouseY) {
		if (mouseX > 40 && mouseX < 80 && mouseY > 25 && mouseY < 60) {
			tooltip.add(Component.translatable("tooltip.coe.processTime", Config.handDrillTicks));
			tooltip.add(Component.translatable("tooltip.coe.handDrill.fuelCost", Config.handDrillFuelPerUnit));
		}
	}
}
