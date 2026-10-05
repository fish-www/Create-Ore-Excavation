package com.tom.createores.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;

/**
 * Formats a number of ticks as a short duration using at most the two largest units, so that long
 * regeneration times read as "3 d 5 h" instead of a seven digit number.
 */
public class TimeFormatter {
	private static final long SECOND = 20;
	private static final long MINUTE = 60 * SECOND;
	private static final long HOUR = 60 * MINUTE;
	private static final long DAY = 24 * HOUR;

	private TimeFormatter() {
	}

	public static Component formatTicks(long ticks) {
		ticks = Math.max(0L, ticks);
		long days = ticks / DAY;
		long hours = ticks % DAY / HOUR;
		long minutes = ticks % HOUR / MINUTE;
		long seconds = ticks % MINUTE / SECOND;

		List<Component> parts = new ArrayList<>(2);
		if (days > 0) {
			parts.add(unit("tooltip.coe.time.day", days));
			if (hours > 0)parts.add(unit("tooltip.coe.time.hour", hours));
		} else if (hours > 0) {
			parts.add(unit("tooltip.coe.time.hour", hours));
			if (minutes > 0)parts.add(unit("tooltip.coe.time.minute", minutes));
		} else if (minutes > 0) {
			parts.add(unit("tooltip.coe.time.minute", minutes));
			if (seconds > 0)parts.add(unit("tooltip.coe.time.second", seconds));
		} else {
			parts.add(unit("tooltip.coe.time.second", seconds));
		}
		return parts.stream().collect(ComponentJoiner.joining(Component.empty(), Component.literal(" ")));
	}

	private static Component unit(String key, long value) {
		return Component.translatable(key, value);
	}
}
