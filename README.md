# CoreTweaks

A Minecraft 1.7.10 coremod that contains various vanilla(-adjacent) bug fixes, tweaks and optimizations (mainly to startup time).

## Features
* [VanillaFix](https://www.curseforge.com/minecraft/mc-mods/vanillafix)-like crash handling
* A class transformer cache that speeds up startup
* Many small fixes - check the [Config](https://github.com/makamys/CoreTweaks/wiki/Config) page on the wiki for the full list.

## Incompatibilities

* Since other crash handling mods (e.g. [BetterCrashes](https://github.com/vfyjxf/BetterCrashes), [CrashGuard](https://github.com/FalsePattern/CrashGuard)) overlap in functionality with CoreTweaks's `crashHandler`, it will be disabled if one of them is detected.
* Various coremods will cause a crash on startup due to an incompatibility with Mixin. Use [Mixingasm](https://github.com/makamys/Mixingasm) to fix this.

## Suggested mods
For more 1.7.10 bugfix/performance/debug mods, refer to [this list](https://gist.github.com/makamys/7cb74cd71d93a4332d2891db2624e17c).

## License & Credits

This mod is licensed under the [MIT License](https://github.com/GTNewHorizons/CoreTweaks/blob/master/LICENSE).

It contains code based on minecraft-backport5160, a mod by Itaros which in turn was based on code from Forge and Paper contributors. See [CREDITS](CREDITS) for details.

The Intel rendering fix was implemented based on [the research](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/1294926-themastercavers-world?page=13#c294) done by PheonixVX and TheMasterCaver.
