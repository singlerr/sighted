package com.kgb_8375.sighted;

import com.kgb_8375.sighted.config.ClientConfiguration;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "sighted")
public class Sighted {

    public static final Logger LOGGER = LogManager.getLogger();
    private static Sighted instance;

    {
        instance = this;
    }


    public static Sighted getInstance() {
        return instance;
    }


    @Mod.EventHandler
    private void setup(final FMLInitializationEvent event) {
        LOGGER.info("Sighted client loaded");
    }

    public boolean isEnabled() {
        return ClientConfiguration.enabled && Minecraft.getMinecraft().getIntegratedServer() == null;
    }
}
