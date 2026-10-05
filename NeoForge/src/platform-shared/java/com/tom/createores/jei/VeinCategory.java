package com.tom.createores.jei;

import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;

import com.simibubi.create.compat.jei.DoubleItemIcon;

import com.tom.createores.Registration;
import com.tom.createores.util.BiomeTooltip;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;

public class VeinCategory implements IRecipeCategory<VeinDisplay> {
	protected IDrawable icon;
	protected IDrawable biomeWIcon, biomeBIcon, chunkIcon;

	public VeinCategory() {
		icon = new ItemIcon(() -> new ItemStack(Registration.NORMAL_DRILL_ITEM.get()));
		biomeWIcon = new ItemIcon(() -> new ItemStack(Items.OAK_SAPLING));
		biomeBIcon = new DoubleItemIcon(() -> new ItemStack(Items.OAK_SAPLING), () -> new ItemStack(Items.BARRIER));
		chunkIcon = new ItemIcon(() -> new ItemStack(Items.FILLED_MAP));
	}

	@Override
	public int getWidth() {
		return 177;
	}

	@Override
	public int getHeight() {
		return 100;
	}

	@Override
	public IDrawable getIcon() {
		return icon;
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, VeinDisplay recipe, IFocusGroup focuses) {
		builder
		.addSlot(RecipeIngredientRole.OUTPUT, 50, 25)
		.addIngredient(VeinIngredient.VEIN, new Vein(recipe.recipe(), recipe.entry()));
	}

	@Override
	public void draw(VeinDisplay recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics stack, double mouseX,
			double mouseY) {
		if (showsBiomes(recipe)) {
			biomeWIcon.draw(stack, 100, 5);
			biomeBIcon.draw(stack, 100, 25);
		}
		if (!recipe.recipe().value().getChunks().isEmpty())chunkIcon.draw(stack, 100, chunkIconY(recipe));
	}

	/**
	 * A distribution pinned to chunks ignores the biome filters, so it lists its chunks in their place.
	 */
	private static boolean showsBiomes(VeinDisplay recipe) {
		return recipe.recipe().value().layerForEntry(recipe.entry()).spacing() > 0;
	}

	private static int chunkIconY(VeinDisplay recipe) {
		return showsBiomes(recipe) ? 45 : 5;
	}

	/**
	 * The icons list the biomes or the chunks of this distribution, and only do so while the mouse is
	 * over them, so the rest of the page stays clean.
	 */
	@Override
	public void getTooltip(ITooltipBuilder tooltip, VeinDisplay recipe, IRecipeSlotsView recipeSlotsView,
			double mouseX, double mouseY) {
		Vein vein = new Vein(recipe.recipe(), recipe.entry());
		List<ChunkPos> chunks = recipe.recipe().value().getChunks();
		if (showsBiomes(recipe) && overIcon(mouseX, mouseY, 5)) {
			BiomeTooltip.listBiomes(Component.translatable("tooltip.coe.biome.whitelist"),
					VeinInfoUtil.biomes(vein, true), tooltip);
		} else if (showsBiomes(recipe) && overIcon(mouseX, mouseY, 25)) {
			BiomeTooltip.listBiomes(Component.translatable("tooltip.coe.biome.blacklist"),
					VeinInfoUtil.biomes(vein, false), tooltip);
		} else if (!chunks.isEmpty() && overIcon(mouseX, mouseY, chunkIconY(recipe))) {
			BiomeTooltip.list(Component.translatable("jei.coe.vein_fixed_chunks"),
					chunks.stream().map(VeinCategory::chunkLine).collect(Collectors.toList()), tooltip);
		} else {
			BiomeTooltip.resetPage();
		}
	}

	private static Component chunkLine(ChunkPos pos) {
		return Component.literal("[" + pos.x + ", " + pos.z + "]").withStyle(ChatFormatting.GRAY);
	}

	private static boolean overIcon(double mouseX, double mouseY, int y) {
		return mouseX >= 100 && mouseX < 118 && mouseY >= y && mouseY < y + 18;
	}

	@Override
	public RecipeType<VeinDisplay> getRecipeType() {
		return JEIRecipes.VEINS;
	}

	@Override
	public ResourceLocation getRegistryName(VeinDisplay recipe) {
		return recipe.id();
	}

	@Override
	public Component getTitle() {
		return Component.translatable("jei.coe.recipe.veins");
	}
}
