package me.steinborn.krypton.mod.server;

import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KryptonServerInitializer implements DedicatedServerModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(KryptonServerInitializer.class);

    @Override
    public void onInitializeServer() {
        LOGGER.info("Krypton is now accelerating your Minecraft server's networking stack");
    }
}
