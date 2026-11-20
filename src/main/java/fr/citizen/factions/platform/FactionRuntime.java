package fr.citizen.factions.platform;

import com.mojang.authlib.GameProfile;
import fr.citizen.factions.CitizenFactions;
import fr.citizen.factions.core.*;
import fr.citizen.factions.ui.FactionUi;
import fr.citizen.factions.ui.UiEnv;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Etat du mod pendant qu'un serveur tourne : donnees, sauvegarde, interface. Tout s'execute sur le thread serveur. */
public final class FactionRuntime {
    private static volatile FactionRuntime current;

    private final MinecraftServer server;
    private final FactionService service;
    private final FactionStore store;
    private final FactionUi ui;
    private final BondSystem bonds;
    private final Map<UUID, long[]> rate = new HashMap<>();
    private int ticks;
    private long lastSaveError;

    private FactionRuntime(MinecraftServer server) throws IOException {
        this.server = server;
        this.service = new FactionService(System::currentTimeMillis);
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("citizenfactions");
        this.store = new FactionStore(dir);
        boolean existed = store.load(service, msg -> CitizenFactions.LOGGER.warn(msg));
        if (!existed) {
            service.seedDefaults();
            CitizenFactions.LOGGER.info("Citizen Factions : premier démarrage, factions Dragonnier et Chasseur de dragon créées.");
        }
        store.save(service);
        this.ui = new FactionUi(service, new Env());
        this.bonds = new BondSystem(this);
        CitizenFactions.LOGGER.info("Citizen Factions : {} faction(s), {} membre(s), {} dragon(s) chargés depuis {}",
                service.factions().size(), countMembers(), service.dragons().size(), store.file());
    }

    public static FactionRuntime current() { return current; }

    public static void start(MinecraftServer server) {
        try {
            current = new FactionRuntime(server);
        } catch (IOException | RuntimeException e) {
            current = null;
            CitizenFactions.LOGGER.error("Citizen Factions : impossible de charger les données. Le mod est désactivé pour éviter toute perte.", e);
        }
    }

    public static void stop() {
        FactionRuntime rt = current;
        current = null;
        if (rt != null) rt.saveNow();
    }

    public MinecraftServer server() { return server; }
    public FactionService service() { return service; }
    public FactionUi ui() { return ui; }
    public BondSystem bonds() { return bonds; }

    private int countMembers() {
        int n = 0;
        for (Faction f : service.factions()) n += service.members(f.id()).size();
        return n;
    }

    public void tick() {
        ticks++;
        for (UUID id : PrimordialState.SERVER.keySet()) {
            net.minecraft.world.entity.Entity e = bonds.find(id);
            if (e != null && e.isAlive()) Primordial.tickBreath(e);
        }
        if (ticks % 20 == 0) bonds.tickSecond();
        if (ticks % 200 == 0) bonds.tickSlow();
        if (ticks % 40 == 0) GradeAuras.refresh(this);
        if (ticks % 100 == 0 && service.dirty()) saveNow();
        if (ticks % 1200 == 0) rate.clear();
    }

    public void saveNow() {
        try {
            store.save(service);
        } catch (IOException | RuntimeException e) {
            long now = System.currentTimeMillis();
            if (now - lastSaveError > 60_000) {
                lastSaveError = now;
                CitizenFactions.LOGGER.error("Citizen Factions : échec de la sauvegarde (nouvel essai automatique).", e);
            }
        }
    }

    public void onLogin(ServerPlayer p) {
        service.seen(p.getUUID(), p.getGameProfile().getName());
    }

    public void onLogout(ServerPlayer p) {
        service.seen(p.getUUID(), p.getGameProfile().getName());
        bonds.onLogout(p);
        GradeAuras.remove(p);
    }

    public void open(ServerPlayer p, String view, Map<String, String> params) {
        Map<String, String> args = new HashMap<>(params);
        args.put("to", view);
        Network.send(p, ui.handle(Admins.actor(p), "open", args));
    }

    /** Au plus 8 actions d'interface par seconde et par joueur. */
    boolean rateLimit(ServerPlayer p) {
        long now = System.currentTimeMillis();
        long[] r = rate.computeIfAbsent(p.getUUID(), k -> new long[]{now, 0});
        if (now - r[0] > 1000) { r[0] = now; r[1] = 0; }
        return ++r[1] <= 8;
    }

    public void message(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal("[Factions] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.YELLOW)));
    }

    /** Retrouve un joueur connecte ou deja venu (cache des profils du serveur), par pseudo. */
    public UiEnv.PlayerRef resolve(String name) {
        if (name == null || name.isBlank()) return null;
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return new UiEnv.PlayerRef(online.getUUID(), online.getGameProfile().getName());
        Member m = service.findMemberByName(name);
        if (m != null) return new UiEnv.PlayerRef(m.uuid(), m.name());
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            Optional<GameProfile> gp = cache.get(name);
            if (gp.isPresent() && gp.get().getId() != null) return new UiEnv.PlayerRef(gp.get().getId(), gp.get().getName());
        }
        return null;
    }

    public String nameOf(UUID uuid) {
        if (uuid == null) return "—";
        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
        if (p != null) return p.getGameProfile().getName();
        Member m = service.member(uuid);
        if (m != null) return m.name();
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            Optional<GameProfile> gp = cache.get(uuid);
            if (gp.isPresent()) return gp.get().getName();
        }
        return uuid.toString().substring(0, 8);
    }

    private final class Env implements UiEnv {
        @Override public boolean online(UUID player) { return server.getPlayerList().getPlayer(player) != null; }
        @Override public PlayerRef resolve(String name) { return FactionRuntime.this.resolve(name); }
        @Override public BondSettings bond() { return Config.bond(); }

        @Override public void notify(UUID player, String message) {
            ServerPlayer p = server.getPlayerList().getPlayer(player);
            if (p != null) message(p, message);
        }

        @Override public void notifyFaction(String factionId, FactionPermission perm, String message) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Member m = service.member(p.getUUID());
                if (m != null && m.factionId().equals(factionId) && service.has(p.getUUID(), perm)) message(p, message);
            }
        }
    }
}
