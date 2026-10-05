package com.tom.createores.menu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import com.tom.createores.Registration;
import com.tom.createores.network.OreVeinAtlasClickPacket.Option;

public class OreVeinAtlasMenu extends AbstractContainerMenu {
	private ItemStack heldItem;

	public OreVeinAtlasMenu(MenuType<?> type, int wid, Inventory pinv) {
		this(type, wid, pinv, pinv.player.getMainHandItem());
	}

	public OreVeinAtlasMenu(MenuType<?> type, int wid, Inventory pinv, ItemStack heldItem) {
		super(type, wid);
		this.heldItem = heldItem;
	}

	@Override
	public ItemStack quickMoveStack(Player p_38941_, int p_38942_) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player pl) {
		return pl.getMainHandItem().getItem() == Registration.VEIN_ATLAS_ITEM.get();
	}

	public ItemStack getHeldItem() {
		return heldItem;
	}

	public void click(Option opt, ResourceLocation id) {
		Registration.VEIN_ATLAS_ITEM.get().menuClicked(heldItem, opt, id);
	}

	public void click2(Option opt, int id) {
		Registration.VEIN_ATLAS_ITEM.get().menuClicked2(heldItem, opt, id);
	}
}
