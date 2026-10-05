package com.tom.createores.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.client.DiscoveredVeinHandler;

/**
 * Tells the client about veins the server revealed, so they can be added to the map.
 */
public class OreVeinDiscoverPacket implements Packet {
	public static final CustomPacketPayload.Type<OreVeinDiscoverPacket> ID = new CustomPacketPayload.Type<>(ResourceLocation.tryBuild(CreateOreExcavation.MODID, "veins_discover"));
	public static final StreamCodec<FriendlyByteBuf, OreVeinDiscoverPacket> STREAM_CODEC = CustomPacketPayload.codec(OreVeinDiscoverPacket::toBytes, OreVeinDiscoverPacket::new);

	public enum Mode {
		ADD,
		REMOVE,
		CLEAR
	}

	/**
	 * What a packet is about: the ore veins a drill can mine, the ore clusters the hand held drill can
	 * mine, or both.
	 */
	public enum Kind {
		VEIN,
		CLUSTER,
		BOTH;

		public boolean veins() {
			return this != CLUSTER;
		}

		public boolean clusters() {
			return this != VEIN;
		}
	}

	public final ResourceKey<Level> dimension;
	public final List<Entry> veins;
	public final Mode mode;
	public final Kind kind;

	public OreVeinDiscoverPacket(ResourceKey<Level> dimension, List<Entry> veins) {
		this(dimension, veins, Mode.ADD, Kind.BOTH);
	}

	public OreVeinDiscoverPacket(ResourceKey<Level> dimension, List<Entry> veins, Mode mode) {
		this(dimension, veins, mode, Kind.BOTH);
	}

	public OreVeinDiscoverPacket(ResourceKey<Level> dimension, List<Entry> veins, Mode mode, Kind kind) {
		this.dimension = dimension;
		this.veins = veins;
		this.mode = mode;
		this.kind = kind;
	}

	public OreVeinDiscoverPacket(FriendlyByteBuf pb) {
		this(ResourceKey.create(Registries.DIMENSION, pb.readResourceLocation()), readEntries(pb), Mode.values()[pb.readVarInt()], Kind.values()[pb.readVarInt()]);
	}

	private static List<Entry> readEntries(FriendlyByteBuf pb) {
		int size = pb.readVarInt();
		List<Entry> list = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			list.add(new Entry(pb.readBlockPos(), pb.readResourceLocation(), pb.readBoolean()));
		}
		return list;
	}

	@Override
	public void toBytes(FriendlyByteBuf pb) {
		pb.writeResourceLocation(dimension.location());
		pb.writeVarInt(veins.size());
		for (Entry e : veins) {
			pb.writeBlockPos(e.pos());
			pb.writeResourceLocation(e.veinId());
			pb.writeBoolean(e.small());
		}
		pb.writeVarInt(mode.ordinal());
		pb.writeVarInt(kind.ordinal());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}

	@Override
	public void handleClient() {
		DiscoveredVeinHandler.received(dimension, veins, mode, kind);
	}

	@Override
	public void handleServer(ServerPlayer p) {
	}

	public static record Entry(BlockPos pos, ResourceLocation veinId, boolean small) {
	}
}
