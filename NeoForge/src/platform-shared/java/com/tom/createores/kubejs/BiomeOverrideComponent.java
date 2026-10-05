package com.tom.createores.kubejs;

import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.recipe.VeinRecipe;

import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.rhino.type.TypeInfo;

public enum BiomeOverrideComponent implements RecipeComponent<VeinRecipe.BiomeOverride> {
	INSTANCE;

	public static final TypeInfo TYPE_INFO = TypeInfo.of(VeinRecipe.BiomeOverride.class);
	public static final RecipeComponentType<?> TYPE = RecipeComponentType.unit(ResourceLocation.tryBuild(CreateOreExcavation.MODID, "biome_override"), INSTANCE);

	@Override
	public Codec<VeinRecipe.BiomeOverride> codec() {
		return VeinRecipe.BiomeOverride.CODEC;
	}

	@Override
	public TypeInfo typeInfo() {
		return TYPE_INFO;
	}

	@Override
	public String toString() {
		return "coe:biome_override";
	}

	@Override
	public RecipeComponentType<?> type() {
		return TYPE;
	}
}
