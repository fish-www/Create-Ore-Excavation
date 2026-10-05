package com.tom.createores.biome;

import net.minecraft.util.RandomSource;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How much ore a vein holds. The amount is drawn from a normal distribution around {@code mean}
 * and cut off at {@code min} and {@code max}, so most veins end up near {@code mean} while the
 * extremes stay inside the declared range.
 */
public record ReserveRange(long min, long max, double mean, double sigma) {
	public static final Codec<ReserveRange> CODEC = RecordCodecBuilder.create(b -> {
		return b.group(
				Codec.LONG.fieldOf("min").forGetter(ReserveRange::min),
				Codec.LONG.fieldOf("max").forGetter(ReserveRange::max),
				Codec.DOUBLE.fieldOf("mean").forGetter(ReserveRange::mean),
				Codec.DOUBLE.optionalFieldOf("sigma", 0D).forGetter(ReserveRange::sigma)
				).apply(b, ReserveRange::new);
	});

	public long sample(RandomSource rng) {
		double v = sigma > 0 ? mean + rng.nextGaussian() * sigma : mean;
		return Math.max(min, Math.min(max, Math.round(v)));
	}
}
