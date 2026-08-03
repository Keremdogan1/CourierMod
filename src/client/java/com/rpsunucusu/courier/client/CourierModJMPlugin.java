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
    private static java.util.List<MarkerOverlay> activeMarkers = new java.util.ArrayList<>();

    @Override
    public void initialize(IClientAPI api) {
        jmAPI = api;
        try {
            api.subscribe(getModId(), java.util.EnumSet.of(journeymap.client.api.event.ClientEvent.Type.MAP_CLICKED));
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("[CourierMod] JourneyMap API initialized.");
    }

    @Override
    public String getModId() {
        return "couriermod";
    }

    @Override
    public void onEvent(journeymap.client.api.event.ClientEvent event) {
        if (event instanceof journeymap.client.api.event.FullscreenMapEvent.ClickEvent) {
            journeymap.client.api.event.FullscreenMapEvent.ClickEvent clickEvent = (journeymap.client.api.event.FullscreenMapEvent.ClickEvent) event;
            net.minecraft.util.math.BlockPos pos = clickEvent.getLocation();

            if (CourierNavigationClient.navigasyonSecimiBekleniyor) {
                CourierNavigationClient.navigasyonSecimiBekleniyor = false;
                
                MinecraftClient.getInstance().execute(() -> {
                    CourierNavigationClient.baslatNavigasyon(pos);
                    if (MinecraftClient.getInstance().currentScreen != null) {
                        MinecraftClient.getInstance().setScreen(null); // Haritayi kapat
                    }
                });
                
                if (event instanceof journeymap.client.api.event.FullscreenMapEvent.ClickEvent.Pre) {
                    event.cancel();
                }
            } 
            else if (CourierModClient.taksiMapActive) {
                for (CourierModClient.LocationData loc : CourierModClient.taksiNoktalari) {
                    double dist = Math.sqrt(Math.pow(loc.x - pos.getX(), 2) + Math.pow(loc.z - pos.getZ(), 2));
                    if (dist < 10) { // 10 blok yakınına tiklandiysa
                        MinecraftClient.getInstance().execute(() -> {
                            if (MinecraftClient.getInstance().player != null) {
                                MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.literal("§eTaksi noktasina tiklandi: " + loc.name), false);
                                MinecraftClient.getInstance().player.networkHandler.sendCommand("taksi cagir " + loc.name);
                            }
                        });
                        
                        if (event instanceof journeymap.client.api.event.FullscreenMapEvent.ClickEvent.Pre) {
                            event.cancel();
                        }
                        return; // Sadece bir noktayi tetikle
                    }
                }
            }
        }
    }

    public static void refreshWaypoints() {
        if (jmAPI == null) return;
        
        try {
            jmAPI.removeAll("couriermod");
            activeMarkers.clear();
            
            for (CourierModClient.LocationData loc : CourierModClient.taksiNoktalari) {
                // Using a vanilla map texture instead of a JourneyMap one to avoid missing texture issues
                MapImage icon = new MapImage(new Identifier("minecraft", "textures/item/map.png"), 64, 64);
                icon.setAnchorX(icon.getDisplayWidth() / 2.0)
                    .setAnchorY(icon.getDisplayHeight() / 2.0);

                BlockPos pos = new BlockPos(loc.x, loc.y, loc.z);
                
                MarkerOverlay marker = new MarkerOverlay("couriermod", "taksi_" + loc.name.replaceAll("\\s+", "_"), pos, icon);
                marker.setTitle("§e§l" + loc.name + "\n§aTaksi çağırmak için tıklayın!");
                marker.setLabel("§e" + loc.name + " §7(Tıkla)");
                
                // Parse dimension correctly instead of hardcoding OVERWORLD
                net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimKey = net.minecraft.world.World.OVERWORLD;
                try {
                    dimKey = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new Identifier(loc.world));
                } catch (Exception ignored) {}
                marker.setDimension(dimKey);
                
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
                                MinecraftClient.getInstance().player.sendMessage(net.minecraft.text.Text.literal("§eTaksi noktasina tiklandi: " + loc.name), false);
                                MinecraftClient.getInstance().player.networkHandler.sendCommand("taksi cagir " + loc.name);
                            }
                        });
                        return true;
                    }
                    @Override
                    public void onOverlayMenuPopup(journeymap.client.api.util.UIState mapState, java.awt.geom.Point2D.Double mousePosition, net.minecraft.util.math.BlockPos blockPosition, journeymap.client.api.display.ModPopupMenu modPopupMenu) {}
                });
                
                activeMarkers.add(marker);
                if (CourierModClient.taksiMapActive) {
                    jmAPI.show(marker);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

        public static void showWaypoints() {
        if (jmAPI == null) return;
        for (MarkerOverlay marker : activeMarkers) {
            try { jmAPI.show(marker); } catch (Exception e) {}
        }
    }

    public static void hideWaypoints() {
        if (jmAPI == null) return;
        for (MarkerOverlay marker : activeMarkers) {
            try { jmAPI.remove(marker); } catch (Exception e) {}
        }
    }

    public static void openFullscreenMap() {
        MinecraftClient client = MinecraftClient.getInstance();
        
        new Thread(() -> {
            try {
                Thread.sleep(500); // Wait for chat screen to fully close
            } catch (Exception e) {}
            
            client.execute(() -> {
                boolean opened = false;
                try {
                    for (net.minecraft.client.option.KeyBinding kb : client.options.allKeys) {
                        if (kb.getCategory().toLowerCase().contains("journeymap") || kb.getTranslationKey().toLowerCase().contains("journeymap")) {
                            if (kb.getTranslationKey().toLowerCase().contains("fullscreen") || kb.getTranslationKey().toLowerCase().contains("map")) {
                                kb.setPressed(true);
                                kb.setPressed(false);
                                net.minecraft.client.option.KeyBinding.onKeyPressed(((net.minecraft.client.util.InputUtil.Key)((Object)kb.getDefaultKey())));
                                opened = true;
                                break;
                            }
                        }
                    }
                } catch (Exception e) {}

                if (!opened) {
                    net.minecraft.client.option.KeyBinding.onKeyPressed(net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(org.lwjgl.glfw.GLFW.GLFW_KEY_J));
                }
                
                fallbackMessage();
            });
        }).start();
    }

    private static void tryAlternativeOpen() {
        // Obsolete
    }

    private static void fallbackMessage() {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(
                net.minecraft.text.Text.literal("\u00a7a[Bilgi] \u00a7eHarita kendili\u011finden a\u00e7\u0131lmazsa, \u00a7bl\u00fctfen kendi harita tu\u015funuza (genellikle J) basarak \u00a7eharitay\u0131 a\u00e7\u0131n ve taksi noktas\u0131na t\u0131klay\u0131n!"), false);
        }
    }
}
