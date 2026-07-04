package com.rpsunucusu.courier.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.ArrayList;

@Environment(EnvType.CLIENT)
public class CourierModClient implements ClientModInitializer {
    
    public static class LocationData {
        public String name;
        public int x;
        public int y;
        public int z;
        public String world;
    }
    
    public static List<LocationData> taksiNoktalari = new ArrayList<>();
    
    public static boolean taksiMapActive = false;
    public static long taksiRequestedTime = 0;
    public static String taksiRequestSuccessMsg = "";

    @Override
    public void onInitializeClient() {
        System.out.println("[CourierMod] Initializing Client...");
        
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((screen1, drawContext, mouseX, mouseY, tickDelta) -> {
                if (taksiMapActive && screen1.getClass().getName().contains("journeymap")) {
                    net.minecraft.client.font.TextRenderer tr = client.textRenderer;
                    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                    com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                    com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
                    drawContext.getMatrices().push();
                    drawContext.getMatrices().translate(0, 0, 1000);

                    int baseY = scaledHeight - 60;

                    if (taksiRequestedTime > 0) {
                        String msg = taksiRequestSuccessMsg.isEmpty() ? "§a§lİsteğiniz taksicilere iletildi!" : taksiRequestSuccessMsg;
                        int w = tr.getWidth(msg);
                        drawContext.fill(scaledWidth / 2 - w / 2 - 10, baseY, scaledWidth / 2 + w / 2 + 10, baseY + 20, 0xAA000000);
                        drawContext.drawCenteredTextWithShadow(tr, msg, scaledWidth / 2, baseY + 6, 0xFFFFFF);
                        
                        if (System.currentTimeMillis() - taksiRequestedTime > 3000) {
                            taksiMapActive = false;
                            taksiRequestedTime = 0;
                            taksiRequestSuccessMsg = "";
                            CourierModJMPlugin.hideWaypoints();
                            client.execute(() -> client.setScreen(null)); // Close the map
                        }
                    } else {
                        // Waiting for selection
                        String msg = "§e§lGitmek İstediğiniz Noktayı Seçin!";
                        int w = tr.getWidth(msg);
                        drawContext.fill(scaledWidth / 2 - w / 2 - 10, baseY, scaledWidth / 2 + w / 2 + 10, baseY + 20, 0xAA000000);
                        drawContext.drawCenteredTextWithShadow(tr, msg, scaledWidth / 2, baseY + 6, 0xFFFFFF);
                    }
                    
                    drawContext.getMatrices().pop();
                    com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
                    com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                }
            });
        });
        
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (taksiMapActive && (client.currentScreen == null || !client.currentScreen.getClass().getName().contains("journeymap"))) {
                taksiMapActive = false;
                taksiRequestedTime = 0;
                CourierModJMPlugin.hideWaypoints();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(new Identifier("couriermod", "taksi_sync"), (client, handler, buf, responseSender) -> {
            String json = buf.readString();
            client.execute(() -> {
                try {
                    Type listType = new TypeToken<ArrayList<LocationData>>(){}.getType();
                    taksiNoktalari = new Gson().fromJson(json, listType);
                    System.out.println("[CourierMod] Received Taksi Points from server! Total: " + taksiNoktalari.size());
                    CourierModJMPlugin.refreshWaypoints();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
        
        ClientPlayNetworking.registerGlobalReceiver(new Identifier("couriermod", "open_taksi_map"), (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                System.out.println("[CourierMod] Received open_taksi_map packet!");
                taksiMapActive = true;
                taksiRequestedTime = 0;
                CourierModJMPlugin.showWaypoints();
                CourierModJMPlugin.openFullscreenMap();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(new Identifier("couriermod", "taksi_request_success"), (client, handler, buf, responseSender) -> {
            String msg = buf.readString();
            client.execute(() -> {
                if (taksiMapActive) {
                    taksiRequestSuccessMsg = msg;
                    taksiRequestedTime = System.currentTimeMillis();
                } else {
                    if (client.player != null) {
                        client.player.sendMessage(net.minecraft.text.Text.literal(msg), false);
                    }
                }
            });
        });
        
    }
}
