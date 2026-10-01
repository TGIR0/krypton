# Krypton (TGIR0 fork)

A [Fabric](https://fabricmc.net/) mod that optimizes the Minecraft networking stack and the entity tracker.
It works on both the client and the dedicated server.

This repository is a fork of [astei/krypton](https://github.com/astei/krypton) with extra hardening,
small cleanups, and unit tests. Most of the credit for the mod itself goes to the original author
(see [Credits](#credits)).

> **Status:** early-stage and experimental. There are no published releases yet, and no guarantees about
> stability or compatibility with other mods. Please test on a non-production world or server first.

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

- **Decompression size limit is always enforced.** The claimed uncompressed size of a packet is now checked
  against the limit even when packet validation is turned off. This protects against "zip bomb" packets that
  claim a huge size with tiny data.
- **Safer compression handler setup.** When the compression threshold changes, the mod now checks the real
  type of the existing pipeline handlers (instead of casting blindly) and replaces them if they are not
  Krypton's own.
- **Faster VarInt length calculation.** The lookup table was replaced with a small arithmetic formula. It was
  compared against the vanilla calculation on boundary values, negative numbers, and random inputs.
- **Unit tests.** Added tests for the decompression limit and extra VarInt edge cases (JUnit and Mockito).
- **Housekeeping.** Updated the Gradle wrapper to 9.7.1, made utility classes `final` with private
  constructors, and minor formatting cleanup.

## Advanced options

| System property | Effect |
|-----------------|--------|
| `-Dkrypton.permit-oversized-packets=true` | Raises the maximum allowed uncompressed packet size from 8 MiB to 128 MiB. Leave this off unless you know you need it. |

## Reporting problems

Please open an issue at <https://github.com/TGIR0/krypton/issues> and include:

- Minecraft, Fabric Loader, and Krypton versions
- Whether it happened on a client or a server
- The crash report or `latest.log`
- The list of other mods you use

## Credits

- **[Krypton](https://github.com/astei/krypton)** by Andrew Steinborn (astei) and contributors, the original
  project this fork is based on. It draws on networking work from
  [Velocity](https://velocitypowered.com/) and [Paper](https://papermc.io).
- Native compression and encryption support comes from Velocity's `velocity-native` library.

## License

Licensed under the [GNU Lesser General Public License v3.0](LICENSE), the same license as the upstream project.
