package com.tom.createores.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.tom.createores.network.OreVeinDiscoverPacket.Entry;

/**
 * Keeps track of the ore markers a player already received, so that walking around does not keep
 * re-sending the same markers (and does not keep re-announcing them in chat).
 */
public class VeinMarkers {
	private static final Map<Player, Set<String>> SENT = new WeakHashMap<>();

	private VeinMarkers() {
	}

	/**
	 * Sends the markers the player does not have yet.
	 *
	 * @return how many markers were actually sent
	 */
	public static int sendNew(ServerPlayer player, ResourceKey<Level> dimension, List<Entry> entries) {
		Set<String> sent = SENT.computeIfAbsent(player, p -> new HashSet<>());
		List<Entry> fresh = new ArrayList<>();
		for (Entry entry : entries) {
			// WeakHashMap is not thread safe and the server may walk several dimensions at once
			synchronized (sent) {
				if (sent.add(key(entry)))fresh.add(entry);
			}
		}
		if (fresh.isEmpty())return 0;
		NetworkHandler.sendTo(player, new OreVeinDiscoverPacket(dimension, fresh, OreVeinDiscoverPacket.Mode.ADD));
		return fresh.size();
	}

	/**
	 * Drops markers that no longer exist, e.g. depleted ore clusters.
	 *
	 * @return how many markers were actually sent for removal
	 */
	public static int sendRemove(ServerPlayer player, ResourceKey<Level> dimension, List<Entry> entries) {
		Set<String> sent = SENT.get(player);
		List<Entry> gone = new ArrayList<>();
		for (Entry entry : entries) {
			String key = key(entry);
			if (sent == null || !sent.remove(key))continue;
			gone.add(entry);
		}
		if (gone.isEmpty())return 0;
		NetworkHandler.sendTo(player, new OreVeinDiscoverPacket(dimension, gone, OreVeinDiscoverPacket.Mode.REMOVE));
		return gone.size();
	}

	private static String key(Entry entry) {
		return entry.veinId() + "@" + (entry.pos().getX() >> 4) + "/" + (entry.pos().getZ() >> 4);
	}
}
