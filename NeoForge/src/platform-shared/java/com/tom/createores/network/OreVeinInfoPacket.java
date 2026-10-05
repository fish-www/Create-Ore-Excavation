package com.tom.createores.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.client.DiscoveredVeinHandler;

public class OreVeinInfoPacket implements Packet {
	public static final CustomPacketPayload.Type<OreVeinInfoPacket> ID = new CustomPacketPayload.Type<>(ResourceLocation.tryBuild(CreateOreExcavation.MODID, "veins_info"));
	public static final StreamCodec<FriendlyByteBuf, OreVeinInfoPacket> STREAM_CODEC = CustomPacketPayload.codec(OreVeinInfoPacket::toBytes, OreVeinInfoPacket::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}

	public CompoundTag tag;

	public OreVeinInfoPacket(CompoundTag tag) {
		this.tag = tag;
	}

	public OreVeinInfoPacket(FriendlyByteBuf pb) {
		tag = pb.readNbt();
	}

	@Override
	public void toBytes(FriendlyByteBuf pb) {
		pb.writeNbt(tag);
	}

	@Override
	public void handleClient() {
		if (CreateOreExcavation.xaero && tag.contains("found")) {
			var mc = net.minecraft.client.Minecraft.getInstance();
			ResourceLocation id = ResourceLocation.tryParse(tag.getString("found"));
			if (id != null && mc.level != null && mc.player != null) {
				DiscoveredVeinHandler.addFound(mc.level.dimension(),
						new BlockPos(tag.getInt("x"), mc.player.getBlockY(), tag.getInt("z")), id);
			}
		}
	}

	@Override
	public void handleServer(ServerPlayer p) {
	}
}
