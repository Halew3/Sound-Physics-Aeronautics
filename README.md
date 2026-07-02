# SPR Aeronautics

**Sound Physics Aeronautics**  is a fork of [Sound Physics Remastered](https://github.com/henkelmax/sound-physics-remastered) rebuilt with full integration for **Create Aeronautics**

The core Sound Physics experience is preserved and extended it so that sublevels aren't ignored.

---

## What This Adds

### Moving Acoustics

Sub-levels can contribute their rotated and moving geometry to acoustic checks, letting Sound Physics-style effects behave more naturally around Aeronautics vehicles.

Depending on the situation, this can affect:

* sound muffling through ship hulls
* exterior vs interior sound behavior
* acoustic obstruction from moving sublevel blocks
* positional audio from machinery mounted on moving ships
* sound behavior while the listener or source is on a moving craft

---

### Long-Range Aeronautics Propellers

Aeronautics propeller sounds have been reworked to throw much, much further (up to 64 chunks!) in a tasteful way. 

Vanilla aeronautics propellers have a sound range of 48. Now, propeller loops can carry farther across the world. Range is scaled using propeller size and speed when available, with fallback handling for other propellers as well.

They appropriately become softer and more distant with range, turning into a subtle background presence instead of a full-volume machine blasting the player from hundreds of blocks away.

This is intentionally scoped to Aeronautics propellers for the time being, but compatability with other propulsion sounds from addons are planned. 

---

### Doppler Audio for Moving Sources

SPR Aeronautics adds a Doppler effect for positional sound sources, fully configurable.

When a supported source moves toward or away from the listener, its pitch can shift based on relative motion. This is especially noticeable with Aeronautics propellers and moving airships.


---

### Improved Sound Policy for Modded Worlds

Large modpacks contain a lot of weird sounds: global ambience, fake positional loops, music, machinery, records, weather, and mod-specific sound wrappers.

SPR Aeronautics adds a stricter sound policy layer so the mod can be more careful about what it processes, imrpoving broad compatability. 

This helps avoid applying expensive acoustic logic to sounds that basically don't deserve it.

---

### Runtime Diagnostics

SPR Aeronautics includes in-game diagnostic commands for testing sound behavior, propeller tracking, Doppler state, audio source recovery, and acoustic processing.

Most players will never need these, but they are useful for modpack authors, testers, and anyone tuning aircraft audio.

Common commands:

```mcfunction
/spr_aero preset quiet
/spr_aero audio status
/spr_aero audio sources
/spr_aero propeller sources
/spr_aero propeller range
/spr_aero doppler status
```

---

## What Changed From Sound Physics Remastered


Major changes include:

* Sable sublevel acoustic support
* long-range propeller audio
* Doppler support for selected positional sources
* safer policy handling for modded ambient and machinery sounds
* catered physics properties for relevent Create and Aeronautics blocks. 

---

## Requirements

SPR Aeronautics is built for:

```text
Minecraft 1.21.1
NeoForge
Create
Create Aeronautics (Sable)
```

Do **not** install Sound Physics Remastered alongside SPR Aeronautics. Use one or the other. While this mod does technically work by itself, I strongly recommend using the original Sound Physics Remastered by henkelmax if you don't have Aeronautics installed. 

---

## Known Limits

SPR Aeronautics focuses on Create Aeronautics and Sable moving sublevels.

SPR Aeronautics is also, naturally, heavier on performance. Despite this, almost anyone who can run SPR should also be able to run SPR Aeronautics, especially with some adjustment to configs. Extensive optimizations are in place. 

It does **not** *currently* work on Create contraptions, only sable sublevels are integrated.  Normal Create trains, gantries, minecart contraptions, and other moving entities may still behave like ordinary positional sounds but support is planned for these. 

---

## Credits and Lineage

SPR Aeronautics is a GPLv3 fork of [Sound Physics Remastered](https://github.com/henkelmax/sound-physics-remastered) by [Max Henkel](https://github.com/henkelmax).

Sound Physics Remastered itself descends from:

* [Sound Physics Fabric](https://github.com/vlad2305m/Sound-Physics-Fabric) by [vlad2305m](https://github.com/vlad2305m)
which descends from:
* [Sound Physics](https://github.com/sonicether/Sound-Physics) by [Sonic Ether](https://github.com/sonicether)


SPR Aeronautics preserves that lineage while focusing on Create Aeronautics, Sable moving sublevels, long-range aircraft audio, and moving-machine sound physics.

---

## License

SPR Aeronautics is licensed under the GNU General Public License v3.0.
