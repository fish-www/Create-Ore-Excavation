package com.tom.createores.client;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.network.OreVeinDiscoverPacket;
import com.tom.createores.network.OreVeinDiscoverPacket.Mode;

/**
 * Client side entry point for veins the server revealed.
 */
public class DiscoveredVeinHandler {

	public static void received(ResourceKey<Level> dimension, List<OreVeinDiscoverPacket.Entry> veins, OreVeinDiscoverPacket.Mode mode, OreVeinDiscoverPacket.Kind kind) {
		if (!CreateOreExcavation.xaero)return;
		if (mode == OreVeinDiscoverPacket.Mode.CLEAR) {
			XaeroWaypoints.clear(kind);
			message(Component.translatable("chat.coe.discover.cleared" + suffix(kind)));
			return;
		}
		if (veins.isEmpty())return;
		if (mode == OreVeinDiscoverPacket.Mode.REMOVE) {
			int removed = XaeroWaypoints.remove(dimension, veins);
			if (removed > 0)message(Component.translatable("chat.coe.discover.removed", String.valueOf(removed)));
			return;
		}
		int added = XaeroWaypoints.add(dimension, veins);
		if (added > 0)message(Component.translatable("chat.coe.discover.marked" + suffix(kind), String.valueOf(added)));
	}

	/**
	 * @return the language key suffix that says what kind of markers the message is about
	 */
	private static String suffix(OreVeinDiscoverPacket.Kind kind) {
		switch (kind) {
		case VEIN:
			return ".vein";
		case CLUSTER:
			return ".cluster";
		default:
			return "";
		}
	}

	public static void addFound(ResourceKey<Level> dimension, BlockPos pos, ResourceLocation veinId) {
		if (!CreateOreExcavation.xaero)return;
		XaeroWaypoints.add(dimension, List.of(new OreVeinDiscoverPacket.Entry(pos, veinId, false)));
	}

	private static void message(Component component) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null)return;
		mc.player.displayClientMessage(component.copy().withStyle(ChatFormatting.AQUA), false);
	}
}
