package com.rpsunucusu.courier.client;

import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.MarkerOverlay;
import journeymap.client.api.display.DisplayType;
import journeymap.client.api.model.MapImage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;

@journeymap.client.api.ClientPlugin
public class CourierModJMPlugin implements IClientPlugin {

    private static IClientAPI jmAPI;

    @Override
    public void initialize(IClientAPI api) {
        jmAPI = api;
        System.out.println("[CourierMod] JourneyMap API initialized.");
    }

    @Override
    public String getModId() {
        return "couriermod";
    }

    @Override
    public void onEvent(journeymap.client.api.event.ClientEvent event) {
        // Handle events if needed
    }

    public static void refreshWaypoints() {
        if (jmAPI == null) return;
        
        try {
            jmAPI.removeAll("couriermod");
            
            for (CourierModClient.LocationData loc : CourierModClient.taksiNoktalari) {
                // Using a default map marker image from JourneyMap (or Minecraft)
                MapImage icon = new MapImage(new Identifier("journeymap", "ui/img/waypoint-icon.png"), 32, 32);
                icon.setAnchorX(icon.getDisplayWidth() / 2.0)
                    .setAnchorY(icon.getDisplayHeight() / 2.0);

                BlockPos pos = new BlockPos(loc.x, loc.y, loc.z);
                
                MarkerOverlay marker = new MarkerOverlay("couriermod", "taksi_" + loc.name.replaceAll("\\s+", "_"), pos, icon);
                marker.setTitle("§e§l" + loc.name + "\n§aTaksi çağırmak için tıklayın!");
                marker.setDimension(net.minecraft.world.World.OVERWORLD);
                
                marker.setOverlayListener(new journeymap.client.api.display.IOverlayListener() {
                    @Override
                    public void onActivate(journeymap.client.api.util.UIState mapState) {}
                    @Override
                    public void onDeactivate(journeymap.client.api.util.UIState mapState) {}
                    @Override
                    public void onMouseMove(journeymap.client.api.util.UIState mapState, java.awt.geom.Point2D.Double mousePosition, net.minecraft.util.math.BlockPos blockPosition) {}
                    @Override
                    public void onMouseOut(journeymap.client.api.util.UIState mapState, java.awt.geom.Point2D.Double mousePosition, net.minecraft.util.math.BlockPos blockPosition) {}
                    @Override
                    public boolean onMouseClick(journeymap.client.api.util.UIState mapState, java.awt.geom.Point2D.Double mousePosition, net.minecraft.util.math.BlockPos blockPosition, int button, boolean doubleClick) {
                        MinecraftClient.getInstance().execute(() -> {
                            if (MinecraftClient.getInstance().player != null) {
                                MinecraftClient.getInstance().player.networkHandler.sendCommand("taksi cagir " + loc.name);
                            }
                        });
                        return false;
                    }
                    @Override
                    public void onOverlayMenuPopup(journeymap.client.api.util.UIState mapState, java.awt.geom.Point2D.Double mousePosition, net.minecraft.util.math.BlockPos blockPosition, journeymap.client.api.display.ModPopupMenu modPopupMenu) {}
                });
                
                jmAPI.show(marker);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void openFullscreenMap() {
        if (jmAPI == null) return;
        try {
            boolean found = false;
            for (net.minecraft.client.option.KeyBinding key : MinecraftClient.getInstance().options.allKeys) {
                String cat = key.getCategory().toLowerCase();
                String name = key.getTranslationKey().toLowerCase();
                if (cat.contains("journeymap") && (name.contains("map") || name.contains("fullscreen") || name.contains("display"))) {
                    net.minecraft.client.option.KeyBinding.onKeyPressed(key.getDefaultKey());
                    key.setPressed(true);
                    key.setPressed(false);
                    found = true;
                    break;
                }
            }
            if (!found) {
                System.out.println("[CourierMod] Could not find JourneyMap keybinding!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
