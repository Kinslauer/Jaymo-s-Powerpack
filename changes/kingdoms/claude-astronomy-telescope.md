# Articulated GeckoLib telescope

The original walnut-and-brass telescope now has an editable GeckoLib block rig: a fixed tripod `base`, an `azimuth` joint that turns the mount and a child `elevation` joint that tilts the whole barrel and optics around the axle. Includes idle, separate azimuth/elevation reference motions and a combined aim pose, with the existing texture and item icon unchanged.

One telescope is enough for this pass: the Altazimuth Mount can use the same rig and its physical scales rather than duplicating basic, mounted and brass tiers. The static Java block export remains as a fallback while registration is wired.

The geometry is centred for GeckoLib and its feet are at y=0. `art/astronomy/telescope.bbmodel` remains editable, its deterministic generator exports the GeckoLib block files, and front/side/back plus posed renders were inspected with no missing faces, gaps or detached parts.

Not tested in game: block registration, per-telescope saved aim, Altazimuth Mount skill checks, observations and failure messages remain with the gameplay implementation and the separate astronomy-sky dependency. No gameplay Java is changed here.
