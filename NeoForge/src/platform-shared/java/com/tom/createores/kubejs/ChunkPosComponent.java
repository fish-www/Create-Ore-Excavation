package com.tom.createores.kubejs;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

import com.mojang.serialization.Codec;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.recipe.VeinRecipe;

import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.rhino.type.TypeInfo;

/**
 * One pinned chunk, written as {@code [x, z]} chunk coordinates. Reachable from scripts one chunk at a
 * time with {@code .chunk(x, z)}, the list of them is the {@code chunks} key of the vein schema.
 */
public enum ChunkPosComponent implements RecipeComponent<ChunkPos> {
	INSTANCE;

	public static final TypeInfo TYPE_INFO = TypeInfo.of(ChunkPos.class);
	public static final RecipeComponentType<?> TYPE = RecipeComponentType.unit(ResourceLocation.tryBuild(CreateOreExcavation.MODID, "chunk_pos"), INSTANCE);

	@Override
	public Codec<ChunkPos> codec() {
		return VeinRecipe.CHUNK_CODEC;
	}

	@Override
	public TypeInfo typeInfo() {
		return TYPE_INFO;
	}

	@Override
	public String toString() {
		return "coe:chunk_pos";
	}

	@Override
	public RecipeComponentType<?> type() {
		return TYPE;
	}
}
