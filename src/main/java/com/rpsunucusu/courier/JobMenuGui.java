package com.rpsunucusu.courier;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class JobMenuGui extends SimpleGui {
    
    private final String jobType;
    private final CourierMod.DataModel data;
    private final Map<UUID, CourierMod.PlayerMission> activeMissions;
    private final List<CourierMod.PlayerCallRequest> callRequests;
    private final ServerPlayerEntity player;

    public JobMenuGui(ServerPlayerEntity player, String jobType, CourierMod.DataModel data, Map<UUID, CourierMod.PlayerMission> activeMissions, List<CourierMod.PlayerCallRequest> callRequests) {
        super(ScreenHandlerType.GENERIC_9X1, player, false);
        this.jobType = jobType;
        this.data = data;
        this.activeMissions = activeMissions;
        this.callRequests = callRequests;
        this.player = player;
        
        this.setTitle(Text.literal(jobType.equals("TAKSI") ? "§6Taksi Görevleri" : "§bKurye Görevleri"));
        this.buildMenu();
    }
    
    private void buildMenu() {
        List<CourierMod.PlayerCallRequest> availablePlayerRequests = new ArrayList<>();
        for (CourierMod.PlayerCallRequest req : callRequests) {
            if (req.type.equals(jobType)) {
                availablePlayerRequests.add(req);
            }
        }
        
        int slotIndex = 0;
        
        for (CourierMod.PlayerCallRequest req : availablePlayerRequests) {
            if (slotIndex >= 5) break;
            
            GuiElementBuilder builder = new GuiElementBuilder(Items.PLAYER_HEAD)
                .setName(Text.literal("§a[Oyuncu Çağrısı] §f" + req.playerName))
                .addLoreLine(Text.literal("§7Nereden: §e" + req.location.name))
                .addLoreLine(Text.literal(jobType.equals("TAKSI") && req.targetLocation != null ? "§7Nereye: §e" + req.targetLocation.name : "§7Bekleme Süresi: §e" + ((System.currentTimeMillis() - req.timestamp)/1000) + "s"))
                .addLoreLine(Text.literal("§8§m----------------"))
                .addLoreLine(Text.literal("§aKabul etmek için tıkla!"))
                .setCallback((index, type, action, gui) -> {
                    acceptPlayerRequest(req);
                    this.close();
                });
                
            this.setSlot(slotIndex++, builder);
        }
        
        while (slotIndex < 5) {
            CourierMod.MissionPair pair = generateRandomNpcPair();
            if (pair == null) break;
            
            GuiElementBuilder builder = new GuiElementBuilder(Items.PAPER)
                .setName(Text.literal("§e[NPC Çağrısı] §f" + (jobType.equals("TAKSI") ? "Müşteri" : "Teslimat")))
                .addLoreLine(Text.literal("§7Alış: §e" + pair.dagitim.name))
                .addLoreLine(Text.literal("§7Teslim: §e" + pair.musteri.name))
                .addLoreLine(Text.literal("§8§m----------------"))
                .addLoreLine(Text.literal("§aKabul etmek için tıkla!"))
                .setCallback((index, type, action, gui) -> {
                    acceptNpcRequest(pair);
                    this.close();
                });
                
            this.setSlot(slotIndex++, builder);
        }
        
        GuiElementBuilder empty = new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).setName(Text.literal(" "));
        while (slotIndex < 9) {
            this.setSlot(slotIndex++, empty);
        }
    }
    
    private void acceptPlayerRequest(CourierMod.PlayerCallRequest req) {
        if (!callRequests.contains(req)) {
            player.sendMessage(Text.literal("§cBu çağrı artık geçerli değil!"));
            return;
        }
        
        CourierMod.PlayerMission pm = new CourierMod.PlayerMission();
        pm.type = jobType;
        pm.state = "TOPLAMA";
        pm.isPlayerJob = true;
        pm.customerId = req.playerId;
        pm.missionStartTime = System.currentTimeMillis();

        if (jobType.equals("TAKSI")) {
            pm.dagitimLoc = req.location;
            pm.musteriLoc = req.targetLocation != null ? req.targetLocation : req.location;
        } else {
            // For KURYE, first find the closest hub to the courier, then deliver to player
            CourierMod.LocationData closestHub = null;
            double minD = Double.MAX_VALUE;
            for(CourierMod.LocationData hub : data.dagitimNoktalari) {
                double d = player.getBlockPos().getSquaredDistance(hub.x, player.getBlockPos().getY(), hub.z);
                if (d < minD) { minD = d; closestHub = hub; }
            }
            pm.dagitimLoc = closestHub != null ? closestHub : data.dagitimNoktalari.get(0);
            pm.musteriLoc = req.location;
        }
        
        
        double dist = Math.sqrt(Math.pow(pm.dagitimLoc.x - pm.musteriLoc.x, 2) + Math.pow(pm.dagitimLoc.z - pm.musteriLoc.z, 2));
        double ucret = Math.floor(dist * (jobType.equals("TAKSI") ? data.taksiCarpan : data.kuryeCarpan));
        if (ucret < CourierMod.MIN_UCRET) ucret = CourierMod.MIN_UCRET;
        
        net.minecraft.server.MinecraftServer server = player.getServer();
        ServerPlayerEntity customer = server.getPlayerManager().getPlayer(req.playerId);
        
        if (customer != null) {
            int para = CourierMod.getPlayerPara(server, customer);
            if (para < ucret) {
                player.sendMessage(Text.literal("§cMüşterinin yeterli bakiyesi kalmamış."));
                customer.sendMessage(Text.literal("§c" + jobType + " çağrınız yetersiz bakiye (Gereken: " + (int)ucret + " TL) sebebiyle iptal edildi!"));
                callRequests.remove(req);
                return;
            }
            CourierMod.addPlayerPara(server, customer, -(int)ucret);
            pm.ucret = ucret;
        } else {
            // Customer is offline
            player.sendMessage(Text.literal("§cMüşteri oyundan çıkmış."));
            callRequests.remove(req);
            return;
        }

        activeMissions.put(player.getUuid(), pm);
        callRequests.remove(req);

        
        // Müşteriye bildirim gönder
        customer = server.getPlayerManager().getPlayer(req.playerId);
        if (customer != null) {
            if (jobType.equals("TAKSI")) {
                customer.sendMessage(net.minecraft.text.Text.literal("§6[Taksi] §a" + player.getGameProfile().getName() + " isimli taksici çağrınızı kabul etti! Geliyor..."));
            } else {
                customer.sendMessage(net.minecraft.text.Text.literal("§6[Kurye] §a" + player.getGameProfile().getName() + " isimli kurye çağrınızı kabul etti!"));
            }
        }
        
        if (jobType.equals("KURYE")) {
            player.sendMessage(Text.literal("§aÇağrıyı kabul ettin! Önce paketi almak için Dağıtım Merkezine gidin. §eHedef: " + pm.dagitimLoc.name));
        } else {
            player.sendMessage(Text.literal("§aÇağrıyı kabul ettin! §eHedef: " + pm.dagitimLoc.name));
        }
        
        net.minecraft.text.MutableText message = Text.literal("§eHedef waypoint'ini görmek için tıkla: ");
        net.minecraft.text.MutableText nameText = Text.literal("§b[" + pm.dagitimLoc.name + "]");
        nameText.setStyle(nameText.getStyle()
            .withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/kurye wp dagitim"))
            .withHoverEvent(new net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT, Text.literal("§aWaypoint oluşturmak için tıkla!"))));
        message.append(nameText);
        player.sendMessage(message);
    }
    
    private void acceptNpcRequest(CourierMod.MissionPair pair) {
        CourierMod.PlayerMission pm = new CourierMod.PlayerMission();
        pm.type = jobType;
        pm.state = "TOPLAMA";
        pm.isPlayerJob = false;
        pm.dagitimLoc = pair.dagitim;
        pm.musteriLoc = pair.musteri;
        
        if (jobType.equals("TAKSI") && player.getWorld() instanceof net.minecraft.server.world.ServerWorld) {
            net.minecraft.server.world.ServerWorld world = (net.minecraft.server.world.ServerWorld) player.getWorld();
            net.minecraft.entity.passive.VillagerEntity villager = net.minecraft.entity.EntityType.VILLAGER.create(world);
            if (villager != null) {
                villager.refreshPositionAndAngles(pm.dagitimLoc.x + 0.5, pm.dagitimLoc.y, pm.dagitimLoc.z + 0.5, 0, 0);
                villager.setAiDisabled(true);
                villager.setInvulnerable(true);
                villager.setCustomName(Text.literal("§eTaksi Müşterisi"));
                villager.setCustomNameVisible(true);
                world.spawnEntity(villager);
                pm.taxiVillagerId = villager.getUuid();
            }
        }
        
        activeMissions.put(player.getUuid(), pm);
        player.sendMessage(Text.literal("§aNPC çağrısını kabul ettin! §eGitmen gereken yer: " + pm.dagitimLoc.name));
        
        net.minecraft.text.MutableText message = Text.literal("§eHedef waypoint'ini görmek için tıkla: ");
        net.minecraft.text.MutableText nameText = Text.literal("§b" + pm.dagitimLoc.name);
        nameText.setStyle(nameText.getStyle()
            .withClickEvent(new net.minecraft.text.ClickEvent(net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/kurye wp dagitim"))
            .withHoverEvent(new net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT, Text.literal("§aWaypoint oluşturmak için tıkla!"))));
        message.append(nameText);
        player.sendMessage(message);
    }
    
    private CourierMod.MissionPair generateRandomNpcPair() {
        List<CourierMod.LocationData> points = jobType.equals("TAKSI") ? data.taksiNoktalari : data.dagitimNoktalari;
        List<CourierMod.LocationData> destPoints = jobType.equals("TAKSI") ? data.taksiNoktalari : data.musteriNoktalari;
        
        if (points.isEmpty() || destPoints.isEmpty()) return null;
        
        List<CourierMod.MissionPair> validPairs = new ArrayList<>();
        net.minecraft.util.math.BlockPos pPos = player.getBlockPos();
        String pWorld = player.getWorld().getRegistryKey().getValue().toString();
        
        for (CourierMod.LocationData start : points) {
            if (start.world != null && start.world.equalsIgnoreCase(pWorld)) {
                double pDistSq = Math.pow(start.x - pPos.getX(), 2) + Math.pow(start.z - pPos.getZ(), 2);
                if (pDistSq <= 500.0 * 500.0) {
                    for (CourierMod.LocationData end : destPoints) {
                        if (start.world.equalsIgnoreCase(end.world) && !start.name.equals(end.name)) {
                            double routeDistSq = Math.pow(start.x - end.x, 2) + Math.pow(start.z - end.z, 2);
                            if (routeDistSq <= 500.0 * 500.0) {
                                validPairs.add(new CourierMod.MissionPair(start, end));
                            }
                        }
                    }
                }
            }
        }
        
        if (validPairs.isEmpty()) return null;
        Collections.shuffle(validPairs);
        return validPairs.get(0);
    }
}
