package com.tom.createores.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.tom.createores.recipe.VeinRecipe;

/**
 * One guide ingredient: a vein recipe with the distribution it stands for, entry 0 being the default
 * distribution of the vein and entry {@code i} the one of {@code biomeOverrides[i - 1]}.
 */
public record Vein(RecipeHolder<VeinRecipe> recipe, int entry) {

	public Vein(RecipeHolder<VeinRecipe> recipe) {
		this(recipe, 0);
	}

	public ResourceLocation id() {
		return recipe().id();
	}

	public VeinRecipe recipe0() {
		return recipe().value();
	}

	public static final Codec<RecipeHolder<VeinRecipe>> HOLDER_CODEC = RecordCodecBuilder.<RecipeHolder<VeinRecipe>>mapCodec(b -> {
		return b.group(
				ResourceLocation.CODEC.fieldOf("id").forGetter(RecipeHolder::id),
				VeinRecipe.Serializer.CODEC.fieldOf("value").forGetter(RecipeHolder::value)
				).apply(b, RecipeHolder::new);
	}).codec();

	public static final Codec<Vein> CODEC = RecordCodecBuilder.<Vein>mapCodec(b -> {
		return b.group(
				HOLDER_CODEC.fieldOf("recipe").forGetter(Vein::recipe),
				Codec.INT.optionalFieldOf("entry", 0).forGetter(Vein::entry)
				).apply(b, Vein::new);
	}).codec();
}
