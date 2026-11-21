package fr.citizen.factions.core;

import fr.citizen.factions.ui.Json;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Sauvegarde JSON sur disque, protegee contre les pertes :
 * ecriture dans un fichier temporaire, synchronisation disque, remplacement atomique,
 * et conservation de la version precedente (.bak) utilisee automatiquement si le fichier principal est illisible.
 */
public final class FactionStore {
    public static final int FORMAT = 2;
    private static final int MAX_FILE = 64 * 1024 * 1024;

    private final Path file;
    private final Path backup;
    private final Path temp;

    public FactionStore(Path directory) {
        this.file = directory.resolve("factions.json");
        this.backup = directory.resolve("factions.json.bak");
        this.temp = directory.resolve("factions.json.tmp");
    }

    public Path file() { return file; }

    /** Charge les donnees. @return false si aucune sauvegarde n'existait (premier demarrage). */
    public boolean load(FactionService s, java.util.function.Consumer<String> warn) throws IOException {
        if (Files.exists(file)) {
            try {
                read(file, s);
                return true;
            } catch (RuntimeException | IOException e) {
                warn.accept("Sauvegarde " + file + " illisible (" + e.getMessage() + "), tentative avec la copie de secours.");
                Files.copy(file, file.resolveSibling("factions.json.corrompu-" + System.currentTimeMillis()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if (Files.exists(backup)) {
            read(backup, s);
            warn.accept("Données restaurées depuis la copie de secours " + backup + ".");
            s.markDirty();
            return true;
        }
        return false;
    }

    private static void read(Path p, FactionService s) throws IOException {
        if (Files.size(p) > MAX_FILE) throw new IOException("fichier trop volumineux");
        String json = Files.readString(p, StandardCharsets.UTF_8);
        FactionService fresh = new FactionService(s::now);
        fromJson(json, fresh);
        // tout s'est bien lu : on remplace le contenu
        s.factions.clear(); s.factions.putAll(fresh.factions);
        s.emblems.clear(); s.emblems.putAll(fresh.emblems);
        s.members.clear(); s.members.putAll(fresh.members);
        s.requests.clear(); s.requests.addAll(fresh.requests);
        s.dragons.clear(); s.dragons.putAll(fresh.dragons);
    }

    public void save(FactionService s) throws IOException {
        Files.createDirectories(file.getParent());
        byte[] data = toJson(s).getBytes(StandardCharsets.UTF_8);
        try (FileChannel ch = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(data);
            while (buf.hasRemaining()) ch.write(buf);
            ch.force(true);
        }
        if (Files.exists(file)) Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        s.clearDirty();
    }

    // ================================================================== JSON

    public static String toJson(FactionService s) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        List<Object> em = new ArrayList<>();
        for (Emblem e : s.emblems.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id()); m.put("name", e.name()); m.put("shape", e.shape().name()); m.put("pattern", e.pattern().name());
            m.put("primary", Emblem.hex(e.primary())); m.put("secondary", Emblem.hex(e.secondary())); m.put("symbol", e.symbol());
            em.add(m);
        }
        root.put("emblems", em);
        List<Object> fs = new ArrayList<>();
        for (Faction f : s.factions.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.id()); m.put("name", f.name()); m.put("description", f.description());
            m.put("emblem", f.emblemId()); m.put("rule", f.rule().name()); m.put("created", f.createdAt());
            List<Object> gs = new ArrayList<>();
            for (Grade g : f.grades()) {
                Map<String, Object> gm = new LinkedHashMap<>();
                gm.put("id", g.id()); gm.put("name", g.name());
                List<Object> ps = new ArrayList<>();
                for (FactionPermission p : g.perms()) ps.add(p.name());
                gm.put("perms", ps);
                gs.add(gm);
            }
            m.put("grades", gs);
            fs.add(m);
        }
        root.put("factions", fs);
        List<Object> ms = new ArrayList<>();
        for (Member mb : s.members.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", mb.uuid().toString()); m.put("name", mb.name()); m.put("faction", mb.factionId());
            m.put("grade", mb.gradeId()); m.put("joined", mb.joinedAt()); m.put("seen", mb.lastSeen());
            ms.add(m);
        }
        root.put("members", ms);
        List<Object> rq = new ArrayList<>();
        for (JoinRequest r : s.requests) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", r.player().toString()); m.put("name", r.playerName()); m.put("faction", r.factionId()); m.put("at", r.at());
            rq.add(m);
        }
        root.put("requests", rq);
        List<Object> ds = new ArrayList<>();
        for (DragonRecord d : s.dragons.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", d.uuid().toString()); m.put("species", d.species()); m.put("variant", d.variant());
            m.put("corrupted", d.corrupted()); m.put("name", d.customName()); m.put("owner", d.owner() == null ? "" : d.owner().toString());
            m.put("dim", d.dimension()); m.put("x", d.x()); m.put("y", d.y()); m.put("z", d.z()); m.put("stage", d.stage()); m.put("seen", d.lastSeen());
            m.put("primordial", d.primordial());
            m.put("downed", d.downed());
            List<Object> bs = new ArrayList<>();
            for (Map.Entry<UUID, Bond> e : d.bonds().entrySet()) {
                Map<String, Object> bm = new LinkedHashMap<>();
                bm.put("player", e.getKey().toString()); bm.put("value", e.getValue().value());
                bm.put("ride", e.getValue().lastRide()); bm.put("upd", e.getValue().lastUpdate());
                bs.add(bm);
            }
            m.put("bonds", bs);
            ds.add(m);
        }
        root.put("dragons", ds);
        return Json.write(root);
    }

    @SuppressWarnings("unchecked")
    public static void fromJson(String json, FactionService s) {
        Map<String, Object> root = (Map<String, Object>) Json.parse(json, MAX_FILE);
        for (Map<String, Object> m : list(root, "emblems")) {
            Emblem e = new Emblem(str(m, "id"), str(m, "name"), Emblem.Shape.valueOf(str(m, "shape")), Emblem.Pattern.valueOf(str(m, "pattern")),
                    Emblem.parseColor(str(m, "primary")), Emblem.parseColor(str(m, "secondary")), str(m, "symbol"));
            s.emblems.put(e.id(), e);
        }
        for (Map<String, Object> m : list(root, "factions")) {
            Faction f = new Faction(str(m, "id"), str(m, "name"), str(m, "description"), str(m, "emblem"),
                    DragonRule.valueOf(str(m, "rule")), num(m, "created"));
            for (Map<String, Object> gm : list(m, "grades")) {
                EnumSet<FactionPermission> ps = EnumSet.noneOf(FactionPermission.class);
                for (Object p : (List<Object>) gm.getOrDefault("perms", List.of())) {
                    try { ps.add(FactionPermission.valueOf(String.valueOf(p))); } catch (IllegalArgumentException ignored) { }
                }
                f.gradesMutable().add(new Grade(str(gm, "id"), str(gm, "name"), ps));
            }
            // format 1 -> 2 : le grade le plus haut recoit le droit de monter le dragon primordial
            if (num(root, "format") < 2 && !f.gradesMutable().isEmpty())
                f.gradesMutable().get(0).set(FactionPermission.MONTER_PRIMORDIAL, true);
            s.factions.put(f.id(), f);
        }
        for (Map<String, Object> m : list(root, "members")) {
            String fid = str(m, "faction");
            Faction f = s.factions.get(fid);
            if (f == null) continue; // faction supprimee : le joueur redevient sans faction
            String gid = str(m, "grade");
            if (f.grade(gid) == null && f.lowestGrade() != null) gid = f.lowestGrade().id();
            Member mb = new Member(UUID.fromString(str(m, "uuid")), str(m, "name"), fid, gid, num(m, "joined"), num(m, "seen"));
            s.members.put(mb.uuid(), mb);
        }
        for (Map<String, Object> m : list(root, "requests")) {
            if (!s.factions.containsKey(str(m, "faction"))) continue;
            s.requests.add(new JoinRequest(UUID.fromString(str(m, "uuid")), str(m, "name"), str(m, "faction"), num(m, "at")));
        }
        for (Map<String, Object> m : list(root, "dragons")) {
            DragonRecord d = new DragonRecord(UUID.fromString(str(m, "uuid")));
            String owner = str(m, "owner");
            d.describe(str(m, "species"), str(m, "variant"), Boolean.TRUE.equals(m.get("corrupted")), str(m, "name"),
                    owner.isBlank() ? null : UUID.fromString(owner), (int) num(m, "stage"));
            d.primordial(str(m, "primordial"));
            d.downed(Boolean.TRUE.equals(m.get("downed")));
            d.seen(str(m, "dim"), (int) num(m, "x"), (int) num(m, "y"), (int) num(m, "z"), num(m, "seen"));
            for (Map<String, Object> bm : list(m, "bonds")) {
                Object v = bm.get("value");
                d.bonds().put(UUID.fromString(str(bm, "player")),
                        new Bond(v instanceof Number n ? n.doubleValue() : 0, num(bm, "ride"), num(bm, "upd")));
            }
            s.dragons.put(d.uuid(), d);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> m, String k) {
        Object o = m.get(k);
        if (!(o instanceof List<?> l)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object x : l) if (x instanceof Map<?, ?>) out.add((Map<String, Object>) x);
        return out;
    }

    private static String str(Map<String, Object> m, String k) {
        Object o = m.get(k);
        return o == null ? "" : o.toString();
    }

    private static long num(Map<String, Object> m, String k) {
        Object o = m.get(k);
        return o instanceof Number n ? n.longValue() : 0L;
    }
}
