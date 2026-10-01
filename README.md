# Krypton (TGIR0 fork)

A [Fabric](https://fabricmc.net/) mod that optimizes the Minecraft networking stack and the entity tracker.
It works on both the client and the dedicated server.

This repository is a fork of [astei/krypton](https://github.com/astei/krypton) with extra hardening,
small cleanups, and unit tests. Most of the credit for the mod itself goes to the original author
(see [Credits](#credits)).

> **Status:** experimental. This fork adds defensive networking changes and is intended to be tested with
> the exact modpack and server configuration before production deployment.

## Requirements

| Item | Version |
|------|---------|
| Minecraft | 26.3 or newer |
| Fabric Loader | 0.18.4 or newer |
| JDK (only for building) | 25 |

## Installation

There are no prebuilt releases yet, so you need to build the mod from source (see below).
After building:

1. Install Fabric Loader for your Minecraft version.
2. Copy the built `.jar` file into your `mods` folder (client or server).
3. Start the game or server.

## Building from source

You need JDK 25 and Git.

```bash
git clone https://github.com/TGIR0/krypton.git
cd krypton

# Linux / macOS
./gradlew build

# Windows
gradlew.bat build
```

The built jar will be in `build/libs/`. Use the file without the `-sources` suffix.

To run the unit tests only:

```bash
./gradlew test
```

## What is different from upstream

- **Decompression size limit is always enforced.** The claimed uncompressed size is checked against the active hard cap even when packet validation is disabled.
- **Per-connection decompression rate limiting.** Large decompression requests are budgeted over a one-second window so a peer cannot repeatedly force large destination-buffer allocations without hitting a limit.
- **Negative claimed sizes are rejected.** Malformed compression metadata cannot reach the native allocator.
- **Safer compression handler setup.** Krypton updates or replaces vanilla/Krypton compression handlers but preserves unknown third-party handlers instead of removing them.
- **e4mc compatibility.** The conflicting Krypton login encryption mixin is skipped automatically when e4mc is loaded.
- **Faster VarInt length calculation.** The lookup table was replaced with a small arithmetic formula and covered by boundary/negative-value tests.
- **Correct reference-count cleanup.** The inactive legacy query path releases reference-counted messages before cancelling the pipeline read.
- **Unit tests.** Added tests for decompression budgeting and VarInt edge cases.
- **Housekeeping.** Utility classes are `final` with private constructors and the Gradle wrapper is kept current with the fork.

## Advanced options

| System property | Effect |
|-----------------|--------|
| `-Dkrypton.permit-oversized-packets=true` | Raises the maximum allowed uncompressed packet size from 8 MiB to 128 MiB. Leave this off unless you know you need it. |
| `-Dkrypton.max-decompressed-bytes-per-second=<bytes>` | Changes the per-connection decompression budget. The default is 128 MiB/s. Values `<= 0` or invalid values fall back to the default. |

The rate limiter is independent of the per-packet cap: one packet may consume up to the configured packet cap, but repeated large packets are bounded by the per-connection budget.

## Compatibility note

Krypton modifies the Netty pipeline through Mixins. Modpacks containing other networking or transport mods should be tested as a complete set. When Krypton sees a foreign handler occupying a compression slot, it leaves that handler in place rather than removing it.

## Reporting problems

Please open an issue at <https://github.com/TGIR0/krypton/issues> and include:

- Minecraft, Fabric Loader, and Krypton versions
- Whether it happened on a client or a server
- The crash report or `latest.log`
- The list of other mods you use
- Relevant JVM system properties, especially compression-related ones

## Credits

- **[Krypton](https://github.com/astei/krypton)** by Andrew Steinborn (astei) and contributors, the original
  project this fork is based on. It draws on networking work from
  [Velocity](https://velocitypowered.com/) and [Paper](https://papermc.io).
- Native compression and encryption support comes from Velocity's `velocity-native` library.

## License

Licensed under the [GNU Lesser General Public License v3.0](LICENSE), the same license as the upstream project.
