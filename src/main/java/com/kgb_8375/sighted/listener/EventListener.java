package com.kgb_8375.sighted.listener;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;

public final class EventListener {


    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        String text = "§bSighted rewinded v1.2.0 §fby singlerr";
        FontRenderer renderer = Minecraft.getMinecraft().fontRenderer;
        renderer.drawStringWithShadow(text, event.getResolution().getScaledWidth() - renderer.getStringWidth(text), renderer.FONT_HEIGHT * 2, Color.WHITE.getRGB());
    }
}
