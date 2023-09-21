package com.kgb_8375.sighted.config;

import net.minecraftforge.common.config.Config;

@Config(modid = "sighted")
public class ClientConfiguration {

    @Config.Comment("Mod enabled")
    public static boolean enabled = true;

    @Config.Comment({"Do not load block entities (e.g. chests) in fake chunks.",
            "These need updating every tick which can add up.",
            "Enabled by default because the render distance for block entities is usually smaller than the server-view distance anyway."})
    public static boolean noBlockEntities = true;

    @Config.Comment({"Delays the unloading of chunks which are outside your view distance.",
            "Saves you from having to reload all chunks when leaving the area for a short moment (e.g. cut scenes).",
            "Does not work across dimensions."})
    @Config.RangeInt(min = 1, max = 3600)
    public static int unloadDelaySecs = 60;

    @Config.Comment({"Changes the maximum value configurable for Render Distance.",
            "Requires Optifine"})
    @Config.RangeInt(min = 6, max = 64)
    public static int maxRenderDistance = 32;

}
