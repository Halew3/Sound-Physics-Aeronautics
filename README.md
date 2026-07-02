# SPR Aeronautics

**Sound Physics Aeronautics** is a fork of [Sound Physics Remastered](https://github.com/henkelmax/sound-physics-remastered) built around **Create Aeronautics** and **Sable** moving sublevel acoustics.

Extends Sound Physics so that sublevels are no longer ignored. In short, your Create Aeronautics creations are missing sound physics. Sound Physics Aeronautics fixes that.

---

## What This Adds

### Moving Acoustics

Moving sublevels can contribute their rotated and moving geometry to acoustic checks, letting Sound Physics effects behave naturally around Aeronautics vehicles.

Depending on the situation, this can affect:

* sound muffling through ship hulls
* exterior vs interior sound behavior
* acoustic obstruction from moving sublevel blocks
* positional audio from machinery mounted on moving ships
* sound behavior while the listener or source is on a moving craft

---

### Long-Range Aeronautics Propellers

Large, high-RPM Aeronautics propellers can carry far beyond vanilla range, with a configurable hard cap up to 1024 blocks.

Vanilla Aeronautics propellers have a sound range of just 48 blocks. SPR Aeronautics scales that range up to 1024 blocks depending on propeller size and speed!

They appropriately become softer and more distant with range, turning into a subtle background presence instead of assasulting your ears.

Compatibility with other propulsion sounds from addons like Create: Propulsion is planned.

---

### Doppler Audio for Moving Sources

SPR Aeronautics adds a Doppler effect for positional sound sources, fully configurable.

When a supported source moves toward or away from the listener, its pitch can shift based on relative motion. Neat, huh?


---

### Improved Sound Policy for Modded Worlds

Large modpacks contain a lot of weird sounds that aren't handled well by the original SPR mod without extensive configuration.

SPR Aeronautics adds a stricter sound policy layer so the mod can be more careful about what it processes, improving broad compatibility. 

This helps avoid applying expensive acoustic logic to sounds that basically don't deserve it.


---

## What Changed From Sound Physics Remastered


Major changes include:

* Sable sublevel acoustic support
* long-range propeller audio
* Doppler support for selected positional sources
* safer policy handling/compatability with more than 30 popular mods so far
* catered physics properties for Create and Create Aeronautics blocks with more on the way

---

## Requirements

SPR Aeronautics is built for:

```text
Minecraft 1.21.1
NeoForge
Create
Create Aeronautics
Sable
```

Do **not** install Sound Physics Remastered alongside SPR Aeronautics. Use one or the other.

If you are not using Aeronautics or at least Sable, the original Sound Physics Remastered by henkelmax is probably the better choice.

---

## Known Limits

SPR Aeronautics is, naturally, a bit heavier on performance. Despite this, almost anyone who can run SPR should also be able to run SPR Aeronautics. Extensive optimizations are in place and config tweaks should allow this to run on a sweet potatoe. 

It does **not** *currently* work on Create contraptions, only sable sublevels are integrated. Normal Create trains, gantries, minecart contraptions, and other moving entities still behave like ordinary positional sounds but wider support is planned for these. 
=======


## Credits and Lineage

SPR Aeronautics is a GPLv3 fork of [Sound Physics Remastered](https://github.com/henkelmax/sound-physics-remastered) by [Max Henkel](https://github.com/henkelmax).

Sound Physics Remastered itself descends from:

* [Sound Physics Fabric](https://github.com/vlad2305m/Sound-Physics-Fabric) by [vlad2305m](https://github.com/vlad2305m)
which descends from:
* [Sound Physics](https://github.com/sonicether/Sound-Physics) by [Sonic Ether](https://github.com/sonicether)


SPR Aeronautics preserves that lineage while focusing on moving-machine sound physics.

---

## License

SPR Aeronautics is licensed under the GNU General Public License v3.0.
