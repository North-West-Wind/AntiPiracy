package com.example.examplemod;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.IngameGui;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.LightningBoltEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.network.play.server.STitlePacket;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.Color;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameType;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.stream.Collectors;

@Mod("examplemod")
public class ExampleMod {
    private static boolean client = false, server = false;
    private static boolean isMidNight = false, respawned = false, lastStep = false;
    private static Vector3d position = null;
    private static final Logger LOGGER = LogManager.getLogger();

    public ExampleMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        // some preinit code
        LOGGER.info("HELLO FROM PREINIT");
        LOGGER.info("DIRT BLOCK >> {}", Blocks.DIRT.getRegistryName());
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        // do something that can only be done on the client
        LOGGER.info("Got game settings {}", event.getMinecraftSupplier().get().gameSettings);
    }

    @Mod.EventBusSubscriber
    public static class EventBusHandler {
        @SubscribeEvent
        public static void onWorldTick(TickEvent.WorldTickEvent e) {
            if (e.side.equals(LogicalSide.CLIENT) || isMidNight) return;
            ServerWorld world = (ServerWorld) e.world;
            if (world.getServer().getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).get())
                world.getServer().getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false, world.getServer());
            long time = world.getDayTime() + 20;
            if (time < 18000) world.setDayTime(time);
            else {
                isMidNight = true;
                world.setDayTime(18000);
            }
        }

        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
            if (lastStep && e.side.equals(LogicalSide.SERVER)) {
                PlayerEntity fake = null;
                fake.shouldHeal();
            }
            Vector3d motion = e.player.getMotion();
            if (position == null && e.player.isOnGround()) position = e.player.getPositionVec();
            if (position == null || motion.equals(Vector3d.ZERO)) return;
            e.player.setMotion(0, 0, 0);
            e.player.addPotionEffect(new EffectInstance(Effects.SLOWNESS, 205, 100));
            if (!new BlockPos(e.player.getPositionVec()).equals(new BlockPos(position)))
                e.player.setPositionAndUpdate(position.x, position.y, position.z);
            if (e.side.equals(LogicalSide.SERVER) && !server) {
                server = true;
                sendTitle((ServerPlayerEntity) e.player);
                new Thread(() -> {
                    try {
                        while (!isMidNight && !Thread.currentThread().isInterrupted()) {
                            Thread.sleep(50);
                        }
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                    }
                    run((ServerPlayerEntity) e.player);
                }).start();
            } else if (e.side.equals(LogicalSide.CLIENT) && !client) {
                client = true;
            }
        }

        @SubscribeEvent
        @OnlyIn(Dist.CLIENT)
        public static void onPlayerRespawn(ClientPlayerNetworkEvent.RespawnEvent event) {
            respawned = true;
            new Thread(() -> {
                try {
                    handleRespawn();
                } catch (InterruptedException ignored) { }
            }).start();
        }
    }

    private static void handleRespawn() throws InterruptedException {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        player.sendStatusMessage(new StringTextComponent("Close and uninstall the game now").setStyle(Style.EMPTY.setColor(Color.fromInt(java.awt.Color.RED.getRGB()))), false);
        Thread.sleep(8000);
        player.sendStatusMessage(new StringTextComponent("You just won't listen, aren't you?").setStyle(Style.EMPTY.setColor(Color.fromInt(java.awt.Color.RED.getRGB()))), false);
        Thread.sleep(3000);
        lastStep = true;
    }

    private static void sendTitle(ServerPlayerEntity player) {
        player.connection.sendPacket(new STitlePacket(STitlePacket.Type.TITLE, new StringTextComponent("You cannot escape...").setStyle(Style.EMPTY.setColor(Color.fromInt(java.awt.Color.RED.getRGB()))), 20, 60, 20));
        player.setGameType(GameType.ADVENTURE);
    }

    private static void run(ServerPlayerEntity player) {
        LightningBoltEntity lightning = new LightningBoltEntity(EntityType.LIGHTNING_BOLT, player.getServerWorld());
        lightning.setPositionAndUpdate(position.x, position.y, position.z);
        player.getServerWorld().summonEntity(lightning);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {
        }
        player.extinguish();
        CreeperEntity creeper1 = new CreeperEntity(EntityType.CREEPER, player.getServerWorld());
        creeper1.func_241841_a(player.getServerWorld(), lightning);
        creeper1.setPositionAndUpdate(position.x + 2, position.y, position.z);
        CreeperEntity creeper2 = new CreeperEntity(EntityType.CREEPER, player.getServerWorld());
        creeper2.func_241841_a(player.getServerWorld(), lightning);
        creeper2.setPositionAndUpdate(position.x - 2, position.y, position.z);
        CreeperEntity creeper3 = new CreeperEntity(EntityType.CREEPER, player.getServerWorld());
        creeper3.func_241841_a(player.getServerWorld(), lightning);
        creeper3.setPositionAndUpdate(position.x, position.y, position.z + 2);
        CreeperEntity creeper4 = new CreeperEntity(EntityType.CREEPER, player.getServerWorld());
        creeper4.func_241841_a(player.getServerWorld(), lightning);
        creeper4.setPositionAndUpdate(position.x, position.y, position.z - 2);
        player.getServerWorld().summonEntity(creeper1);
        player.getServerWorld().summonEntity(creeper2);
        player.getServerWorld().summonEntity(creeper3);
        player.getServerWorld().summonEntity(creeper4);
    }
}
