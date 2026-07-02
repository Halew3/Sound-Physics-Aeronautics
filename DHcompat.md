Distant Horizons far propeller occlusion checklist

Implemented in this pass:
- Optional DH API 5+ far propeller occlusion for PropellerLongRangeAudio-eligible sounds.
- Compile-only Distant Horizons API 5.1.0 dependency.
- One soft terrain cache created via IDhApiTerrainDataRepo.createSoftCache().
- Per-source throttling, smoothing, fail-open handling, lifecycle cleanup, diagnostics, config, and NeoForge Cloth entries.
- Direct gain/cutoff multipliers only; no DH reverb, material absorption, terrain generation, or generic sound processing.

Manual verification still needed:
- Test with DistantHorizons-3.1.2-b-1.21.1-fabric-neoforge.jar on NeoForge 1.21.1.
- Feature disabled: propellers sound unchanged.
- Feature enabled with no DH installed: no crash, no behavior change, diagnostics report dh_not_loaded.
- Feature enabled with DH installed on flat/open terrain: little or no extra muffle.
- Mountain/ridge between listener and far propeller: direct gain/cutoff reduce smoothly.
- Moving around the obstruction: muffle fades out without popping.
- Source under 192 blocks: no DH query.
- Multiple propellers: query counts remain throttled.
- World unload, server switch, audio reset, and config reload: DH state/cache clear cleanly.
- Sable/Aeronautics aircraft: confirm debug source coordinates are root-world/global, not sublevel-local.

Debug line to inspect:
DH prop occ source=<id> sound=<sound> listener=<x,y,z> sourcePos=<x,y,z> distance=<d> dim=<dimension> result=<reason>

Known API note:
- DH API 5.1.0 exposes createSoftCache(), but its cache interface only guarantees clear() at compile time. Runtime cleanup attempts AutoCloseable.close() when available, otherwise clear().
