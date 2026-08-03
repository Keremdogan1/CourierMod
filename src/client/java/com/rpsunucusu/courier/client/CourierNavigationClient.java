package com.rpsunucusu.courier.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.registry.Registries;
import net.minecraft.particle.ParticleTypes;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class CourierNavigationClient implements ClientModInitializer {
	public static final String MOD_ID = "gargarayolbulucu";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

	private static List<BlockPos> aktifRota = new ArrayList<>();
	private static BlockPos nihaiHedef = null;
	private static int sayac = 0;
	private static int kontrolZamanlayici = 0;
	public static boolean navigasyonSecimiBekleniyor = false;

	public static void baslatNavigasyon(BlockPos hedef) {
		nihaiHedef = hedef;
		aktifRota.clear();
		if (MinecraftClient.getInstance().player != null) {
			dinamikRotaHesapla();
			MinecraftClient.getInstance().player.sendMessage(Text.literal("Navigasyon hedefe yonlendirildi: " + hedef.getX() + ", " + hedef.getY() + ", " + hedef.getZ()).formatted(Formatting.GREEN), false);
		}
	}

	@Override
	public void onInitializeClient() {
		LOGGER.info("Gargara Yol Bulucu v2.0 (Dinamik) - Oyuncu Group yuklendi!");

		WorldRenderEvents.END.register(CourierNavigationClient::renderAndTick);

		// Sunucudan gelen navigasyon paketini dinle
		ClientPlayNetworking.registerGlobalReceiver(new Identifier("courier", "set_waypoint"), (client, handler, buf, responseSender) -> {
			int x = buf.readInt();
			int y = buf.readInt();
			int z = buf.readInt();

			client.execute(() -> {
				nihaiHedef = new BlockPos(x, y, z);
				aktifRota.clear();
				if (client.player != null) {
					dinamikRotaHesapla();
				}
			});
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("navigasyon")
				.then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
					.then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
						.then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
							.executes(context -> {
								int x = IntegerArgumentType.getInteger(context, "x");
								int y = IntegerArgumentType.getInteger(context, "y");
								int z = IntegerArgumentType.getInteger(context, "z");
								nihaiHedef = new BlockPos(x, y, z);
								aktifRota.clear();
								if (MinecraftClient.getInstance().player != null) {
									dinamikRotaHesapla();
									context.getSource().sendFeedback(Text.literal("Navigasyon hedefe yonlendirildi: " + x + ", " + y + ", " + z).formatted(Formatting.GREEN));
								}
								return 1;
							})
						)
					)
				)
				.then(ClientCommandManager.literal("kapat")
					.executes(context -> {
						nihaiHedef = null;
						aktifRota.clear();
						context.getSource().sendFeedback(Text.literal("Navigasyon kapatildi!").formatted(Formatting.RED));
						return 1;
					})
				)
			);
		});
	}

	// Hem okları çizen hem de yeni chunklar yüklendikçe rotayı uzatan ana döngü
	private static void renderAndTick(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || nihaiHedef == null) return;

		// Varış Noktası Kontrolü
		BlockPos oyuncuPos = client.player.getBlockPos();
		if (Math.abs(oyuncuPos.getX() - nihaiHedef.getX()) < 4 && Math.abs(oyuncuPos.getZ() - nihaiHedef.getZ()) < 4) {
			aktifRota.clear();
			nihaiHedef = null;
			client.player.sendMessage(Text.literal("Hedefe ulastiniz!").formatted(Formatting.GREEN), false);
			return;
		}

		// DİNAMİK ROTAYI UZATMA: Her 2 saniyede bir (40 karede bir) yeni chunkları kontrol et
		kontrolZamanlayici++;
		if (kontrolZamanlayici % 40 == 0 && (aktifRota.isEmpty() || oyuncuPos.getManhattanDistance(aktifRota.get(aktifRota.size() - 1)) < 100)) {
			dinamikRotaHesapla();
		}

		if (aktifRota.isEmpty()) return;

		// --- NAVİGASYON OK RENDER MOTORU ---
		sayac++;
		if (sayac % 5 == 0) {
			for (int i = 0; i < aktifRota.size() - 1; i++) {
				BlockPos guncel = aktifRota.get(i);
				BlockPos sonraki = aktifRota.get(i + 1);

				// Sadece oyuncunun yakınındaki (yüklü) alandaki okları çiz (Performans için)
				if (oyuncuPos.getManhattanDistance(guncel) > 120) continue;

				Vec3d yonVektoru = new Vec3d(sonraki.getX() - guncel.getX(), 0, sonraki.getZ() - guncel.getZ()).normalize();

				client.world.addParticle(
						ParticleTypes.SCRAPE,
						guncel.getX() + 0.5 + yonVektoru.x * 0.2,
						guncel.getY() + 1.2,
						guncel.getZ() + 0.5 + yonVektoru.z * 0.2,
						yonVektoru.x * 0.15, 0, yonVektoru.z * 0.15
				);
			}
		}
	}

	// --- ADIM ADIM A* ROTALAMA MOTORU ---
	private static void dinamikRotaHesapla() {
		MinecraftClient client = MinecraftClient.getInstance();
		World world = client.world;
		if (world == null || client.player == null || nihaiHedef == null) return;

		BlockPos baslangic = client.player.getBlockPos();
		PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fSkor));
		Map<BlockPos, BlockPos> neredenGeldi = new HashMap<>();
		Map<BlockPos, Double> gSkor = new HashMap<>();

		openSet.add(new Node(baslangic, 0, getManhattanMesafe(baslangic, nihaiHedef)));
		gSkor.put(baslangic, 0.0);

		BlockPos ulasilanEnUzakNokta = baslangic;

		while (!openSet.isEmpty()) {
			Node mevcut = openSet.poll();

			// Eğer nihai hedefe ulaştıysak rotayı tam kur ve bitir
			if (mevcut.pos.equals(nihaiHedef)) {
				aktifRota = rotayiYenidenKur(neredenGeldi, mevcut.pos);
				return;
			}

			// Gidilen en uzak noktayı hafızada tut (Chunk sınırını bulmak için)
			if (getManhattanMesafe(mevcut.pos, nihaiHedef) < getManhattanMesafe(ulasilanEnUzakNokta, nihaiHedef)) {
				ulasilanEnUzakNokta = mevcut.pos;
			}

			for (Direction yon : Direction.Type.HORIZONTAL) {
				BlockPos komsuPos = mevcut.pos.offset(yon);

				// Chunk yüklenmiş mi kontrolü (Eğer yüklenmemişse o yöne devam etme)
				if (!world.isChunkLoaded(komsuPos.getX() >> 4, komsuPos.getZ() >> 4)) {
					continue;
				}

				if (!yolMu(world, komsuPos)) {
					if (yolMu(world, komsuPos.up())) {
						komsuPos = komsuPos.up();
					} else if (yolMu(world, komsuPos.down())) {
						komsuPos = komsuPos.down();
					} else {
						continue;
					}
				}

				BlockState state = world.getBlockState(komsuPos);
				String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
				double stepCost = 1.0;

				if (blockId.contains("asphalt_pattern") && state.contains(Properties.HORIZONTAL_FACING)) {
					Direction yolYonu = state.get(Properties.HORIZONTAL_FACING);
					if (yolYonu == yon.getOpposite()) {
						continue; // Ters yone (karsi seride) gecis yasak
					} else if (yolYonu == yon) {
						stepCost = 2.0; // Ok yonunde cizgi uzerinde gitmek
					} else {
						stepCost = 20.0; // Cizgiyi enlemesine kesmek (serit degistirmek / karsi seride cikmak buyuk ceza)
					}
				} else if (state.contains(Properties.HORIZONTAL_FACING)) {
					Direction yolYonu = state.get(Properties.HORIZONTAL_FACING);
					if (yolYonu == yon.getOpposite()) {
						continue; // Genel ters yon yasagi
					}
					stepCost = 1.0;
				}

				double gecici_gSkor = gSkor.getOrDefault(mevcut.pos, Double.MAX_VALUE) + stepCost;

				if (gecici_gSkor < gSkor.getOrDefault(komsuPos, Double.MAX_VALUE)) {
					neredenGeldi.put(komsuPos, mevcut.pos);
					gSkor.put(komsuPos, gecici_gSkor);
					double fSkor = gecici_gSkor + getManhattanMesafe(komsuPos, nihaiHedef);

					BlockPos finalKomsu = komsuPos;
					if (openSet.stream().noneMatch(n -> n.pos.equals(finalKomsu))) {
						openSet.add(new Node(komsuPos, gecici_gSkor, fSkor));
					}
				}
			}
		}

		// Eğer nihai hedefe henüz varamadıysak ama yüklü chunkların son sınırına kadar yol bulabildiysek rotayı oraya kadar çiz
		if (!ulasilanEnUzakNokta.equals(baslangic)) {
			aktifRota = rotayiYenidenKur(neredenGeldi, ulasilanEnUzakNokta);
			client.player.sendMessage(Text.literal("Yol tarifi guncelleniyor...").formatted(Formatting.AQUA), true);
		} else {
			// Eğer bulunulan yerde hiç yol yoksa uyarı ver
			if (aktifRota.isEmpty()) {
				client.player.sendMessage(Text.literal("Buraya yol tarifi olusturulamiyor.").formatted(Formatting.RED), false);
				nihaiHedef = null;
			}
		}
	}

	private static boolean yolMu(World world, BlockPos pos) {
		String blockId = Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString();
		return blockId.startsWith("trafficcraft:") && (blockId.contains("asphalt") || blockId.contains("concrete"));
	}

	private static double getManhattanMesafe(BlockPos a, BlockPos b) {
		return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
	}

	private static List<BlockPos> rotayiYenidenKur(Map<BlockPos, BlockPos> neredenGeldi, BlockPos mevcut) {
		List<BlockPos> toplamYol = new ArrayList<>();
		toplamYol.add(mevcut);
		while (neredenGeldi.containsKey(mevcut)) {
			mevcut = neredenGeldi.get(mevcut);
			toplamYol.add(0, mevcut);
		}
		return toplamYol;
	}

	private static class Node {
		BlockPos pos;
		double gSkor;
		double fSkor;

		Node(BlockPos pos, double gSkor, double fSkor) {
			this.pos = pos;
			this.gSkor = gSkor;
			this.fSkor = fSkor;
		}
	}
}
