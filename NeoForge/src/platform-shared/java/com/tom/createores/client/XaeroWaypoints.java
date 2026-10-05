package com.tom.createores.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import com.tom.createores.CreateOreExcavation;
import com.tom.createores.network.OreVeinDiscoverPacket;
import com.tom.createores.recipe.VeinRecipe;

import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

/**
 * Shows discovered veins as waypoints on Xaero's Minimap and World Map.
 * <p>
 * Only ever touched when Xaero's Minimap is loaded, see {@link DiscoveredVeinHandler}.
 */
public class XaeroWaypoints {
	public static final ResourceLocation ORIGIN = ResourceLocation.tryBuild(CreateOreExcavation.MODID, "veins");
	/**
	 * Every marker id starts with the kind of vein it belongs to, so that a clear can be limited to one
	 * kind without having to look up the recipe.
	 */
	private static final String VEIN_PREFIX = "vein|";
	private static final String CLUSTER_PREFIX = "cluster|";

	private XaeroWaypoints() {
	}

	public static int add(ResourceKey<Level> dimension, List<OreVeinDiscoverPacket.Entry> veins) {
		if (Minecraft.getInstance().getConnection() == null)return 0;
		RecipeManager mngr = Minecraft.getInstance().getConnection().getRecipeManager();
		ThirdPartyWaypoints waypoints = forDimension(dimension);
		if (waypoints == null) {
			CreateOreExcavation.LOGGER.info("[Xaero] no minimap world container for {} yet, {} ore vein waypoints skipped", dimension.location(), veins.size());
			return 0;
		}
		int added = 0;
		for (OreVeinDiscoverPacket.Entry entry : veins) {
			RecipeHolder<VeinRecipe> holder = mngr.byKey(entry.veinId()).filter(r -> r.value() instanceof VeinRecipe)
					.map(r -> (RecipeHolder<VeinRecipe>) r).orElse(null);
			if (holder == null)continue;
			String id = id(entry);
			if (waypoints.get(id) != null)continue;
			// drop the marker an older version named without the kind, so that nothing shows up twice
			waypoints.remove(legacyId(entry));
			waypoints.add(id, create(entry.pos(), holder.value()));
			added++;
		}
		CreateOreExcavation.LOGGER.debug("[Xaero] added {} ore vein waypoints for {} (origin {} now has {}), enabled={}",
				added, dimension.location(), ORIGIN, waypoints.getCount(), waypoints.isEnabled());
		return added;
	}

	/**
	 * Drops the markers of veins that no longer exist, e.g. depleted ore clusters.
	 *
	 * @return how many markers were removed
	 */
	public static int remove(ResourceKey<Level> dimension, List<OreVeinDiscoverPacket.Entry> veins) {
		ThirdPartyWaypoints waypoints = forDimension(dimension);
		if (waypoints == null)return 0;
		int removed = 0;
		for (OreVeinDiscoverPacket.Entry entry : veins) {
			String id = id(entry);
			if (waypoints.get(id) == null)continue;
			waypoints.remove(id);
			removed++;
		}
		return removed;
	}

	/**
	 * Removes the markers of one kind, or of both kinds.
	 *
	 * @return how many markers were removed
	 */
	public static int clear(OreVeinDiscoverPacket.Kind kind) {
		MinimapWorldRootContainer root = root();
		if (root == null)return 0;
		int cleared = 0;
		for (MinimapWorldContainer container : root.getSubContainers()) {
			ThirdPartyWaypoints waypoints = container.getThirdPartyWaypointManager().get(ORIGIN);
			List<String> ids = new ArrayList<>();
			for (String id : waypoints.getIds()) {
				OreVeinDiscoverPacket.Kind marker = kindOf(id);
				// an id written by an older version has no kind in it, clear it with either kind
				if (marker == null || matches(marker, kind))ids.add(id);
			}
			for (String id : ids)waypoints.remove(id);
			cleared += ids.size();
		}
		CreateOreExcavation.LOGGER.info("[Xaero] cleared {} {} ore vein waypoints", cleared, kind);
		return cleared;
	}

	private static boolean matches(OreVeinDiscoverPacket.Kind marker, OreVeinDiscoverPacket.Kind kind) {
		if (marker == OreVeinDiscoverPacket.Kind.CLUSTER)return kind.clusters();
		return kind.veins();
	}

	/**
	 * @return the kind a marker id belongs to, null when the id was written by an older version
	 */
	private static OreVeinDiscoverPacket.Kind kindOf(String id) {
		if (id.startsWith(CLUSTER_PREFIX))return OreVeinDiscoverPacket.Kind.CLUSTER;
		if (id.startsWith(VEIN_PREFIX))return OreVeinDiscoverPacket.Kind.VEIN;
		return null;
	}

	private static ThirdPartyWaypoints forDimension(ResourceKey<Level> dimension) {
		MinimapWorldRootContainer root = root();
		if (root == null)return null;
		String directory = root.getSession().getDimensionHelper().getDimensionDirectoryName(dimension);
		MinimapWorldContainer container = root.addSubContainer(root.getPath().resolve(directory));
		return container != null ? container.getThirdPartyWaypointManager().get(ORIGIN) : null;
	}

	private static MinimapWorldRootContainer root() {
		if (Minecraft.getInstance().level == null)return null;
		MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
		return session != null ? session.getWorldManager().getAutoRootContainer() : null;
	}

	private static Waypoint create(BlockPos pos, VeinRecipe vein) {
		String name = vein.getName().getString();
		String initials = initials(name);
		return new Waypoint(pos.getX(), pos.getY(), pos.getZ(), name, initials, color(vein), WaypointPurpose.NORMAL);
	}

	/**
	 * The colour is up to the recipe ({@code waypointColor}), clusters without one are grey and veins
	 * without one take the colour of their rarity.
	 */
	private static WaypointColor color(VeinRecipe vein) {
		WaypointColor color = parseColor(vein.getWaypointColor());
		if (color != null)return color;
		if (vein.isCluster())return WaypointColor.GRAY;
		return vein.isRare() ? WaypointColor.GOLD : WaypointColor.WHITE;
	}

	/**
	 * @return the colour a recipe named, null when it named none or an unknown one
	 */
	private static WaypointColor parseColor(String name) {
		if (name == null || name.isEmpty())return null;
		try {
			return WaypointColor.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			CreateOreExcavation.LOGGER.warn("Unknown waypointColor '{}' in a vein recipe, valid names are the Xaero waypoint colours", name);
			return null;
		}
	}

	private static String initials(String name) {
		if (name.isEmpty())return "?";
		return name.substring(0, 1).toUpperCase();
	}

	private static String id(OreVeinDiscoverPacket.Entry entry) {
		return (entry.small() ? CLUSTER_PREFIX : VEIN_PREFIX) + chunkKey(entry);
	}

	private static String legacyId(OreVeinDiscoverPacket.Entry entry) {
		return entry.veinId().getPath() + "@" + (entry.pos().getX() >> 4) + "/" + (entry.pos().getZ() >> 4);
	}

	private static String chunkKey(OreVeinDiscoverPacket.Entry entry) {
		return entry.veinId() + "@" + (entry.pos().getX() >> 4) + "/" + (entry.pos().getZ() >> 4);
	}
}
