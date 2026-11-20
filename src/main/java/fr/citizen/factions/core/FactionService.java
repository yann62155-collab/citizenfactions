package fr.citizen.factions.core;

import java.text.Normalizer;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * Toute la logique des factions, independante de Minecraft (testable seule).
 * Les droits d'ADMINISTRATEUR sont verifies par l'appelant (commandes / interface) ; les droits lies aux GRADES
 * sont verifies ici. Toute modification marque les donnees comme "a sauvegarder".
 */
public final class FactionService {
    public static final int MAX_FACTIONS = 64;
    public static final int MAX_GRADES = 20;
    public static final int MAX_EMBLEMS = 128;

    final Map<String, Faction> factions = new LinkedHashMap<>();
    final Map<String, Emblem> emblems = new LinkedHashMap<>();
    final Map<UUID, Member> members = new LinkedHashMap<>();
    final List<JoinRequest> requests = new ArrayList<>();
    final Map<UUID, DragonRecord> dragons = new LinkedHashMap<>();

    private final LongSupplier clock;
    private boolean dirty;

    public FactionService(LongSupplier clock) { this.clock = clock; }

    public long now() { return clock.getAsLong(); }
    public boolean dirty() { return dirty; }
    public void markDirty() { dirty = true; }
    public void clearDirty() { dirty = false; }

    // ================================================================== valeurs par defaut

    /** Cree les factions, grades et emblemes d'origine (appele quand aucune sauvegarde n'existe). */
    public void seedDefaults() {
        if (!emblems.containsKey("ecaille"))
            emblems.put("ecaille", new Emblem("ecaille", "Écaille de feu", Emblem.Shape.ECU, Emblem.Pattern.CHEVRON, 0x8B1A1A, 0xD4AF37, "D"));
        if (!emblems.containsKey("lame"))
            emblems.put("lame", new Emblem("lame", "Lame noire", Emblem.Shape.ECU, Emblem.Pattern.CROIX, 0x1A1A1A, 0xB0B0B0, "X"));
        if (!factions.containsKey("dragonnier"))
            createFaction("dragonnier", "Dragonnier", DragonRule.NON_CORROMPUS).emblemId("ecaille");
        if (!factions.containsKey("chasseur"))
            createFaction("chasseur", "Chasseur de dragon", DragonRule.CORROMPUS).emblemId("lame");
        factions.get("dragonnier").description("Les Dragonniers montent tous les dragons, sauf les dragons corrompus.");
        factions.get("chasseur").description("Les Chasseurs de dragon ne peuvent monter que les dragons corrompus.");
        dirty = true;
    }

    private static void defaultGrades(Faction f) {
        List<Grade> g = f.gradesMutable();
        g.add(new Grade("primodiau", "Primodiau", EnumSet.allOf(FactionPermission.class)));
        g.add(new Grade("alpha", "Alpha", EnumSet.of(FactionPermission.VOIR_MEMBRES, FactionPermission.VOIR_DRAGONS,
                FactionPermission.GERER_DEMANDES, FactionPermission.PROMOUVOIR)));
        g.add(new Grade("confirme", "Confirmé", EnumSet.of(FactionPermission.VOIR_MEMBRES, FactionPermission.VOIR_DRAGONS)));
        g.add(new Grade("petit", "Petit", EnumSet.of(FactionPermission.VOIR_MEMBRES)));
    }

    // ================================================================== lecture

    public Collection<Faction> factions() { return Collections.unmodifiableCollection(factions.values()); }
    public Collection<Emblem> emblems() { return Collections.unmodifiableCollection(emblems.values()); }
    public Faction faction(String id) { return id == null ? null : factions.get(id.toLowerCase(Locale.ROOT)); }
    public Emblem emblem(String id) { return id == null || id.isBlank() ? null : emblems.get(id.toLowerCase(Locale.ROOT)); }
    public Member member(UUID uuid) { return members.get(uuid); }
    public Collection<DragonRecord> dragons() { return Collections.unmodifiableCollection(dragons.values()); }
    public DragonRecord dragon(UUID uuid) { return dragons.get(uuid); }

    public Faction requireFaction(String id) {
        Faction f = faction(id);
        if (f == null) throw new FactionException("Faction introuvable : " + id);
        return f;
    }

    public Emblem requireEmblem(String id) {
        Emblem e = emblem(id);
        if (e == null) throw new FactionException("Emblème introuvable : " + id);
        return e;
    }

    /** Faction du joueur, ou null s'il n'en a pas. */
    public Faction factionOf(UUID player) {
        Member m = members.get(player);
        return m == null ? null : factions.get(m.factionId());
    }

    public Grade gradeOf(UUID player) {
        Member m = members.get(player);
        Faction f = factionOf(player);
        return m == null || f == null ? null : f.grade(m.gradeId());
    }

    public boolean has(UUID player, FactionPermission p) {
        Grade g = gradeOf(player);
        return g != null && g.has(p);
    }

    /** Membres d'une faction, du grade le plus haut au plus bas puis par nom. */
    public List<Member> members(String factionId) {
        Faction f = requireFaction(factionId);
        List<Member> out = new ArrayList<>();
        for (Member m : members.values()) if (m.factionId().equals(f.id())) out.add(m);
        out.sort(Comparator.comparingInt((Member m) -> { int r = f.rank(m.gradeId()); return r < 0 ? 999 : r; })
                .thenComparing(m -> m.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    public Member findMemberByName(String name) {
        for (Member m : members.values()) if (m.name().equalsIgnoreCase(name)) return m;
        return null;
    }

    public List<JoinRequest> requests(String factionId) {
        List<JoinRequest> out = new ArrayList<>();
        for (JoinRequest r : requests) if (r.factionId().equals(factionId)) out.add(r);
        return out;
    }

    public JoinRequest requestOf(UUID player) {
        for (JoinRequest r : requests) if (r.player().equals(player)) return r;
        return null;
    }

    /** Dragons dont le joueur est proprietaire ou avec lesquels il a un lien. */
    public List<DragonRecord> dragonsOf(UUID player) {
        List<DragonRecord> out = new ArrayList<>();
        for (DragonRecord d : dragons.values())
            if (player.equals(d.owner()) || d.bonds().containsKey(player)) out.add(d);
        out.sort(Comparator.comparing(DragonRecord::displayName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // ================================================================== administration des factions

    public Faction createFaction(String id, String name, DragonRule rule) {
        String fid = checkId(id, "faction");
        if (factions.containsKey(fid)) throw new FactionException("Une faction « " + fid + " » existe déjà.");
        if (factions.size() >= MAX_FACTIONS) throw new FactionException("Nombre maximal de factions atteint.");
        Faction f = new Faction(fid, checkName(name, 32), "", "", rule == null ? DragonRule.NON_CORROMPUS : rule, now());
        defaultGrades(f);
        factions.put(fid, f);
        dirty = true;
        return f;
    }

    public void deleteFaction(String id) {
        Faction f = requireFaction(id);
        factions.remove(f.id());
        members.values().removeIf(m -> m.factionId().equals(f.id()));
        requests.removeIf(r -> r.factionId().equals(f.id()));
        dirty = true;
    }

    public void renameFaction(String id, String name) { requireFaction(id).name(checkName(name, 32)); dirty = true; }

    public void describeFaction(String id, String description) {
        String d = description == null ? "" : description.strip();
        if (d.length() > 240) throw new FactionException("Description trop longue (240 caractères maximum).");
        requireFaction(id).description(d);
        dirty = true;
    }

    public void setRule(String id, DragonRule rule) { requireFaction(id).rule(rule); dirty = true; }

    public void assignEmblem(String factionId, String emblemId) {
        Faction f = requireFaction(factionId);
        if (emblemId == null || emblemId.isBlank()) f.emblemId("");
        else f.emblemId(requireEmblem(emblemId).id());
        dirty = true;
    }

    // ================================================================== administration des membres

    public Member addMember(String factionId, UUID player, String name, String gradeId) {
        Faction f = requireFaction(factionId);
        Member m = members.get(player);
        if (m != null) {
            Faction cur = factions.get(m.factionId());
            throw new FactionException(m.name() + " est déjà membre de « " + (cur == null ? m.factionId() : cur.name())
                    + " ». Un joueur ne peut appartenir qu'à une seule faction : utilisez le transfert.");
        }
        Grade g = gradeId == null || gradeId.isBlank() ? f.lowestGrade() : f.grade(gradeId);
        if (g == null) throw new FactionException("Grade introuvable dans " + f.name() + " : " + gradeId);
        long now = now();
        m = new Member(player, name, f.id(), g.id(), now, now);
        members.put(player, m);
        requests.removeIf(r -> r.player().equals(player));
        dirty = true;
        return m;
    }

    public Member removeMember(UUID player) {
        Member m = members.remove(player);
        if (m == null) throw new FactionException("Ce joueur n'appartient à aucune faction.");
        dirty = true;
        return m;
    }

    public void transfer(UUID player, String factionId) {
        Member m = members.get(player);
        if (m == null) throw new FactionException("Ce joueur n'appartient à aucune faction : ajoutez-le directement.");
        Faction f = requireFaction(factionId);
        if (m.factionId().equals(f.id())) throw new FactionException(m.name() + " est déjà dans " + f.name() + ".");
        Grade g = f.lowestGrade();
        if (g == null) throw new FactionException(f.name() + " n'a aucun grade.");
        m.faction(f.id(), g.id(), now());
        dirty = true;
    }

    public void setGrade(UUID player, String gradeId) {
        Member m = members.get(player);
        if (m == null) throw new FactionException("Ce joueur n'appartient à aucune faction.");
        Faction f = requireFaction(m.factionId());
        Grade g = f.grade(gradeId);
        if (g == null) throw new FactionException("Grade introuvable dans " + f.name() + " : " + gradeId);
        m.grade(g.id());
        dirty = true;
    }

    /** Met a jour le pseudo connu et la derniere connexion (si le joueur est membre). */
    public void seen(UUID player, String name) {
        Member m = members.get(player);
        if (m != null) { m.name(name); m.lastSeen(now()); dirty = true; }
        for (int i = 0; i < requests.size(); i++) {
            JoinRequest r = requests.get(i);
            if (r.player().equals(player) && !r.playerName().equals(name)) requests.set(i, new JoinRequest(player, name, r.factionId(), r.at()));
        }
    }

    // ================================================================== grades

    public Grade createGrade(String factionId, String name) {
        Faction f = requireFaction(factionId);
        if (f.grades().size() >= MAX_GRADES) throw new FactionException("Nombre maximal de grades atteint.");
        String n = checkName(name, 24);
        String base = slug(n);
        if (base.isEmpty()) base = "grade";
        String id = base;
        for (int i = 2; f.grade(id) != null; i++) id = base + i;
        Grade g = new Grade(id, n, EnumSet.of(FactionPermission.VOIR_MEMBRES));
        f.gradesMutable().add(g);
        dirty = true;
        return g;
    }

    public void renameGrade(String factionId, String gradeId, String name) {
        Grade g = requireGrade(requireFaction(factionId), gradeId);
        g.name(checkName(name, 24));
        dirty = true;
    }

    public void deleteGrade(String factionId, String gradeId) {
        Faction f = requireFaction(factionId);
        Grade g = requireGrade(f, gradeId);
        if (f.grades().size() <= 1) throw new FactionException("Une faction doit garder au moins un grade.");
        int idx = f.rank(g.id());
        f.gradesMutable().remove(idx);
        Grade fallback = f.grades().get(Math.min(idx, f.grades().size() - 1));
        for (Member m : members.values())
            if (m.factionId().equals(f.id()) && m.gradeId().equals(g.id())) m.grade(fallback.id());
        dirty = true;
    }

    /** Monte (up = true) ou descend un grade d'un cran dans la hierarchie. */
    public void moveGrade(String factionId, String gradeId, boolean up) {
        Faction f = requireFaction(factionId);
        Grade g = requireGrade(f, gradeId);
        int i = f.rank(g.id());
        int j = up ? i - 1 : i + 1;
        if (j < 0 || j >= f.grades().size()) return;
        Collections.swap(f.gradesMutable(), i, j);
        dirty = true;
    }

    public void togglePermission(String factionId, String gradeId, FactionPermission p) {
        requireGrade(requireFaction(factionId), gradeId).toggle(p);
        dirty = true;
    }

    public void setPermission(String factionId, String gradeId, FactionPermission p, boolean on) {
        requireGrade(requireFaction(factionId), gradeId).set(p, on);
        dirty = true;
    }

    public Grade requireGrade(Faction f, String gradeId) {
        Grade g = f.grade(gradeId);
        if (g == null) throw new FactionException("Grade introuvable dans " + f.name() + " : " + gradeId);
        return g;
    }

    // ================================================================== emblemes

    public Emblem createEmblem(String id, String name) {
        String eid = checkId(id, "emblème");
        if (emblems.containsKey(eid)) throw new FactionException("Un emblème « " + eid + " » existe déjà.");
        if (emblems.size() >= MAX_EMBLEMS) throw new FactionException("Nombre maximal d'emblèmes atteint.");
        Emblem e = new Emblem(eid, checkName(name, 32), Emblem.Shape.ECU, Emblem.Pattern.UNI, 0x5C0F0F, 0xD4AF37, "");
        emblems.put(eid, e);
        dirty = true;
        return e;
    }

    public void editEmblem(String id, String name, String shape, String pattern, String primary, String secondary, String symbol) {
        Emblem e = requireEmblem(id);
        if (name != null) e.name(checkName(name, 32));
        if (shape != null) e.shape(parseEnum(Emblem.Shape.class, shape, "forme"));
        if (pattern != null) e.pattern(parseEnum(Emblem.Pattern.class, pattern, "motif"));
        if (primary != null) e.primary(Emblem.parseColor(primary));
        if (secondary != null) e.secondary(Emblem.parseColor(secondary));
        if (symbol != null) e.symbol(symbol);
        dirty = true;
    }

    public void cycleEmblemShape(String id) { Emblem e = requireEmblem(id); e.shape(e.shape().next()); dirty = true; }
    public void cycleEmblemPattern(String id) { Emblem e = requireEmblem(id); e.pattern(e.pattern().next()); dirty = true; }

    public void deleteEmblem(String id) {
        Emblem e = requireEmblem(id);
        emblems.remove(e.id());
        for (Faction f : factions.values()) if (f.emblemId().equals(e.id())) f.emblemId("");
        dirty = true;
    }

    // ================================================================== demandes et gestion par les grades

    public JoinRequest requestJoin(UUID player, String name, String factionId) {
        if (members.containsKey(player)) throw new FactionException("Vous appartenez déjà à une faction.");
        Faction f = requireFaction(factionId);
        requests.removeIf(r -> r.player().equals(player));
        JoinRequest r = new JoinRequest(player, name, f.id(), now());
        requests.add(r);
        dirty = true;
        return r;
    }

    public void cancelRequest(UUID player) {
        if (requests.removeIf(r -> r.player().equals(player))) dirty = true;
    }

    public Member acceptRequest(UUID actor, boolean admin, UUID target) {
        JoinRequest r = requestOf(target);
        if (r == null) throw new FactionException("Aucune demande en attente pour ce joueur.");
        requireSameFactionPerm(actor, admin, r.factionId(), FactionPermission.GERER_DEMANDES);
        return addMember(r.factionId(), r.player(), r.playerName(), null);
    }

    public void refuseRequest(UUID actor, boolean admin, UUID target) {
        JoinRequest r = requestOf(target);
        if (r == null) throw new FactionException("Aucune demande en attente pour ce joueur.");
        requireSameFactionPerm(actor, admin, r.factionId(), FactionPermission.GERER_DEMANDES);
        requests.remove(r);
        dirty = true;
    }

    public Member kickBy(UUID actor, boolean admin, UUID target) {
        Member t = members.get(target);
        if (t == null) throw new FactionException("Ce joueur n'appartient à aucune faction.");
        if (!admin) {
            requireSameFactionPerm(actor, false, t.factionId(), FactionPermission.EXCLURE);
            requireAbove(actor, t);
        }
        return removeMember(target);
    }

    public void setGradeBy(UUID actor, boolean admin, UUID target, String gradeId) {
        Member t = members.get(target);
        if (t == null) throw new FactionException("Ce joueur n'appartient à aucune faction.");
        if (!admin) {
            requireSameFactionPerm(actor, false, t.factionId(), FactionPermission.PROMOUVOIR);
            requireAbove(actor, t);
            Faction f = requireFaction(t.factionId());
            int newRank = f.rank(gradeId);
            int myRank = f.rank(members.get(actor).gradeId());
            if (newRank < 0) throw new FactionException("Grade introuvable : " + gradeId);
            if (newRank <= myRank) throw new FactionException("Vous ne pouvez attribuer qu'un grade inférieur au vôtre.");
        }
        setGrade(target, gradeId);
    }

    /** Vrai si l'acteur peut gerer (exclure / changer le grade) ce membre. */
    public boolean canManage(UUID actor, boolean admin, Member target, FactionPermission p) {
        if (admin) return true;
        Member a = members.get(actor);
        if (a == null || target == null || a.uuid().equals(target.uuid()) || !a.factionId().equals(target.factionId())) return false;
        Faction f = factions.get(a.factionId());
        if (f == null || !has(actor, p)) return false;
        return f.rank(a.gradeId()) >= 0 && f.rank(a.gradeId()) < f.rank(target.gradeId());
    }

    private void requireSameFactionPerm(UUID actor, boolean admin, String factionId, FactionPermission p) {
        if (admin) return;
        Member a = members.get(actor);
        if (a == null || !a.factionId().equals(factionId)) throw new FactionException("Ce joueur ne fait pas partie de votre faction.");
        if (!has(actor, p)) throw new FactionException("Votre grade ne vous permet pas cette action (" + p.label() + ").");
    }

    private void requireAbove(UUID actor, Member target) {
        if (actor.equals(target.uuid())) throw new FactionException("Vous ne pouvez pas vous gérer vous-même.");
        Faction f = requireFaction(target.factionId());
        int a = f.rank(members.get(actor).gradeId());
        int t = f.rank(target.gradeId());
        if (a < 0 || a >= t) throw new FactionException("Ce membre a un grade égal ou supérieur au vôtre.");
    }

    // ================================================================== dragons

    public DragonRecord dragonRecord(UUID dragon) {
        DragonRecord d = dragons.get(dragon);
        if (d == null) { d = new DragonRecord(dragon); dragons.put(dragon, d); dirty = true; }
        return d;
    }

    public void forgetDragon(UUID dragon) { if (dragons.remove(dragon) != null) dirty = true; }

    // ================================================================== validation

    private static String checkId(String id, String what) {
        String s = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        if (!s.matches("[a-z0-9_]{2,24}"))
            throw new FactionException("Identifiant de " + what + " invalide : 2 à 24 caractères, lettres minuscules, chiffres ou _.");
        return s;
    }

    private static String checkName(String name, int max) {
        String s = name == null ? "" : name.strip();
        if (s.isEmpty()) throw new FactionException("Le nom ne peut pas être vide.");
        if (s.length() > max) throw new FactionException("Nom trop long (" + max + " caractères maximum).");
        if (s.chars().anyMatch(c -> c < 0x20 || c == '§')) throw new FactionException("Le nom contient des caractères interdits.");
        return s;
    }

    static String slug(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        n = n.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return n.length() > 20 ? n.substring(0, 20) : n;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> c, String v, String what) {
        for (E e : c.getEnumConstants()) if (e.name().equalsIgnoreCase(v.strip())) return e;
        throw new FactionException(what + " inconnu(e) : " + v);
    }
}
