package com.jaymospowers.power.xenoform;

import com.jaymospowers.power.xenoform.forms.Velox;
import com.jaymospowers.power.xenoform.forms.Bulwark;
import com.jaymospowers.power.xenoform.forms.Duplex;
import com.jaymospowers.power.xenoform.forms.Gremlin;
import com.jaymospowers.power.xenoform.forms.Knell;
import com.jaymospowers.power.xenoform.forms.Nadir;
import com.jaymospowers.power.xenoform.forms.Prism;
import com.jaymospowers.power.xenoform.forms.Wisp;
import com.jaymospowers.power.xenoform.forms.Wyrm;
import com.jaymospowers.power.xenoform.forms.Pyre;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Every alien the band knows, in dial order. The first {@link #STARTERS} are the ones a fresh band comes with one of;
 * the rest are scanned from the creatures that carry their DNA. Stage 1 shipped Bulwark, Pyre and Blur (now Velox); stage 2 added Prism, Wisp,
 * Gremlin, Knell (scanned from a Warden) and Wyrm (from the Ender Dragon); stage 3 Nadir, the void alien the voices give; then Duplex (from a copper golem).
 */
public final class XenoForms {
	private static final Map<String, XenoForm> ALL = new LinkedHashMap<>();
	/** How many of the first forms a new band may start with. */
	public static final int STARTERS = 3;

	public static final XenoForm BULWARK = register(new Bulwark());
	public static final XenoForm PYRE = register(new Pyre());
	public static final XenoForm VELOX = register(new Velox());
	public static final XenoForm PRISM = register(new Prism());
	public static final XenoForm WISP = register(new Wisp());
	public static final XenoForm GREMLIN = register(new Gremlin());
	public static final XenoForm KNELL = register(new Knell());
	public static final XenoForm WYRM = register(new Wyrm());
	public static final XenoForm DUPLEX = register(new Duplex());
	/** (0.18.95) Rivet, the living metal (from a clockwork golem). */
	public static final XenoForm RIVET = register(new com.jaymospowers.power.xenoform.forms.Rivet());
	/** (0.18.96, patch 4) Gorger (an armadillo), Slipfin (an axolotl), Polestar (the Magnetic Giant). */
	public static final XenoForm GORGER = register(new com.jaymospowers.power.xenoform.forms.Gorger());
	public static final XenoForm SLIPFIN = register(new com.jaymospowers.power.xenoform.forms.Slipfin());
	public static final XenoForm POLESTAR = register(new com.jaymospowers.power.xenoform.forms.Polestar());
	/** The void alien: no creature carries it; the two voices give it ({@link XenoVoices}). */
	public static final XenoForm NADIR = register(new Nadir());
	/** (0.18.96) The stalker's own shape, scanned from its shed skin ({@link XenoStalker}); hidden, but kept once found. */
	public static final XenoForm LURK = register(new com.jaymospowers.power.xenoform.forms.Lurk());

	private XenoForms() {
	}

	private static XenoForm register(XenoForm form) {
		ALL.put(form.id, form);
		return form;
	}

	public static @Nullable XenoForm get(@Nullable String id) {
		return id == null ? null : ALL.get(id);
	}

	public static List<XenoForm> all() {
		return List.copyOf(ALL.values());
	}

	/** How many aliens can be scanned from creatures: a band with that many is complete. */
	public static int scannable() {
		return (int)ALL.values().stream().filter(f -> !f.hidden()).count();
	}

	/** The alien whose DNA {@code creature} carries, or null. */
	public static @Nullable XenoForm scanOf(Entity creature) {
		for (XenoForm form : ALL.values()) {
			if (form.scans(creature)) {
				return form;
			}
		}
		return null;
	}
}
