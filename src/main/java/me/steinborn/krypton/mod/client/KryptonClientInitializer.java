package me.steinborn.krypton.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class KryptonClientInitializer implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(KryptonClientInitializer.class);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Krypton is now accelerating your Minecraft client's networking stack");
    }
}
