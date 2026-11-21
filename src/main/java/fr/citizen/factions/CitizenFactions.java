package fr.citizen.factions;

import com.mojang.logging.LogUtils;
import fr.citizen.factions.platform.ClientConfig;
import fr.citizen.factions.platform.Commands;
import fr.citizen.factions.platform.Config;
import fr.citizen.factions.platform.DragonGuard;
import fr.citizen.factions.platform.FactionRuntime;
import fr.citizen.factions.platform.Network;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(CitizenFactions.MOD_ID)
public class CitizenFactions {
    public static final String MOD_ID = "citizenfactions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CitizenFactions(IEventBus modBus, ModContainer container) {
        modBus.addListener(Network::register);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> Commands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent e) -> {
            if (!ModList.get().isLoaded("iceandfire"))
                LOGGER.warn("Citizen Factions : le mod Ice and Fire (iceandfire) n'est pas détecté. Les factions fonctionnent, mais aucun dragon ne sera reconnu.");
            FactionRuntime.start(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> FactionRuntime.stop());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            FactionRuntime rt = FactionRuntime.current();
            if (rt != null) rt.tick();
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            FactionRuntime rt = FactionRuntime.current();
            if (rt != null && e.getEntity() instanceof ServerPlayer p) rt.onLogin(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            FactionRuntime rt = FactionRuntime.current();
            if (rt != null && e.getEntity() instanceof ServerPlayer p) rt.onLogout(p);
        });

        // Regles des dragons : priorite haute pour passer avant Ice and Fire
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, DragonGuard::onInteract);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, DragonGuard::onInteractSpecific);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, DragonGuard::onMount);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, DragonGuard::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, DragonGuard::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(DragonGuard::onJoin);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, fr.citizen.factions.platform.DragonDowned::onDeath);
        NeoForge.EVENT_BUS.addListener(DragonGuard::onDeath);
        NeoForge.EVENT_BUS.addListener(DragonGuard::onLeave);
        NeoForge.EVENT_BUS.addListener(DragonGuard::onStartTracking);
        NeoForge.EVENT_BUS.addListener(DragonGuard::onIncomingDamage);
        // en dernier : les degats finaux (apres armure et autres mods) sont rapportes aux PV du grade
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre e) -> {
            FactionRuntime rt = FactionRuntime.current();
            if (rt != null && !e.getEntity().level().isClientSide()) rt.health().onDamage(e);
        });

        if (FMLEnvironment.dist == Dist.CLIENT) fr.citizen.factions.client.ClientSetup.init(modBus);
    }
}
