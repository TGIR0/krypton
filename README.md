# Krypton (TGIR0 fork)

A [Fabric](https://fabricmc.net/) mod that optimizes the Minecraft networking stack and the entity tracker.
It works on both the client and the dedicated servethe
This repository is a fork of [astei/krypton](https://github.com/astei/krypton) with extra hardening,
small cleanups, and unit tests. Most of the credit for the mod itself goes to the original author
(see [Credits](#credits)).

> **Status:** experimental. This fork adds defensive networking changes and is intended to be tested with
> the exact modpack and server configuration before production deployment.

## Requirements

| Item | Version |
|------|---------|
| Minecraft | 26.3.x |
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

Every push is also built and tested automatically by GitHub Actions (see `.github/workflows/build.yml`).

## What is different from upstream

- **Decompression size limit is always enforced.** The claimed uncompressed size is checked against the active hard cap even when packet validation is disabled.
- **Negative claimed sizes are rejected.** Malformed compression metadata cannot reach the native allocator.
- **Safer compression handler setup.** When compression is turned off, only vanilla or Krypton compression handlers are removed. When it is turned on, Krypton reuses its own handlers if they already exist (only updating the threshold) and otherwise replaces whatever is in the `compress` and `decompress` slots.
- **e4mc compatibility.** The Krypton login encryption mixin is skipped automatically when e4mc is loaded, to avoid a mixin conflict. In that case the game's normal (vanilla) encryption is used instead of Krypton's native encryption.
- **Correct reference-count cleanup.** The inactive legacy query path releases reference-counted messages before cancelling the pipeline read.
- **Low-latency sockets.** `TCP_NODELAY` is enforced on every TCP connection (normally already on; this is a safety net), avoiding stalls of roughly 40 ms caused by Nagle's algorithm.
- **Frame size check.** Packets too large for the protocol's 3-byte length prefix now fail on the sending side with a clear error.
- **Tunable compression level.** See `krypton.compression-level` below.
- **Unit tests.** A test suite built on Netty's `EmbeddedChannel` covers the compression and encryption handlers: oversized, negative and corrupt packets, exact threshold and size-cap boundaries, full compress/decompress round trips, fragmented encrypted streams, and buffer leak checks (Netty's paranoid leak detector is enabled while tests run). VarInt length calculation is checked against the vanilla result for all 2^32 integers.
- **Housekeeping.** Utility classes are `final` with private constructors and the Gradle wrapper is kept current with the fork.

## Advanced options

| System property | Effect |
|-----------------|--------|
| `-Dkrypton.permit-oversized-packets=true` | Raises the maximum allowed uncompressed packet size from 8 MiB to 128 MiB. Leave this off unless you know you need it. |
| `-Dkrypton.compression-level=<1-9>` | Sets the zlib compression level for packets this side sends (default 4). Lower is faster and sends more bytes; higher sends fewer bytes and uses more CPU. Invalid values fall back to 4. |

## More tuning

See [docs/NETWORK_TUNING.md](docs/NETWORK_TUNING.md) for measured latency results and recommended server, JVM, Linux and IPv6 settings.

## Compatibility note

Krypton modifies the Netty pipeline through Mixins. Modpacks containing other networking or transport mods should be tested as a complete set.

## Reporting problems

Please open an issue at <https://github.com/TGIR0/krypton/issues> and include:

- Minecraft, Fabric Loader, and Krypton versions
- Whether it happened on a client or a server
- The crash report or `latest.log`
- The list of other mods you use
- Any JVM system properties you set for Krypton

## Credits

- **[Krypton](https://github.com/astei/krypton)** by Andrew Steinborn (astei) and contributors, the original
  project this fork is based on. It draws on networking work from
  [Velocity](https://velocitypowered.com/) and [Paper](https://papermc.io).
- Native compression and encryption support comes from Velocity's `velocity-native` library.

## License

Licensed under the [GNU Lesser General Public License v3.0](LICENSE), the same license as the upstream project.
