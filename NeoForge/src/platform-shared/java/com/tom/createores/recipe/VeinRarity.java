package com.tom.createores.recipe;

import net.minecraft.util.StringRepresentable;

import com.mojang.serialization.Codec;

/**
 * Whether a vein holds a common or a rare mineral. Only a label, shown in JEI and used as the
 * fallback colour of a map waypoint when the recipe does not name one.
 */
public enum VeinRarity implements StringRepresentable {
	COMMON("common"),
	RARE("rare")
	;

	public static final Codec<VeinRarity> CODEC = StringRepresentable.fromEnum(VeinRarity::values);

	private final String name;

	private VeinRarity(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
