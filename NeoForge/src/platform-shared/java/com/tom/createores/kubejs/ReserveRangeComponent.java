package com.tom.createores.kubejs;

import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.biome.ReserveRange;

import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.rhino.type.TypeInfo;

public enum ReserveRangeComponent implements RecipeComponent<ReserveRange> {
	INSTANCE;

	public static final TypeInfo TYPE_INFO = TypeInfo.of(ReserveRange.class);
	public static final RecipeComponentType<?> TYPE = RecipeComponentType.unit(ResourceLocation.tryBuild(CreateOreExcavation.MODID, "reserve_range"), INSTANCE);

	@Override
	public Codec<ReserveRange> codec() {
		return ReserveRange.CODEC;
	}

	@Override
	public TypeInfo typeInfo() {
		return TYPE_INFO;
	}

	@Override
	public String toString() {
		return "coe:reserve_range";
	}

	@Override
	public RecipeComponentType<?> type() {
		return TYPE;
	}
}
