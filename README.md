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
| Minecraft | 26.3.x |
| Fabric Loader | 0.19.5 or newer |
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
git clone [https://github.com/TGIR0/krypton.git](https://github.com/TGIR0/krypton.git)
cd krypton

# Linux / macOS
./gradlew build

# Windows
gradlew.bat build
