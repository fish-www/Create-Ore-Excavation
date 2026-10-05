package com.tom.createores;

import org.apache.commons.lang3.tuple.Pair;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public class Config {
	public static class Server {
		public IntValue maxExtractorsPerVein;
		public BooleanValue defaultInfinite;
		public IntValue handDrillTicks, handDrillFuelPerUnit, handDrillFuelCapacity;
		public IntValue handDrillRadius, handDrillSearchRadius;
		public IntValue veinFinderNear, veinFinderFar, veinFinderCd;

		private Server(ModConfigSpec.Builder builder) {
			builder.comment("IMPORTANT NOTICE:",
					"You can add more entries using KubeJS",
					"https://github.com/tom5454/Create-Ore-Excavation#kubejs").
			define("importantInfo", true);

			defaultInfinite = builder.comment("Veins whose recipe leaves 'finite' at default hold infinite ore. When disabled their amount comes from the reserve range written in the vein recipe").translation("config.coe.defaultInfinite").define("defaultInfinite", false);

			maxExtractorsPerVein = builder.comment("Max number of extractor per ore vein, Set to 0 for infinite").translation("config.coe.maxExtractorsPerVein")
					.defineInRange("maxExtractorsPerVein", 0, 0, 64);

			handDrillTicks = builder.comment("Ticks the Handheld Drill needs per extracted item").translation("config.coe.handDrillTicks")
					.defineInRange("handDrillTicks", 30, 1, Integer.MAX_VALUE);

			handDrillFuelPerUnit = builder.comment("Fuel the Handheld Drill consumes per extracted item, 1 coal is 1600 fuel").translation("config.coe.handDrillFuelPerUnit")
					.defineInRange("handDrillFuelPerUnit", 200, 1, Integer.MAX_VALUE);

			handDrillFuelCapacity = builder.comment("Maximum fuel the Handheld Drill can hold, 1 coal is 1600 fuel").translation("config.coe.handDrillFuelCapacity")
					.defineInRange("handDrillFuelCapacity", 20000, 1, Integer.MAX_VALUE);

			handDrillRadius = builder.comment("Handheld Drill cluster scan radius in chunks, the chunk the player stands in and the ring around it").translation("config.coe.handDrillRadius")
					.defineInRange("handDrillRadius", 1, 0, 8);

			handDrillSearchRadius = builder.comment("How far the Handheld Drill looks for the closest ore cluster when its own chunk has none, in blocks").translation("config.coe.handDrillSearchRadius")
					.defineInRange("handDrillSearchRadius", 256, 16, 1024);

			veinFinderNear = builder.comment("Vein Finder 'Found Nearby' range in chunks").translation("config.coe.veinFinderNear")
					.defineInRange("veinFinderNear", 1, 1, 8);

			veinFinderFar = builder.comment("Vein Finder accuracy for 'Found traces of ...'").translation("config.coe.veinFinderFar")
					.defineInRange("veinFinderFar", 25, 1, 1000);

			veinFinderCd = builder.comment("Vein Finder use cooldown in ticks").translation("config.coe.veinFinderCd")
					.defineInRange("veinFinderCd", 100, 10, 1000);
		}
	}

	public static class Common {

		public Common(ModConfigSpec.Builder builder) {
			builder.comment("IMPORTANT NOTICE:",
					"THIS IS ONLY THE COMMON CONFIG. It does not contain all the values adjustable for Create Ore Excavation",
					"The settings have been moved to createoreexcavation-server.toml",
					"That file is PER WORLD, meaning you have to go into 'saves/<world name>/serverconfig' to adjust it. Those changes will then only apply for THAT WORLD.",
					"You can then take that config file and put it in the 'defaultconfigs' folder to make it apply automatically to all NEW worlds you generate FROM THERE ON.",
					"This may appear confusing to many of you, but it is a new sensible way to handle configuration, because the server configuration is synced when playing multiplayer.").
			define("importantInfo", true);
		}
	}

	static final ModConfigSpec commonSpec;
	public static final Common COMMON;
	static {
		final Pair<Common, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Common::new);
		commonSpec = specPair.getRight();
		COMMON = specPair.getLeft();
	}

	static final ModConfigSpec serverSpec;
	public static final Server SERVER;
	static {
		final Pair<Server, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Server::new);
		serverSpec = specPair.getRight();
		SERVER = specPair.getLeft();
	}

	public static int maxExtractorsPerVein, handDrillTicks, handDrillFuelPerUnit, handDrillFuelCapacity, handDrillRadius, handDrillSearchRadius, veinFinderNear, veinFinderFar, veinFinderCd;
	public static boolean defaultInfinite;

	public static void load(ModConfig modConfig) {
		if(modConfig.getType() == ModConfig.Type.SERVER) {
			defaultInfinite = SERVER.defaultInfinite.get();
			maxExtractorsPerVein = SERVER.maxExtractorsPerVein.get();
			handDrillTicks = SERVER.handDrillTicks.get();
			handDrillFuelPerUnit = SERVER.handDrillFuelPerUnit.get();
			handDrillFuelCapacity = SERVER.handDrillFuelCapacity.get();
			handDrillRadius = SERVER.handDrillRadius.get();
			handDrillSearchRadius = SERVER.handDrillSearchRadius.get();
			veinFinderNear = SERVER.veinFinderNear.get();
			veinFinderFar = SERVER.veinFinderFar.get();
			veinFinderCd = SERVER.veinFinderCd.get();
		}
	}
}
