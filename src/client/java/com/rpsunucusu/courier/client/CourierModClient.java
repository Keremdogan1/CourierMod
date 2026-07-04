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

    @Override
    public void onInitializeClient() {
        System.out.println("[CourierMod] Initializing Client...");
        
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((screen1, drawContext, mouseX, mouseY, tickDelta) -> {
                if (taksiMapActive && screen1.getClass().getName().contains("journeymap")) {
                    net.minecraft.client.font.TextRenderer tr = client.textRenderer;
                    
                    if (taksiRequestedTime > 0) {
                        // Request sent, showing confirmation and counting down to 3 seconds
                        String msg = "\u00a7a\u00a7l\u0130ste\u011finiz taksicilere iletildi!";
                        drawContext.getMatrices().push();
                        drawContext.getMatrices().translate(0, 0, 500); // high Z index
                        drawContext.drawCenteredTextWithShadow(tr, msg, scaledWidth / 2, 20, 0xFFFFFF);
                        drawContext.getMatrices().pop();
                        
                        if (System.currentTimeMillis() - taksiRequestedTime > 3000) {
                            taksiMapActive = false;
                            taksiRequestedTime = 0;
                            client.execute(() -> client.setScreen(null)); // Close the map
                        }
                    } else {
                        // Waiting for selection
                        String msg = "\u00a7e\u00a7lGitmek \u0130stedi\u011finiz Noktay\u0131 Se\u00e7in!";
                        // Draw with high z-index
                        drawContext.getMatrices().push();
                        drawContext.getMatrices().translate(0, 0, 500); // high Z index
                        drawContext.drawCenteredTextWithShadow(tr, msg, scaledWidth / 2, 20, 0xFFFFFF);
                        drawContext.getMatrices().pop();
                    }
                } else if (taksiMapActive) {
                    // If map is closed manually by user pressing ESC
                    taksiMapActive = false;
                    taksiRequestedTime = 0;
                }
            });
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
                CourierModJMPlugin.openFullscreenMap();
            });
        });
    }
}
