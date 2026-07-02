import java.nio.file.*;
import java.io.*;

public class Patcher3 {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("src/main/java/com/rpsunucusu/courier/CourierMod.java");
        String content = new String(Files.readAllBytes(path), "UTF-8");

        String kuryeAlNew = "private int takeMission(CommandContext<ServerCommandSource> context) {\n" +
            "        ServerPlayerEntity p = context.getSource().getPlayer();\n" +
            "        if (p == null) return 0;\n" +
            "        if (activeMissions.containsKey(p.getUuid())) {\n" +
            "            p.sendMessage(Text.literal(P + \"§cZaten aktif bir görevin var!\"));\n" +
            "            return 0;\n" +
            "        }\n" +
            "        List<JobOffer> offers = new ArrayList<>();\n" +
            "        for (PlayerCallRequest req : callRequests) {\n" +
            "            if (req.type.equals(\"KURYE\") && offers.size() < 5) {\n" +
            "                JobOffer o = new JobOffer();\n" +
            "                o.jobId = UUID.randomUUID().toString().substring(0, 8);\n" +
            "                o.type = \"KURYE\";\n" +
            "                o.isPlayerRequest = true;\n" +
            "                o.playerRequest = req;\n" +
            "                o.expireTime = System.currentTimeMillis() + 60000;\n" +
            "                offers.add(o);\n" +
            "                availableOffers.put(o.jobId, o);\n" +
            "            }\n" +
            "        }\n" +
            "        List<MissionPair> validPairs = new ArrayList<>();\n" +
            "        BlockPos pPos = p.getBlockPos();\n" +
            "        for (LocationData dLoc : data.dagitimNoktalari) {\n" +
            "            if (dLoc.world != null && dLoc.world.equalsIgnoreCase(p.getWorld().getRegistryKey().getValue().toString())) {\n" +
            "                if (Math.pow(dLoc.x - pPos.getX(), 2) + Math.pow(dLoc.z - pPos.getZ(), 2) <= 250000.0) {\n" +
            "                    for (LocationData mLoc : data.musteriNoktalari) {\n" +
            "                        if (dLoc.world.equalsIgnoreCase(mLoc.world) && Math.pow(dLoc.x - mLoc.x, 2) + Math.pow(dLoc.z - mLoc.z, 2) <= 250000.0) {\n" +
            "                            validPairs.add(new MissionPair(dLoc, mLoc));\n" +
            "                        }\n" +
            "                    }\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "        if (!validPairs.isEmpty()) {\n" +
            "            while (offers.size() < 5) {\n" +
            "                MissionPair selected = validPairs.get(random.nextInt(validPairs.size()));\n" +
            "                JobOffer o = new JobOffer();\n" +
            "                o.jobId = UUID.randomUUID().toString().substring(0, 8);\n" +
            "                o.type = \"KURYE\";\n" +
            "                o.isPlayerRequest = false;\n" +
            "                o.npcMissionPair = selected;\n" +
            "                o.expireTime = System.currentTimeMillis() + 60000;\n" +
            "                offers.add(o);\n" +
            "                availableOffers.put(o.jobId, o);\n" +
            "            }\n" +
            "        }\n" +
            "        if (offers.isEmpty()) { p.sendMessage(Text.literal(P + \"§cŞu an hiç uygun iş bulunamadı!\")); return 0; }\n" +
            "        p.sendMessage(Text.literal(P + \"§e--- Mevcut Kurye İşleri ---\"));\n" +
            "        for (int i = 0; i < offers.size(); i++) {\n" +
            "            JobOffer o = offers.get(i);\n" +
            "            MutableText msg = Text.literal(\"§7\" + (i + 1) + \". \");\n" +
            "            if (o.isPlayerRequest) msg.append(Text.literal(\"§b[Oyuncu] §f\" + o.playerRequest.playerName + \" \"));\n" +
            "            else msg.append(Text.literal(\"§a[NPC] §f\" + o.npcMissionPair.dagitim.name + \" -> \" + o.npcMissionPair.musteri.name + \" \"));\n" +
            "            MutableText acceptBtn = Text.literal(\"§2§l[KABUL ET]\");\n" +
            "            acceptBtn.setStyle(acceptBtn.getStyle().withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, \"/kurye_accept \" + o.jobId))\n" +
            "                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(\"§aGörevi almak için tıkla!\"))));\n" +
            "            msg.append(acceptBtn);\n" +
            "            p.sendMessage(msg);\n" +
            "        }\n" +
            "        return 1;\n" +
            "    }\n\n";

        String taksiAlNew = "private int takeTaksiMission(CommandContext<ServerCommandSource> context) {\n" +
            "        ServerPlayerEntity p = context.getSource().getPlayer();\n" +
            "        if (p == null) return 0;\n" +
            "        if (activeMissions.containsKey(p.getUuid())) {\n" +
            "            p.sendMessage(Text.literal(\"§6[Taksi] §cZaten aktif bir görevin var!\"));\n" +
            "            return 0;\n" +
            "        }\n" +
            "        List<JobOffer> offers = new ArrayList<>();\n" +
            "        for (PlayerCallRequest req : callRequests) {\n" +
            "            if (req.type.equals(\"TAKSI\") && offers.size() < 5) {\n" +
            "                JobOffer o = new JobOffer();\n" +
            "                o.jobId = UUID.randomUUID().toString().substring(0, 8);\n" +
            "                o.type = \"TAKSI\";\n" +
            "                o.isPlayerRequest = true;\n" +
            "                o.playerRequest = req;\n" +
            "                o.expireTime = System.currentTimeMillis() + 60000;\n" +
            "                offers.add(o);\n" +
            "                availableOffers.put(o.jobId, o);\n" +
            "            }\n" +
            "        }\n" +
            "        List<LocationData> validPickups = new ArrayList<>();\n" +
            "        BlockPos pPos = p.getBlockPos();\n" +
            "        for (LocationData loc : data.taksiNoktalari) {\n" +
            "            if (loc.world != null && loc.world.equalsIgnoreCase(p.getWorld().getRegistryKey().getValue().toString())) {\n" +
            "                if (Math.pow(loc.x - pPos.getX(), 2) + Math.pow(loc.z - pPos.getZ(), 2) <= 250000.0) {\n" +
            "                    validPickups.add(loc);\n" +
            "                }\n" +
            "            }\n" +
            "        }\n" +
            "        if (!validPickups.isEmpty()) {\n" +
            "            while (offers.size() < 5) {\n" +
            "                LocationData pickup = validPickups.get(random.nextInt(validPickups.size()));\n" +
            "                LocationData dropoff = data.taksiNoktalari.get(random.nextInt(data.taksiNoktalari.size()));\n" +
            "                while (dropoff.name.equals(pickup.name)) {\n" +
            "                    dropoff = data.taksiNoktalari.get(random.nextInt(data.taksiNoktalari.size()));\n" +
            "                }\n" +
            "                JobOffer o = new JobOffer();\n" +
            "                o.jobId = UUID.randomUUID().toString().substring(0, 8);\n" +
            "                o.type = \"TAKSI\";\n" +
            "                o.isPlayerRequest = false;\n" +
            "                o.npcTaksiPickup = pickup;\n" +
            "                o.npcTaksiDropoff = dropoff;\n" +
            "                o.expireTime = System.currentTimeMillis() + 60000;\n" +
            "                offers.add(o);\n" +
            "                availableOffers.put(o.jobId, o);\n" +
            "            }\n" +
            "        }\n" +
            "        if (offers.isEmpty()) { p.sendMessage(Text.literal(\"§6[Taksi] §cŞu an hiç uygun iş bulunamadı!\")); return 0; }\n" +
            "        p.sendMessage(Text.literal(\"§6[Taksi] §e--- Mevcut Taksi İşleri ---\"));\n" +
            "        for (int i = 0; i < offers.size(); i++) {\n" +
            "            JobOffer o = offers.get(i);\n" +
            "            MutableText msg = Text.literal(\"§7\" + (i + 1) + \". \");\n" +
            "            if (o.isPlayerRequest) msg.append(Text.literal(\"§b[Oyuncu] §f\" + o.playerRequest.playerName + \" \"));\n" +
            "            else msg.append(Text.literal(\"§a[NPC] §f\" + o.npcTaksiPickup.name + \" Durağından \"));\n" +
            "            MutableText acceptBtn = Text.literal(\"§2§l[KABUL ET]\");\n" +
            "            acceptBtn.setStyle(acceptBtn.getStyle().withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, \"/taksi_accept \" + o.jobId))\n" +
            "                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(\"§aGörevi almak için tıkla!\"))));\n" +
            "            msg.append(acceptBtn);\n" +
            "            p.sendMessage(msg);\n" +
            "        }\n" +
            "        return 1;\n" +
            "    }\n\n";

        int startKurye = content.indexOf("private int takeMission(");
        int endKurye = content.indexOf("private int cancelMission(", startKurye);
        if (startKurye != -1 && endKurye != -1) {
            content = content.substring(0, startKurye) + kuryeAlNew + content.substring(endKurye);
        }

        int startTaksi = content.indexOf("private int takeTaksiMission(");
        int endTaksi = content.indexOf("// === TAKSI EVENTS ===", startTaksi);
        if (startTaksi != -1 && endTaksi != -1) {
            content = content.substring(0, startTaksi) + taksiAlNew + content.substring(endTaksi);
        }

        Files.write(path, content.getBytes("UTF-8"));
        System.out.println("Patcher3 applied successfully.");
    }
}
