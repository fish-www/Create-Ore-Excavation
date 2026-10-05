package com.tom.createores.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import com.tom.createores.Config;
import com.tom.createores.item.HandheldDrillItem;

/**
 * Client only helper for the handheld drill item bar. It lives here rather than in the item class
 * because the item class is loaded on the dedicated server too, and referring to the client player
 * from there makes the server fail to load it.
 */
public class DrillBars {

	private DrillBars() {
	}

	/**
	 * @return how far the held drill is into its current extraction, 0 when it is not mining
	 */
	public static float getMiningProgress(ItemStack drill) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.player == null)return 0;
		if (minecraft.player.getUseItem() != drill)return 0;
		int ticks = Math.max(1, Config.handDrillTicks);
		int elapsed = Math.max(0, HandheldDrillItem.USE_DURATION - minecraft.player.getUseItemRemainingTicks());
		return Math.max((float) (elapsed % ticks) / ticks, 0.04F);
	}
}
