package com.jaymoskingdoms.client.mixin.mca;

import com.google.gson.JsonElement;
import com.jaymoskingdoms.Professions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.conczin.mca.client.gui.MCAButton;
import net.conczin.mca.client.gui.MCAScreens;
import net.conczin.mca.resources.Resources;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.5.21) MCA's Professions menu (villager, guard, archer) also offers the settlement's jobs: builder, farmer, miner,
 * woodcutter, one row each below MCA's own. The server answers them ({@code McaCommandMixin}). (0.5.29) The main page
 * offers Quests. (0.7.449) The Interact page offers Ask the Way, which opens a page of places ({@code McaWayPageMixin}).
 */
@Mixin(MCAScreens.class)
abstract class McaScreensMixin {
	@Shadow
	@Final
	private Map<String, MCAButton[]> buttons;

	/** (0.5.29) The main page's Quests button. */
	private static final String QUESTS = "gui.button.quests";
	/** (0.7.55) A tailor's Change Clothes button ({@code Tailors}). */
	private static final String TAILOR = "gui.button.tailor";
	/** (0.7.69) A tailor's Costume Table and costumes sewn to order ({@code TailorCostumes}; only with Jaymo's Powers). */
	private static final String COSTUME_TABLE = "gui.button.costume_table", COSTUME = "gui.button.costume";
	/** How many rows of buttons MCA's own main page has (talk ... family tree): Quests goes under them. */
	private static final int MAIN_ROWS = 6;

	/** (0.7.449) Interact's Ask the Way, the page it opens, and the places on it ({@code Directions.Place}). */
	private static final String WAY = "gui.button.way", WAY_PAGE = "way";
	private static final String[] PLACES = {"healer", "butcher", "smith", "tailor", "tavern", "stables", "ruler"};
	/** The Interact row Ask the Way goes on: under MCA's own (gift ... professions on row 8). */
	private static final int WAY_ROW = 9;

	/** How many rows of buttons MCA's own Professions page has. */
	private static final int MCA_ROWS = 3;

	@Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
	private void jaymoskingdoms$jobs(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
		jaymoskingdoms$quests();
		jaymoskingdoms$way();
		MCAButton[] page = this.buttons.get("professions");
		if (page == null) {
			return;
		}
		// (MCA's own: the back arrow and villager on row 0, guard and archer on rows 1 and 2)
		int row = MCA_ROWS - 1;
		List<MCAButton> all = new ArrayList<>(Arrays.asList(page));
		StringBuilder json = new StringBuilder("[");
		for (String job : Professions.JOBS) {
			String id = "gui.button.profession." + job;
			if (all.stream().anyMatch(b -> b.identifier().equals(id))) {
				continue;
			}
			row++;
			json.append(json.length() > 1 ? "," : "").append("{\"identifier\":\"").append(id).append("\",\"x\":-90,\"y\":10,\"row\":").append(row)
				.append(",\"width\":80,\"height\":20,\"notifyServer\":true,\"targetServer\":false,\"constraints\":\"\"}");
		}
		all.addAll(Arrays.asList(Resources.GSON.fromJson(json.append("]").toString(), MCAButton[].class)));
		this.buttons.put("professions", all.toArray(MCAButton[]::new));
	}

	/** (0.5.29) The main page's Quests button, under MCA's own rows (the server opens the villager's quests). */
	private void jaymoskingdoms$quests() {
		MCAButton[] page = this.buttons.get("main");
		if (page == null || Arrays.stream(page).anyMatch(b -> b.identifier().equals(QUESTS))) {
			return;
		}
		int row = MAIN_ROWS;
		// (0.7.55) and Change Clothes under it, shown only on a tailor (McaTailorButtonMixin)
		String json = "[{\"identifier\":\"" + QUESTS + "\",\"x\":-90,\"y\":10,\"row\":" + row
			+ ",\"width\":80,\"height\":20,\"notifyServer\":true,\"targetServer\":false,\"constraints\":\"adult\",\"hideOnFail\":true},"
			+ "{\"identifier\":\"" + TAILOR + "\",\"x\":-90,\"y\":10,\"row\":" + (row + 1)
			+ ",\"width\":80,\"height\":20,\"notifyServer\":true,\"targetServer\":false,\"constraints\":\"adult\",\"hideOnFail\":true},"
			// (0.7.69) and the tailor's costume work under it
			+ "{\"identifier\":\"" + COSTUME + "\",\"x\":-90,\"y\":10,\"row\":" + (row + 2)
			+ ",\"width\":80,\"height\":20,\"notifyServer\":true,\"targetServer\":false,\"constraints\":\"adult\",\"hideOnFail\":true},"
			+ "{\"identifier\":\"" + COSTUME_TABLE + "\",\"x\":-90,\"y\":10,\"row\":" + (row + 3)
			+ ",\"width\":80,\"height\":20,\"notifyServer\":true,\"targetServer\":false,\"constraints\":\"adult\",\"hideOnFail\":true}]";
		List<MCAButton> all = new ArrayList<>(Arrays.asList(page));
		all.addAll(Arrays.asList(Resources.GSON.fromJson(json, MCAButton[].class)));
		this.buttons.put("main", all.toArray(MCAButton[]::new));
	}

	/** (0.7.449) Interact's Ask the Way, and its page: a back arrow and one row for each place. */
	private void jaymoskingdoms$way() {
		MCAButton[] interact = this.buttons.get("interact");
		if (interact == null || Arrays.stream(interact).anyMatch(b -> b.identifier().equals(WAY))) {
			return;
		}
		List<MCAButton> all = new ArrayList<>(Arrays.asList(interact));
		all.addAll(Arrays.asList(Resources.GSON.fromJson("[{\"identifier\":\"" + WAY + "\",\"x\":-90,\"y\":10,\"row\":" + WAY_ROW
			+ ",\"width\":80,\"height\":20,\"notifyServer\":false,\"targetServer\":false,\"constraints\":\"\"}]", MCAButton[].class)));
		// (0.7.449) and the actions moved off the talk menu under it (InteractActions; McaActionButtonsMixin shows only those
		// that apply, stacked under Ask the Way)
		StringBuilder acts = new StringBuilder("[");
		for (int i = 0; i < com.jaymoskingdoms.InteractActions.ALL.size(); i++) {
			acts.append(i > 0 ? "," : "").append("{\"identifier\":\"gui.button.act.").append(com.jaymoskingdoms.InteractActions.ALL.get(i))
				.append("\",\"x\":-90,\"y\":10,\"row\":").append(WAY_ROW + 1 + i)
				.append(",\"width\":80,\"height\":20,\"notifyServer\":true,\"targetServer\":false,\"constraints\":\"\"}");
		}
		all.addAll(Arrays.asList(Resources.GSON.fromJson(acts.append("]").toString(), MCAButton[].class)));
		this.buttons.put("interact", all.toArray(MCAButton[]::new));
		StringBuilder json = new StringBuilder("[{\"identifier\":\"gui.button.backarrow\",\"x\":-113,\"y\":10,\"row\":0,\"width\":20,\"height\":20,"
			+ "\"notifyServer\":false,\"targetServer\":false,\"constraints\":\"\"}");
		for (int i = 0; i < PLACES.length; i++) {
			json.append(",{\"identifier\":\"").append(WAY).append('.').append(PLACES[i]).append("\",\"x\":-90,\"y\":10,\"row\":").append(i)
				.append(",\"width\":80,\"height\":20,\"notifyServer\":true,\"targetServer\":false,\"constraints\":\"\"}");
		}
		this.buttons.put(WAY_PAGE, Resources.GSON.fromJson(json.append("]").toString(), MCAButton[].class));
	}
}
