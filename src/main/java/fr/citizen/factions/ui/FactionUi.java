package fr.citizen.factions.ui;

import fr.citizen.factions.core.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static fr.citizen.factions.ui.ViewBuilder.confirm;
import static fr.citizen.factions.ui.ViewBuilder.nav;

/**
 * Tous les ecrans des factions et le traitement des clics. Chaque action est reverifiee ici
 * (statut administrateur, grade, appartenance) : un client modifie ne peut rien faire de plus qu'un client normal.
 */
public final class FactionUi {
    public static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Paris"));

    private final FactionService s;
    private final UiEnv env;

    public FactionUi(FactionService service, UiEnv env) {
        this.s = service;
        this.env = env;
    }

    // ================================================================== point d'entree

    public View handle(Actor a, String action, Map<String, String> args) {
        if ("open".equals(action)) return open(a, args.getOrDefault("to", "main"), args, null, null);
        Map<String, String> p = new LinkedHashMap<>(args);
        try {
            String ok = perform(a, action, p);
            return open(a, p.getOrDefault("_v", "main"), p, ok, "ok");
        } catch (FactionException e) {
            return open(a, args.getOrDefault("_v", "main"), args, e.getMessage(), "err");
        }
    }

    public View open(Actor a, String view, Map<String, String> p, String msg, String kind) {
        try {
            ViewBuilder b = switch (view) {
                case "factions" -> factionsList(a, p);
                case "members" -> members(a, p);
                case "member" -> member(a, p);
                case "dragons" -> dragons(a, p);
                case "requests" -> requests(a, p);
                case "admin" -> admin(a, p);
                case "admin_faction" -> adminFaction(a, p);
                case "admin_grades" -> adminGrades(a, p);
                case "admin_grade" -> adminGrade(a, p);
                case "admin_emblems" -> adminEmblems(a, p);
                case "admin_emblem" -> adminEmblem(a, p);
                default -> main(a, p);
            };
            return b.message(msg, kind).build();
        } catch (FactionException e) {
            // vue devenue invalide (faction supprimee, droits retires...) : retour a l'accueil avec le message
            return main(a, Map.of()).message(msg != null && "ok".equals(kind) ? msg : e.getMessage(), msg != null && "ok".equals(kind) ? "ok" : "err").build();
        }
    }

    // ================================================================== actions

    private String perform(Actor a, String action, Map<String, String> p) {
        UUID me = a.uuid();
        switch (action) {
            case "join" -> {
                Faction f = s.requireFaction(p.get("fid"));
                s.requestJoin(me, a.name(), f.id());
                env.notifyFaction(f.id(), FactionPermission.GERER_DEMANDES, a.name() + " demande à rejoindre " + f.name() + ".");
                return "Demande envoyée à « " + f.name() + " ».";
            }
            case "cancel_req" -> { s.cancelRequest(me); return "Demande annulée."; }
            case "accept" -> {
                UUID t = uuid(p.get("target"));
                Member m = s.acceptRequest(me, a.admin(), t);
                Faction f = s.requireFaction(m.factionId());
                env.notify(t, "Votre demande a été acceptée : vous êtes désormais membre de « " + f.name() + " ».");
                return m.name() + " a rejoint la faction.";
            }
            case "refuse" -> {
                UUID t = uuid(p.get("target"));
                s.refuseRequest(me, a.admin(), t);
                env.notify(t, "Votre demande pour rejoindre une faction a été refusée.");
                return "Demande refusée.";
            }
            case "kick" -> {
                UUID t = uuid(p.get("target"));
                Member m = s.kickBy(me, a.admin(), t);
                env.notify(t, "Vous avez été exclu de votre faction. Sans faction, vous ne pouvez plus avoir de dragon.");
                return m.name() + " a été exclu de la faction.";
            }
            case "grade_set" -> {
                UUID t = uuid(p.get("target"));
                s.setGradeBy(me, a.admin(), t, p.get("grade"));
                Member m = s.member(t);
                Grade g = s.gradeOf(t);
                env.notify(t, "Votre grade est désormais : " + (g == null ? "?" : g.name()) + ".");
                return m.name() + " est maintenant « " + (g == null ? "?" : g.name()) + " ».";
            }
            default -> { }
        }
        if (!action.startsWith("admin_") && !action.startsWith("g_") && !action.startsWith("e_"))
            throw new FactionException("Action inconnue.");
        if (!a.admin()) throw new FactionException("Action réservée aux administrateurs.");
        String fid = p.get("fid");
        switch (action) {
            case "admin_create" -> {
                Faction f = s.createFaction(p.get("nid"), p.get("nname"), DragonRule.NON_CORROMPUS);
                p.put("fid", f.id());
                p.put("_v", "admin_faction");
                return "Faction « " + f.name() + " » créée (grades par défaut ajoutés).";
            }
            case "admin_delete" -> {
                Faction f = s.requireFaction(fid);
                for (Member m : s.members(f.id())) env.notify(m.uuid(), "Votre faction « " + f.name() + " » a été dissoute.");
                s.deleteFaction(f.id());
                p.put("_v", "admin");
                return "Faction « " + f.name() + " » supprimée.";
            }
            case "admin_save" -> {
                s.renameFaction(fid, p.get("fname"));
                s.describeFaction(fid, p.get("fdesc"));
                return "Informations enregistrées.";
            }
            case "admin_rule" -> {
                Faction f = s.requireFaction(fid);
                s.setRule(fid, f.rule().next());
                return "Règle des dragons : " + f.rule().label() + ".";
            }
            case "admin_emblem" -> {
                s.assignEmblem(fid, p.getOrDefault("eid", ""));
                return "Emblème mis à jour.";
            }
            case "admin_add" -> {
                UiEnv.PlayerRef ref = env.resolve(p.getOrDefault("pname", "").strip());
                if (ref == null) throw new FactionException("Joueur inconnu : il doit s'être déjà connecté au serveur.");
                Member m = s.addMember(fid, ref.uuid(), ref.name(), null);
                env.notify(ref.uuid(), "Vous avez été ajouté à la faction « " + s.requireFaction(fid).name() + " ».");
                return m.name() + " ajouté à la faction.";
            }
            case "admin_remove" -> {
                UUID t = uuid(p.get("target"));
                Member m = s.removeMember(t);
                env.notify(t, "Vous avez été retiré de votre faction. Sans faction, vous ne pouvez plus avoir de dragon.");
                return m.name() + " retiré de la faction.";
            }
            case "admin_transfer" -> {
                UUID t = uuid(p.get("target"));
                s.transfer(t, p.get("dest"));
                Faction f = s.requireFaction(p.get("dest"));
                env.notify(t, "Vous avez été transféré dans la faction « " + f.name() + " ».");
                return s.member(t).name() + " transféré vers « " + f.name() + " ».";
            }
            case "g_create" -> {
                Grade g = s.createGrade(fid, p.get("gname"));
                return "Grade « " + g.name() + " » créé (placé en bas de la hiérarchie).";
            }
            case "g_rename" -> { s.renameGrade(fid, p.get("gid"), p.get("gname")); return "Grade renommé."; }
            case "g_delete" -> {
                s.deleteGrade(fid, p.get("gid"));
                p.put("_v", "admin_grades");
                return "Grade supprimé (ses membres ont été placés au grade suivant).";
            }
            case "g_up" -> { s.moveGrade(fid, p.get("gid"), true); return "Grade monté."; }
            case "g_down" -> { s.moveGrade(fid, p.get("gid"), false); return "Grade descendu."; }
            case "g_perm" -> {
                s.togglePermission(fid, p.get("gid"), FactionPermission.parse(p.get("perm")));
                return "Permission modifiée.";
            }
            case "e_create" -> {
                Emblem e = s.createEmblem(p.get("neid"), p.get("nename"));
                p.put("eid", e.id());
                p.put("_v", "admin_emblem");
                return "Emblème « " + e.name() + " » créé.";
            }
            case "e_save" -> {
                s.editEmblem(p.get("eid"), p.get("ename"), null, null, p.get("ecol1"), p.get("ecol2"), p.getOrDefault("esym", ""));
                return "Emblème enregistré.";
            }
            case "e_shape" -> { s.cycleEmblemShape(p.get("eid")); return "Forme changée."; }
            case "e_pattern" -> { s.cycleEmblemPattern(p.get("eid")); return "Motif changé."; }
            case "e_delete" -> {
                s.deleteEmblem(p.get("eid"));
                p.put("_v", "admin_emblems");
                return "Emblème supprimé.";
            }
            default -> throw new FactionException("Action inconnue.");
        }
    }

    // ================================================================== ecrans joueurs

    private ViewBuilder main(Actor a, Map<String, String> p) {
        Faction f = s.factionOf(a.uuid());
        ViewBuilder b = new ViewBuilder("main", "Citizen — Factions", p);
        if (f == null) {
            b.h("Vous n'appartenez à aucune faction");
            b.r("Sans faction, vous ne pouvez ni monter, ni posséder, ni faire éclore de dragon.");
            JoinRequest r = s.requestOf(a.uuid());
            if (r != null) {
                Faction rf = s.faction(r.factionId());
                b.g("Demande en attente pour « " + (rf == null ? r.factionId() : rf.name()) + " » depuis le " + date(r.at()),
                        b.act("Annuler", "cancel_req"));
            }
            b.blank();
            b.n("Choisissez une faction et envoyez une demande : un membre habilité ou un administrateur l'acceptera.");
            b.button(nav("Voir les factions", "factions"));
        } else {
            Member m = s.member(a.uuid());
            Grade g = s.gradeOf(a.uuid());
            b.emblem(emblemCode(f));
            b.line(f.name(), "h", emblemCode(f));
            if (!f.description().isBlank()) b.m(f.description());
            b.n("Votre grade : " + (g == null ? "?" : g.name()));
            b.n("Membre depuis le " + date(m.joinedAt()));
            b.n("Dragons autorisés : " + f.rule().label());
            List<Member> all = s.members(f.id());
            long on = all.stream().filter(x -> env.online(x.uuid())).count();
            b.n("Membres : " + all.size() + " (" + on + " en ligne)");
            b.blank();
            b.h("Mes dragons");
            List<DragonRecord> ds = s.dragonsOf(a.uuid());
            if (ds.isEmpty()) b.m("Aucun dragon lié pour le moment.");
            for (DragonRecord d : ds.subList(0, Math.min(3, ds.size()))) b.n(dragonLine(d, a.uuid()));
            if (ds.size() > 3) b.m("… et " + (ds.size() - 3) + " autre(s).");
            if (g != null && g.has(FactionPermission.VOIR_MEMBRES)) b.button(nav("Membres", "members", "fid", f.id()));
            b.button(nav("Mes dragons", "dragons", "target", a.uuid().toString()));
            if (a.admin() || (g != null && g.has(FactionPermission.GERER_DEMANDES))) {
                int n = s.requests(f.id()).size();
                b.button(nav("Demandes (" + n + ")", "requests", "fid", f.id()));
            }
            b.button(nav("Factions", "factions"));
        }
        if (a.admin()) b.button(nav("Administration", "admin"));
        return b.refresh();
    }

    private ViewBuilder factionsList(Actor a, Map<String, String> p) {
        ViewBuilder b = new ViewBuilder("factions", "Factions du royaume", p);
        boolean member = s.member(a.uuid()) != null;
        JoinRequest r = s.requestOf(a.uuid());
        for (Faction f : s.factions()) {
            int n = s.members(f.id()).size();
            View.Btn[] btns = new View.Btn[0];
            if (!member && (r == null || !r.factionId().equals(f.id()))) btns = new View.Btn[]{b.act("Demander à rejoindre", "join", "fid", f.id())};
            b.line(f.name() + " — " + n + " membre(s)", "h", emblemCode(f), btns);
            if (!f.description().isBlank()) b.m(f.description());
            b.n("Dragons : " + f.rule().label());
            if (r != null && r.factionId().equals(f.id())) b.g("Votre demande est en attente.", b.act("Annuler", "cancel_req"));
        }
        if (s.factions().isEmpty()) b.m("Aucune faction n'a encore été créée.");
        b.button(nav("Retour", "main"));
        return b.refresh();
    }

    private ViewBuilder members(Actor a, Map<String, String> p) {
        Faction f = s.requireFaction(p.get("fid"));
        if (!a.admin()) {
            Member me = s.member(a.uuid());
            if (me == null || !me.factionId().equals(f.id()) || !s.has(a.uuid(), FactionPermission.VOIR_MEMBRES))
                throw new FactionException("Vous ne pouvez pas consulter les membres de cette faction.");
        }
        String filter = p.getOrDefault("f", "all");
        List<Member> all = s.members(f.id());
        List<Member> list = new ArrayList<>();
        for (Member m : all) {
            boolean on = env.online(m.uuid());
            if (filter.equals("on") && !on || filter.equals("off") && on) continue;
            list.add(m);
        }
        int pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(pages - 1, intArg(p.get("p"), 0)));
        long online = all.stream().filter(m -> env.online(m.uuid())).count();
        ViewBuilder b = new ViewBuilder("members", "Membres — " + f.name(), p).emblem(emblemCode(f));
        b.h(all.size() + " membre(s) : " + online + " en ligne, " + (all.size() - online) + " hors ligne");
        b.m("Filtre : " + switch (filter) { case "on" -> "en ligne"; case "off" -> "hors ligne"; default -> "tous"; }
                + "  —  page " + (page + 1) + " / " + pages);
        for (Member m : list.subList(page * PAGE_SIZE, Math.min(list.size(), (page + 1) * PAGE_SIZE))) {
            boolean on = env.online(m.uuid());
            Grade g = f.grade(m.gradeId());
            String txt = (on ? "● " : "○ ") + m.name() + "  —  " + (g == null ? "?" : g.name())
                    + (on ? "" : "  (vu le " + date(m.lastSeen()) + ")");
            b.line(txt, on ? "v" : "m", "", nav("Fiche", "member", "target", m.uuid().toString(), "fid", f.id()));
        }
        if (list.isEmpty()) b.m("Aucun membre à afficher.");
        if (page > 0) b.button(nav("◀ Précédent", "members", "fid", f.id(), "f", filter, "p", String.valueOf(page - 1)));
        if (page < pages - 1) b.button(nav("Suivant ▶", "members", "fid", f.id(), "f", filter, "p", String.valueOf(page + 1)));
        b.button(nav("Tous", "members", "fid", f.id(), "f", "all"));
        b.button(nav("En ligne", "members", "fid", f.id(), "f", "on"));
        b.button(nav("Hors ligne", "members", "fid", f.id(), "f", "off"));
        b.button(nav("Retour", a.admin() && s.member(a.uuid()) == null ? "admin_faction" : "main", "fid", f.id()));
        return b.refresh();
    }

    private ViewBuilder member(Actor a, Map<String, String> p) {
        UUID t = uuid(p.get("target"));
        Member m = s.member(t);
        if (m == null) throw new FactionException("Ce joueur n'appartient plus à aucune faction.");
        Faction f = s.requireFaction(m.factionId());
        boolean self = t.equals(a.uuid());
        if (!a.admin() && !self) {
            Member me = s.member(a.uuid());
            if (me == null || !me.factionId().equals(f.id()) || !s.has(a.uuid(), FactionPermission.VOIR_MEMBRES))
                throw new FactionException("Vous ne pouvez pas consulter cette fiche.");
        }
        Grade g = f.grade(m.gradeId());
        boolean on = env.online(t);
        ViewBuilder b = new ViewBuilder("member", "Fiche — " + m.name(), p).emblem(emblemCode(f));
        b.line(m.name(), "h", emblemCode(f));
        b.n("Faction : " + f.name());
        b.n("Grade : " + (g == null ? "?" : g.name()) + "  (rang " + (f.rank(m.gradeId()) + 1) + " / " + f.grades().size() + ")");
        if (on) b.v("● En ligne"); else b.m("○ Hors ligne — dernière connexion le " + date(m.lastSeen()));
        b.n("Membre depuis le " + date(m.joinedAt()));
        if (g != null && !g.perms().isEmpty()) {
            StringJoiner sj = new StringJoiner(", ");
            for (FactionPermission fp : g.perms()) sj.add(fp.label());
            b.m("Droits : " + sj);
        }
        boolean seeDragons = a.admin() || self || s.has(a.uuid(), FactionPermission.VOIR_DRAGONS);
        b.blank();
        b.h("Dragons liés");
        if (!seeDragons) b.m("Votre grade ne permet pas de voir les dragons des autres membres.");
        else {
            List<DragonRecord> ds = s.dragonsOf(t);
            if (ds.isEmpty()) b.m("Aucun dragon lié.");
            for (DragonRecord d : ds) b.n(dragonLine(d, t));
        }

        boolean canGrade = s.canManage(a.uuid(), a.admin(), m, FactionPermission.PROMOUVOIR);
        boolean canKick = s.canManage(a.uuid(), a.admin(), m, FactionPermission.EXCLURE);
        if (canGrade) {
            b.blank();
            b.h("Changer le grade");
            int myRank = a.admin() ? -1 : f.rank(s.member(a.uuid()).gradeId());
            for (int i = 0; i < f.grades().size(); i++) {
                Grade gg = f.grades().get(i);
                if (i <= myRank) continue;
                if (gg.id().equals(m.gradeId())) b.g("• " + gg.name() + " (actuel)");
                else b.n("• " + gg.name(), b.act("Attribuer", "grade_set", "target", t.toString(), "grade", gg.id()));
            }
        }
        if (canKick && !a.admin())
            b.button(confirm(b.act("Exclure", "kick", "target", t.toString()), "Exclure " + m.name() + " de la faction ?"));
        if (a.admin()) {
            b.blank();
            b.h("Administration");
            for (Faction other : s.factions())
                if (!other.id().equals(f.id()))
                    b.n("Transférer vers « " + other.name() + " »", confirm(b.act("Transférer", "admin_transfer", "target", t.toString(), "dest", other.id()),
                            "Transférer " + m.name() + " vers « " + other.name() + " » ? Il prendra le grade le plus bas."));
            b.button(confirm(b.act("Retirer de la faction", "admin_remove", "target", t.toString()), "Retirer " + m.name() + " de sa faction ?"));
        }
        b.button(nav("Membres", "members", "fid", f.id()));
        b.button(nav("Accueil", "main"));
        return b.refresh();
    }

    private ViewBuilder dragons(Actor a, Map<String, String> p) {
        UUID t = p.containsKey("target") ? uuid(p.get("target")) : a.uuid();
        boolean self = t.equals(a.uuid());
        if (!self && !a.admin()) {
            Member me = s.member(a.uuid()), other = s.member(t);
            if (me == null || other == null || !me.factionId().equals(other.factionId()) || !s.has(a.uuid(), FactionPermission.VOIR_DRAGONS))
                throw new FactionException("Vous ne pouvez pas voir ces dragons.");
        }
        Member m = s.member(t);
        ViewBuilder b = new ViewBuilder("dragons", self ? "Mes dragons" : "Dragons de " + (m == null ? "?" : m.name()), p);
        List<DragonRecord> ds = s.dragonsOf(t);
        if (ds.isEmpty()) b.m("Aucun dragon lié. Le lien se crée et grandit en montant votre dragon.");
        long now = s.now();
        for (DragonRecord d : ds) {
            double v = BondMath.effective(d.bond(t), now, env.bond());
            b.line(d.displayName(), "h", "");
            b.n(d.speciesLabel() + (d.variant().isBlank() ? "" : " — " + d.variant()) + (d.corrupted() ? "  (corrompu)" : "")
                    + "  —  stade " + d.stage());
            if (d.downed()) b.r("✖ À terre — un Dragonnier ou un Chasseur de dragon doit le sauver (pomme dorée).");
            if (!d.primordial().isBlank()) b.g("★ Dragon primordial — réservé au plus haut grade de sa faction");
            b.line("Lien : " + BondMath.percent(v) + " — " + BondMath.tier(v), v >= 50 ? "g" : "r", "");
            b.m(t.equals(d.owner()) ? "Propriétaire : vous" + (self ? "" : " (ce joueur)") : "Propriétaire : autre joueur");
            Bond bd = d.bond(t);
            if (bd.lastRide() > 0) b.m("Dernière monte : " + date(bd.lastRide()));
            if (d.lastSeen() > 0) b.m("Dernière position connue : " + d.dimension() + " " + d.x() + " / " + d.y() + " / " + d.z()
                    + " (" + date(d.lastSeen()) + ")");
        }
        b.button(nav("Accueil", "main"));
        return b.refresh();
    }

    private ViewBuilder requests(Actor a, Map<String, String> p) {
        Faction f = s.requireFaction(p.get("fid"));
        if (!a.admin()) {
            Member me = s.member(a.uuid());
            if (me == null || !me.factionId().equals(f.id()) || !s.has(a.uuid(), FactionPermission.GERER_DEMANDES))
                throw new FactionException("Votre grade ne permet pas de gérer les demandes.");
        }
        ViewBuilder b = new ViewBuilder("requests", "Demandes — " + f.name(), p).emblem(emblemCode(f));
        List<JoinRequest> rs = s.requests(f.id());
        if (rs.isEmpty()) b.m("Aucune demande en attente.");
        for (JoinRequest r : rs) {
            boolean on = env.online(r.player());
            b.line((on ? "● " : "○ ") + r.playerName() + "  —  le " + date(r.at()), on ? "v" : "n", "",
                    b.act("Accepter", "accept", "target", r.player().toString()),
                    confirm(b.act("Refuser", "refuse", "target", r.player().toString()), "Refuser la demande de " + r.playerName() + " ?"));
        }
        b.button(nav("Retour", a.admin() && s.member(a.uuid()) == null ? "admin_faction" : "main", "fid", f.id()));
        return b.refresh();
    }

    // ================================================================== administration

    private void requireAdmin(Actor a) {
        if (!a.admin()) throw new FactionException("Espace réservé aux administrateurs.");
    }

    private ViewBuilder admin(Actor a, Map<String, String> p) {
        requireAdmin(a);
        ViewBuilder b = new ViewBuilder("admin", "Administration des factions", p);
        b.h("Factions");
        for (Faction f : s.factions()) {
            int n = s.members(f.id()).size();
            int rq = s.requests(f.id()).size();
            b.line(f.name() + " (" + f.id() + ") — " + n + " membre(s)" + (rq > 0 ? ", " + rq + " demande(s)" : ""), "n", emblemCode(f),
                    nav("Gérer", "admin_faction", "fid", f.id()));
        }
        if (s.factions().isEmpty()) b.m("Aucune faction.");
        b.blank();
        b.m("Créer une faction : identifiant (minuscules, chiffres, _) et nom affiché, puis « Créer ».");
        b.field("nid", "Identifiant", "", 24);
        b.field("nname", "Nom affiché", "", 32);
        b.button(b.act("Créer", "admin_create"));
        b.button(nav("Emblèmes", "admin_emblems"));
        b.button(nav("Accueil", "main"));
        return b.refresh();
    }

    private ViewBuilder adminFaction(Actor a, Map<String, String> p) {
        requireAdmin(a);
        Faction f = s.requireFaction(p.get("fid"));
        ViewBuilder b = new ViewBuilder("admin_faction", "Gérer — " + f.name(), Map.of("fid", f.id())).emblem(emblemCode(f));
        b.line(f.name() + "  (" + f.id() + ")", "h", emblemCode(f));
        b.n("Créée le " + date(f.createdAt()));
        b.n("Dragons autorisés : " + f.rule().label(), b.act("Changer", "admin_rule"));
        Emblem cur = s.emblem(f.emblemId());
        b.n("Emblème : " + (cur == null ? "aucun" : cur.name()));
        b.n("Grades : " + f.grades().size(), nav("Grades", "admin_grades", "fid", f.id()));
        List<Member> ms = s.members(f.id());
        b.n("Membres : " + ms.size(), nav("Membres", "members", "fid", f.id()));
        b.n("Demandes en attente : " + s.requests(f.id()).size(), nav("Demandes", "requests", "fid", f.id()));
        b.blank();
        b.h("Choisir l'emblème");
        for (Emblem e : s.emblems()) {
            if (e.id().equals(f.emblemId())) b.line(e.name() + " (actuel)", "g", e.encode());
            else b.line(e.name(), "n", e.encode(), b.act("Attribuer", "admin_emblem", "eid", e.id()));
        }
        if (cur != null) b.m("Retirer l'emblème", b.act("Retirer", "admin_emblem", "eid", ""));
        b.field("fname", "Nom affiché", f.name(), 32);
        b.field("fdesc", "Description", f.description(), 240);
        b.field("pname", "Ajouter un joueur (pseudo)", "", 16);
        b.button(b.act("Enregistrer", "admin_save"));
        b.button(b.act("Ajouter le joueur", "admin_add"));
        b.button(confirm(b.act("Supprimer la faction", "admin_delete"),
                "Supprimer « " + f.name() + " » ? Ses " + ms.size() + " membre(s) n'auront plus de faction (et ne pourront plus avoir de dragon)."));
        b.button(nav("Retour", "admin"));
        return b.refresh();
    }

    private ViewBuilder adminGrades(Actor a, Map<String, String> p) {
        requireAdmin(a);
        Faction f = s.requireFaction(p.get("fid"));
        ViewBuilder b = new ViewBuilder("admin_grades", "Grades — " + f.name(), Map.of("fid", f.id())).emblem(emblemCode(f));
        b.h("Hiérarchie (du plus haut au plus bas)");
        List<Grade> gs = f.grades();
        for (int i = 0; i < gs.size(); i++) {
            Grade g = gs.get(i);
            long n = s.members(f.id()).stream().filter(m -> m.gradeId().equals(g.id())).count();
            List<View.Btn> btns = new ArrayList<>();
            if (i > 0) btns.add(b.act("▲", "g_up", "gid", g.id()));
            if (i < gs.size() - 1) btns.add(b.act("▼", "g_down", "gid", g.id()));
            btns.add(nav("Modifier", "admin_grade", "fid", f.id(), "gid", g.id()));
            b.line((i + 1) + ". " + g.name() + " — " + n + " membre(s)", "n", "", btns.toArray(new View.Btn[0]));
        }
        b.field("gname", "Nom du nouveau grade", "", 24);
        b.button(b.act("Créer le grade", "g_create"));
        b.button(nav("Retour", "admin_faction", "fid", f.id()));
        return b.refresh();
    }

    private ViewBuilder adminGrade(Actor a, Map<String, String> p) {
        requireAdmin(a);
        Faction f = s.requireFaction(p.get("fid"));
        Grade g = s.requireGrade(f, p.get("gid"));
        ViewBuilder b = new ViewBuilder("admin_grade", "Grade — " + g.name(), Map.of("fid", f.id(), "gid", g.id())).emblem(emblemCode(f));
        b.h(g.name() + " — rang " + (f.rank(g.id()) + 1) + " / " + f.grades().size());
        b.m("Cliquez pour accorder ou retirer une permission.");
        for (FactionPermission fp : FactionPermission.values()) {
            boolean on = g.has(fp);
            b.line((on ? "✔ " : "✘ ") + fp.label(), on ? "g" : "m", "", b.act(on ? "Retirer" : "Accorder", "g_perm", "perm", fp.name()));
        }
        b.field("gname", "Nouveau nom", g.name(), 24);
        b.button(b.act("Renommer", "g_rename"));
        b.button(confirm(b.act("Supprimer le grade", "g_delete"), "Supprimer le grade « " + g.name() + " » ? Ses membres passeront au grade suivant."));
        b.button(nav("Retour", "admin_grades", "fid", f.id()));
        return b.refresh();
    }

    private ViewBuilder adminEmblems(Actor a, Map<String, String> p) {
        requireAdmin(a);
        ViewBuilder b = new ViewBuilder("admin_emblems", "Emblèmes", p);
        for (Emblem e : s.emblems()) {
            StringJoiner used = new StringJoiner(", ");
            for (Faction f : s.factions()) if (f.emblemId().equals(e.id())) used.add(f.name());
            b.line(e.name() + " (" + e.id() + ")" + (used.length() > 0 ? " — " + used : ""), "n", e.encode(),
                    nav("Modifier", "admin_emblem", "eid", e.id()));
        }
        if (s.emblems().isEmpty()) b.m("Aucun emblème.");
        b.field("neid", "Identifiant du nouvel emblème", "", 24);
        b.field("nename", "Nom", "", 32);
        b.button(b.act("Créer l'emblème", "e_create"));
        b.button(nav("Retour", "admin"));
        return b.refresh();
    }

    private ViewBuilder adminEmblem(Actor a, Map<String, String> p) {
        requireAdmin(a);
        Emblem e = s.requireEmblem(p.get("eid"));
        ViewBuilder b = new ViewBuilder("admin_emblem", "Emblème — " + e.name(), Map.of("eid", e.id())).emblem(e.encode());
        b.line("Aperçu : " + e.name(), "h", e.encode());
        b.n("Forme : " + e.shape().label(), b.act("Changer", "e_shape"));
        b.n("Motif : " + e.pattern().label(), b.act("Changer", "e_pattern"));
        b.n("Couleur principale : #" + Emblem.hex(e.primary()));
        b.n("Couleur secondaire : #" + Emblem.hex(e.secondary()));
        b.n("Symbole : " + (e.symbol().isEmpty() ? "aucun" : e.symbol()));
        b.m("Couleurs au format #RRGGBB (ex. #8B1A1A rouge sombre, #D4AF37 or). Symbole : un seul caractère.");
        b.field("ename", "Nom", e.name(), 32);
        b.field("esym", "Symbole", e.symbol(), 2);
        b.field("ecol1", "Couleur principale", "#" + Emblem.hex(e.primary()), 7);
        b.field("ecol2", "Couleur secondaire", "#" + Emblem.hex(e.secondary()), 7);
        b.button(b.act("Enregistrer", "e_save"));
        b.button(confirm(b.act("Supprimer", "e_delete"), "Supprimer l'emblème « " + e.name() + " » ? Les factions qui l'utilisent n'auront plus d'emblème."));
        b.button(nav("Retour", "admin_emblems"));
        return b.refresh();
    }

    // ================================================================== utilitaires

    private String emblemCode(Faction f) {
        Emblem e = s.emblem(f.emblemId());
        return e == null ? "" : e.encode();
    }

    private String dragonLine(DragonRecord d, UUID player) {
        double v = BondMath.effective(d.bond(player), s.now(), env.bond());
        return d.displayName() + (d.corrupted() ? " (corrompu)" : "") + " — lien " + BondMath.percent(v) + " (" + BondMath.tier(v) + ")";
    }

    static String date(long ms) { return ms <= 0 ? "—" : DATE.format(Instant.ofEpochMilli(ms)); }

    private static UUID uuid(String s) {
        try { return UUID.fromString(s); } catch (RuntimeException e) { throw new FactionException("Joueur invalide."); }
    }

    private static int intArg(String s, int def) {
        try { return s == null ? def : Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
