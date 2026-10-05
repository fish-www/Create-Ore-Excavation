package com.tom.createores;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.util.INBTSerializable;

import com.tom.createores.network.OreVeinDiscoverPacket.Kind;

public class OreDataAttachment implements INBTSerializable<CompoundTag> {
	private OreData data;

	public OreDataAttachment(IAttachmentHolder holder) {
		data = new OreData();
	}

	@Override
	public CompoundTag serializeNBT(Provider provider) {
		return (CompoundTag) OreData.Serialized.CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), data.save()).getOrThrow();
	}

	@Override
	public void deserializeNBT(Provider provider, CompoundTag nbt) {
		OreData.Serialized.CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), nbt).ifSuccess(data::load);
	}

	public static OreData getData(LevelChunk chunk) {
		if(chunk.getLevel().isClientSide)throw new RuntimeException("Ore Data accessed from client");
		OreDataAttachment at = chunk.getData(CreateOreExcavation.ORE_DATA);
		if (at == null) {
			at = new OreDataAttachment(chunk);
			chunk.setData(CreateOreExcavation.ORE_DATA, at);
		}
		OreData data = at.data;
		// a chunk marked by /coe regenerate drops what it holds and picks again, veins and ore clusters
		// are marked apart and each kind is picked from the seed the last command set
		ServerLevel level = (ServerLevel) chunk.getLevel();
		ChunkPos pos = chunk.getPos();
		boolean first = !data.isLoaded();
		int veinEpoch = VeinRegeneration.epochFor(level, pos, Kind.VEIN);
		int clusterEpoch = VeinRegeneration.epochFor(level, pos, Kind.CLUSTER);
		boolean veins = first || data.getGen() < veinEpoch;
		boolean clusters = first || data.getClusterGen() < clusterEpoch;
		if (veins || clusters) {
			if (veins)data.clear(false);
			if (clusters)data.clear(true);
			data.populate(chunk, veins, clusters,
					VeinRegeneration.seedFor(level, Kind.VEIN), VeinRegeneration.seedFor(level, Kind.CLUSTER));
			if (veins)data.setGen(veinEpoch);
			if (clusters)data.setClusterGen(clusterEpoch);
			chunk.setUnsaved(true);
		}
		return data;
	}
}
