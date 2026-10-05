package com.tom.createores;

import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;

import com.tom.createores.util.RandomSpreadGenerator;

public class OreVeinGenerator {
	private static AtomicReference<RandomSpreadGenerator> picker = new AtomicReference<>();

	public static void invalidate() {
		picker.set(null);
	}

	public static RandomSpreadGenerator getPicker(ServerLevel chunk) {
		RandomSpreadGenerator v = picker.get();
		if(v != null)return v;
		synchronized (picker) {
			v = picker.get();
			if(v != null)return v;
			v = new RandomSpreadGenerator();
			v.loadAll(chunk);
			picker.set(v);
			return v;
		}
	}

	public static RandomSpreadGenerator.PickResult pick(LevelChunk chunk, boolean cluster, long seed) {
		ServerLevel level = (ServerLevel) chunk.getLevel();
		return getPicker(level).pick(level, chunk.getPos(), cluster, seed);
	}

	public static RandomSource rngFromChunk(LevelChunk chunk, boolean cluster, long seed) {
		long pos = chunk.getPos().toLong();
		return RandomSource.create(seed ^ (cluster ? pos * 31L + 0x9E3779B97F4A7C15L : pos));
	}
}
