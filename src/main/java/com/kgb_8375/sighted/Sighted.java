package com.kgb_8375.sighted;

import com.kgb_8375.sighted.config.ClientConfiguration;
import com.kgb_8375.sighted.listener.EventListener;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "sightedrewinded")
public class Sighted {

    public static final Logger LOGGER = LogManager.getLogger();
    private static Sighted instance;
    private static EventListener listenerInstance;

    {
        instance = this;
    }

    public static Sighted getInstance() {
        return instance;
    }

    public static void enableStatusMessage() {
        if (listenerInstance == null)
            listenerInstance = new EventListener();
        MinecraftForge.EVENT_BUS.register(listenerInstance);
    }

    public static void disableStatusMessage() {
        if (listenerInstance != null) {
            MinecraftForge.EVENT_BUS.unregister(listenerInstance);
            listenerInstance = null;
        }
    }

    @Mod.EventHandler
    private void setup(final FMLInitializationEvent event) {
        LOGGER.info("Sighted client loaded");
    }

    public boolean isEnabled() {

        return ClientConfiguration.enabled && Minecraft.getMinecraft().getIntegratedServer() == null;
    }
}
