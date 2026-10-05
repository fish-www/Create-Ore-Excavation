package com.tom.createores.jei;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeManager;

import com.mojang.blaze3d.systems.RenderSystem;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.Registration;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IModIngredientRegistration;

public class VeinIngredient implements IIngredientHelper<Vein>, IIngredientRenderer<Vein> {
	public static final IIngredientType<Vein> VEIN = () -> Vein.class;
	private ItemStack drill, handDrill;

	public VeinIngredient(IModIngredientRegistration registration) {
		RecipeManager mngr = Minecraft.getInstance().getConnection().getRecipeManager();
		// One entry per distribution of a vein, exactly like the recipe pages
		List<Vein> entries = JEIHandler.buildVeinDisplays(mngr).stream().map(d -> new Vein(d.recipe(), d.entry())).toList();
		registration.register(VEIN, entries, this, this, Vein.CODEC);
		drill = new ItemStack(Registration.NORMAL_DRILL_ITEM.get());
		handDrill = new ItemStack(Registration.HANDHELD_DRILL_ITEM.get());
		CreateOreExcavation.LOGGER.info("[JEI] {} vein ingredients, first: {}", entries.size(), entries.isEmpty() ? "-" : getDisplayName(entries.get(0)));
	}

	@Override
	public void render(GuiGraphics guiGraphics, Vein ingredient) {
		RenderSystem.enableDepthTest();
		guiGraphics.pose().pushPose();

		GuiGameElement.of(ingredient.recipe0().icon)
		.render(guiGraphics);

		guiGraphics.pose().pushPose();
		float s = 0.5f;
		guiGraphics.pose().translate(8, 8, 100);
		guiGraphics.pose().scale(s, s, s);
		GuiGameElement.of(ingredient.recipe0().isCluster() ? handDrill : drill)
		.render(guiGraphics);
		guiGraphics.pose().popPose();

		guiGraphics.pose().popPose();
	}

	@Override
	@Deprecated
	public List<Component> getTooltip(Vein ingredient, TooltipFlag tooltipFlag) {
		return buildTooltip(ingredient);
	}

	@Override
	public void getTooltip(ITooltipBuilder tooltip, Vein ingredient, TooltipFlag tooltipFlag) {
		tooltip.addAll(buildTooltip(ingredient));
	}

	private static List<Component> buildTooltip(Vein ingredient) {
		List<Component> tooltip = new ArrayList<>();
		tooltip.add(VeinInfoUtil.title(ingredient.recipe0(), ingredient.entry()));
		tooltip.addAll(VeinInfoUtil.details(ingredient.recipe0(), ingredient.entry()));
		return tooltip;
	}

	@Override
	public IIngredientType<Vein> getIngredientType() {
		return VEIN;
	}

	@Override
	public String getDisplayName(Vein ingredient) {
		return VeinInfoUtil.title(ingredient.recipe0(), ingredient.entry()).getString();
	}

	@Override
	public String getDisplayModId(Vein ingredient) {
		return CreateOreExcavation.MODID;
	}

	@Override
	public String getUid(Vein ingredient, UidContext context) {
		return ingredient.id().toString() + "/" + ingredient.entry();
	}

	@Override
	@Deprecated
	public String getUniqueId(Vein ingredient, UidContext context) {
		return getUid(ingredient, context);
	}

	@Override
	public ResourceLocation getResourceLocation(Vein ingredient) {
		return ingredient.id();
	}

	@Override
	public Vein copyIngredient(Vein ingredient) {
		return ingredient;
	}

	@Override
	public String getErrorInfo(@Nullable Vein ingredient) {
		return ingredient != null && ingredient.id() != null ? ingredient.id().toString() : "null";
	}
}
