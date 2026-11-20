package fr.citizen.factions.platform;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.citizen.factions.core.*;
import fr.citizen.factions.ui.UiEnv;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * /factions : menus joueurs ; /factions admin ... : gestion complete, reservee aux administrateurs
 * (niveau op configure ou liste adminPlayers). Les memes regles s'appliquent qu'en passant par l'interface.
 */
public final class Commands {
    private Commands() { }

    private interface Body { int run(CommandContext<CommandSourceStack> c, FactionRuntime rt); }

    private static final SuggestionProvider<CommandSourceStack> FACTIONS = (c, b) -> {
        FactionRuntime rt = FactionRuntime.current();
        List<String> l = new ArrayList<>();
        if (rt != null) for (Faction f : rt.service().factions()) l.add(f.id());
        return SharedSuggestionProvider.suggest(l, b);
    };
    private static final SuggestionProvider<CommandSourceStack> PLAYERS = (c, b) -> {
        Set<String> l = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        l.addAll(Arrays.asList(c.getSource().getServer().getPlayerNames()));
        FactionRuntime rt = FactionRuntime.current();
        if (rt != null) for (Faction f : rt.service().factions()) for (Member m : rt.service().members(f.id())) l.add(m.name());
        return SharedSuggestionProvider.suggest(l, b);
    };
    private static final SuggestionProvider<CommandSourceStack> EMBLEMS = (c, b) -> {
        FactionRuntime rt = FactionRuntime.current();
        List<String> l = new ArrayList<>(List.of("aucun"));
        if (rt != null) for (Emblem e : rt.service().emblems()) l.add(e.id());
        return SharedSuggestionProvider.suggest(l, b);
    };
    private static final SuggestionProvider<CommandSourceStack> RULES = (c, b) -> {
        List<String> l = new ArrayList<>();
        for (DragonRule r : DragonRule.values()) l.add(r.name());
        return SharedSuggestionProvider.suggest(l, b);
    };
    private static final SuggestionProvider<CommandSourceStack> PERMS = (c, b) -> {
        List<String> l = new ArrayList<>();
        for (FactionPermission p : FactionPermission.values()) l.add(p.name());
        return SharedSuggestionProvider.suggest(l, b);
    };
    private static final SuggestionProvider<CommandSourceStack> GRADES = (c, b) -> {
        FactionRuntime rt = FactionRuntime.current();
        Set<String> l = new TreeSet<>();
        if (rt != null) {
            Faction f = null;
            try { f = rt.service().faction(StringArgumentType.getString(c, "faction")); } catch (IllegalArgumentException ignored) { }
            if (f == null) {
                try {
                    UiEnv.PlayerRef ref = rt.resolve(StringArgumentType.getString(c, "joueur"));
                    if (ref != null) f = rt.service().factionOf(ref.uuid());
                } catch (IllegalArgumentException ignored) { }
            }
            if (f != null) for (Grade g : f.grades()) l.add(g.id());
            else for (Faction ff : rt.service().factions()) for (Grade g : ff.grades()) l.add(g.id());
        }
        return SharedSuggestionProvider.suggest(l, b);
    };

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("factions").executes(c -> open(c, "main"));
        root.then(literal("liste").executes(c -> open(c, "factions")));
        root.then(literal("membres").executes(c -> {
            if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return fail(c, "Commande réservée aux joueurs.");
            FactionRuntime rt = FactionRuntime.current();
            if (rt == null) return fail(c, "Les factions ne sont pas disponibles.");
            Faction f = rt.service().factionOf(p.getUUID());
            if (f == null) return fail(c, "Vous n'appartenez à aucune faction.");
            rt.open(p, "members", Map.of("fid", f.id()));
            return 1;
        }));
        root.then(literal("dragons").executes(c -> open(c, "dragons")));
        root.then(admin());
        var node = d.register(root);
        d.register(literal("faction").executes(c -> open(c, "main")).redirect(node));
    }

    // ================================================================== administration

    private static LiteralArgumentBuilder<CommandSourceStack> admin() {
        LiteralArgumentBuilder<CommandSourceStack> a = literal("admin").requires(Admins::is).executes(c -> open(c, "admin"));

        a.then(literal("creer").then(argument("id", StringArgumentType.word()).then(argument("nom", StringArgumentType.greedyString())
                .executes(c -> run(c, (cc, rt) -> {
                    Faction f = rt.service().createFaction(StringArgumentType.getString(cc, "id"), StringArgumentType.getString(cc, "nom"), DragonRule.NON_CORROMPUS);
                    return ok(cc, "Faction « " + f.name() + " » créée (" + f.id() + "), grades par défaut ajoutés. Règle : " + f.rule().label() + ".");
                })))));

        a.then(literal("supprimer").then(argument("faction", StringArgumentType.word()).suggests(FACTIONS)
                .executes(c -> run(c, (cc, rt) -> {
                    Faction f = rt.service().requireFaction(StringArgumentType.getString(cc, "faction"));
                    rt.service().deleteFaction(f.id());
                    return ok(cc, "Faction « " + f.name() + " » supprimée ; ses membres n'ont plus de faction.");
                }))));

        a.then(literal("modifier").then(argument("faction", StringArgumentType.word()).suggests(FACTIONS)
                .then(literal("nom").then(argument("valeur", StringArgumentType.greedyString()).executes(c -> run(c, (cc, rt) -> {
                    rt.service().renameFaction(StringArgumentType.getString(cc, "faction"), StringArgumentType.getString(cc, "valeur"));
                    return ok(cc, "Nom modifié.");
                }))))
                .then(literal("description").then(argument("valeur", StringArgumentType.greedyString()).executes(c -> run(c, (cc, rt) -> {
                    rt.service().describeFaction(StringArgumentType.getString(cc, "faction"), StringArgumentType.getString(cc, "valeur"));
                    return ok(cc, "Description modifiée.");
                }))))
                .then(literal("regle").then(argument("valeur", StringArgumentType.word()).suggests(RULES).executes(c -> run(c, (cc, rt) -> {
                    DragonRule r = DragonRule.parse(StringArgumentType.getString(cc, "valeur"));
                    rt.service().setRule(StringArgumentType.getString(cc, "faction"), r);
                    return ok(cc, "Règle des dragons : " + r.label() + ".");
                }))))
                .then(literal("embleme").then(argument("valeur", StringArgumentType.word()).suggests(EMBLEMS).executes(c -> run(c, (cc, rt) -> {
                    String v = StringArgumentType.getString(cc, "valeur");
                    rt.service().assignEmblem(StringArgumentType.getString(cc, "faction"), v.equalsIgnoreCase("aucun") ? "" : v);
                    return ok(cc, "Emblème mis à jour.");
                }))))));

        a.then(literal("ajouter").then(argument("faction", StringArgumentType.word()).suggests(FACTIONS)
                .then(argument("joueur", StringArgumentType.word()).suggests(PLAYERS)
                        .executes(c -> run(c, (cc, rt) -> add(cc, rt, null)))
                        .then(argument("grade", StringArgumentType.word()).suggests(GRADES)
                                .executes(c -> run(c, (cc, rt) -> add(cc, rt, StringArgumentType.getString(cc, "grade"))))))));

        a.then(literal("retirer").then(argument("joueur", StringArgumentType.word()).suggests(PLAYERS)
                .executes(c -> run(c, (cc, rt) -> {
                    UiEnv.PlayerRef ref = player(cc, rt);
                    Member m = rt.service().removeMember(ref.uuid());
                    notify(rt, ref, "Vous avez été retiré de votre faction. Sans faction, vous ne pouvez plus avoir de dragon.");
                    return ok(cc, m.name() + " retiré de sa faction.");
                }))));

        a.then(literal("transferer").then(argument("joueur", StringArgumentType.word()).suggests(PLAYERS)
                .then(argument("faction", StringArgumentType.word()).suggests(FACTIONS).executes(c -> run(c, (cc, rt) -> {
                    UiEnv.PlayerRef ref = player(cc, rt);
                    Faction f = rt.service().requireFaction(StringArgumentType.getString(cc, "faction"));
                    rt.service().transfer(ref.uuid(), f.id());
                    notify(rt, ref, "Vous avez été transféré dans la faction « " + f.name() + " ».");
                    return ok(cc, ref.name() + " transféré vers « " + f.name() + " ».");
                })))));

        a.then(literal("grade").then(argument("joueur", StringArgumentType.word()).suggests(PLAYERS)
                .then(argument("grade", StringArgumentType.word()).suggests(GRADES).executes(c -> run(c, (cc, rt) -> {
                    UiEnv.PlayerRef ref = player(cc, rt);
                    rt.service().setGrade(ref.uuid(), StringArgumentType.getString(cc, "grade"));
                    Grade g = rt.service().gradeOf(ref.uuid());
                    notify(rt, ref, "Votre grade est désormais : " + g.name() + ".");
                    return ok(cc, ref.name() + " est maintenant « " + g.name() + " ».");
                })))));

        a.then(literal("liste").executes(c -> run(c, (cc, rt) -> {
            FactionService s = rt.service();
            line(cc, "== Factions (" + s.factions().size() + ") ==", ChatFormatting.GOLD);
            for (Faction f : s.factions()) {
                List<Member> ms = s.members(f.id());
                long on = ms.stream().filter(m -> rt.server().getPlayerList().getPlayer(m.uuid()) != null).count();
                Emblem e = s.emblem(f.emblemId());
                line(cc, f.name() + " [" + f.id() + "] — " + ms.size() + " membre(s), " + on + " en ligne — " + f.rule().label()
                        + " — emblème : " + (e == null ? "aucun" : e.name()), ChatFormatting.YELLOW);
            }
            return 1;
        })));

        a.then(literal("membres").then(argument("faction", StringArgumentType.word()).suggests(FACTIONS).executes(c -> run(c, (cc, rt) -> {
            FactionService s = rt.service();
            Faction f = s.requireFaction(StringArgumentType.getString(cc, "faction"));
            List<Member> ms = s.members(f.id());
            line(cc, "== " + f.name() + " : " + ms.size() + " membre(s) ==", ChatFormatting.GOLD);
            for (Member m : ms) {
                boolean on = rt.server().getPlayerList().getPlayer(m.uuid()) != null;
                Grade g = f.grade(m.gradeId());
                line(cc, (on ? "● " : "○ ") + m.name() + " — " + (g == null ? "?" : g.name()), on ? ChatFormatting.GREEN : ChatFormatting.GRAY);
            }
            return 1;
        }))));

        a.then(literal("joueur").then(argument("joueur", StringArgumentType.word()).suggests(PLAYERS).executes(c -> run(c, (cc, rt) -> {
            FactionService s = rt.service();
            UiEnv.PlayerRef ref = player(cc, rt);
            Member m = s.member(ref.uuid());
            line(cc, "== " + ref.name() + " ==", ChatFormatting.GOLD);
            line(cc, "UUID : " + ref.uuid(), ChatFormatting.GRAY);
            if (m == null) {
                line(cc, "Aucune faction (ne peut pas avoir de dragon).", ChatFormatting.RED);
                JoinRequest r = s.requestOf(ref.uuid());
                if (r != null) line(cc, "Demande en attente pour : " + r.factionId(), ChatFormatting.YELLOW);
            } else {
                Faction f = s.requireFaction(m.factionId());
                Grade g = f.grade(m.gradeId());
                line(cc, "Faction : " + f.name() + " — grade : " + (g == null ? "?" : g.name()), ChatFormatting.YELLOW);
                line(cc, "Membre depuis : " + date(m.joinedAt()) + " — dernière connexion : " + date(m.lastSeen()), ChatFormatting.GRAY);
            }
            List<DragonRecord> ds = s.dragonsOf(ref.uuid());
            line(cc, "Dragons liés : " + ds.size(), ChatFormatting.GOLD);
            for (DragonRecord d : ds) line(cc, " - " + dragonText(d, ref.uuid()), ChatFormatting.YELLOW);
            return 1;
        }))));

        a.then(literal("dragon")
                .executes(c -> run(c, (cc, rt) -> {
                    if (!(cc.getSource().getEntity() instanceof ServerPlayer p)) return fail(cc, "Indiquez l'UUID du dragon depuis la console.");
                    Entity d = lookedDragon(p);
                    if (d == null) return fail(cc, "Regardez un dragon (à moins de 24 blocs) ou montez-le.");
                    return dragonInfo(cc, rt, rt.bonds().sync(d));
                }))
                .then(argument("uuid", StringArgumentType.word()).executes(c -> run(c, (cc, rt) -> {
                    UUID id;
                    try { id = UUID.fromString(StringArgumentType.getString(cc, "uuid")); }
                    catch (IllegalArgumentException e) { return fail(cc, "UUID invalide."); }
                    Entity d = rt.bonds().find(id);
                    DragonRecord rec = d != null && DragonBridge.isDragon(d) ? rt.bonds().sync(d) : rt.service().dragon(id);
                    if (rec == null) return fail(cc, "Dragon inconnu.");
                    return dragonInfo(cc, rt, rec);
                }))));

        a.then(primordial());
        a.then(grades());
        a.then(emblems());
        a.then(literal("sauvegarder").executes(c -> run(c, (cc, rt) -> { rt.saveNow(); return ok(cc, "Données des factions sauvegardées."); })));
        return a;
    }

    private static final SuggestionProvider<CommandSourceStack> PRIMORDIAL_FACTIONS =
            (c, b) -> SharedSuggestionProvider.suggest(List.of(Primordial.DRAGONNIER.factionId(), Primordial.CHASSEUR.factionId()), b);

    private static Primordial.Kind primordialKind(CommandContext<CommandSourceStack> c) {
        Primordial.Kind k = Primordial.kind(StringArgumentType.getString(c, "faction"));
        if (k == null) throw new FactionException("Seules les factions « dragonnier » et « chasseur » ont un dragon primordial.");
        return k;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> primordial() {
        return literal("primordial")
                .then(literal("creer").then(argument("faction", StringArgumentType.word()).suggests(PRIMORDIAL_FACTIONS)
                        .executes(c -> run(c, (cc, rt) -> spawnPrimordial(cc, rt, null)))
                        .then(argument("proprietaire", StringArgumentType.word()).suggests(PLAYERS)
                                .executes(c -> run(c, (cc, rt) -> {
                                    ServerPlayer owner = rt.server().getPlayerList().getPlayerByName(StringArgumentType.getString(cc, "proprietaire"));
                                    if (owner == null) return fail(cc, "Le propriétaire doit être connecté.");
                                    return spawnPrimordial(cc, rt, owner);
                                })))))
                .then(literal("marquer").then(argument("faction", StringArgumentType.word()).suggests(PRIMORDIAL_FACTIONS)
                        .executes(c -> run(c, (cc, rt) -> {
                            if (!(cc.getSource().getEntity() instanceof ServerPlayer p)) return fail(cc, "Commande réservée aux joueurs.");
                            Entity d = lookedDragon(p);
                            if (d == null) return fail(cc, "Regardez un dragon (à moins de 24 blocs) ou montez-le.");
                            Primordial.Kind k = primordialKind(cc);
                            try { Primordial.mark(d, k); }
                            catch (IllegalArgumentException e) { return fail(cc, e.getMessage()); }
                            rt.bonds().sync(d);
                            return ok(cc, "Ce dragon est désormais le " + k.name() + " (réservé au Primodiau de « " + k.factionId() + " »).");
                        }))))
                .then(literal("retirer").executes(c -> run(c, (cc, rt) -> {
                    if (!(cc.getSource().getEntity() instanceof ServerPlayer p)) return fail(cc, "Commande réservée aux joueurs.");
                    Entity d = lookedDragon(p);
                    if (d == null || Primordial.of(d) == null) return fail(cc, "Regardez un dragon primordial.");
                    Primordial.unmark(d);
                    rt.bonds().sync(d);
                    return ok(cc, "Ce dragon redevient un dragon ordinaire.");
                })))
                .then(literal("liste").executes(c -> run(c, (cc, rt) -> {
                    line(cc, "== Dragons primordiaux ==", ChatFormatting.GOLD);
                    int n = 0;
                    for (DragonRecord d : rt.service().dragons()) {
                        if (d.primordial().isBlank()) continue;
                        n++;
                        line(cc, d.displayName() + " — " + d.primordial() + " — propriétaire : " + (d.owner() == null ? "aucun" : rt.nameOf(d.owner()))
                                + " — " + d.dimension() + " " + d.x() + " " + d.y() + " " + d.z(), ChatFormatting.YELLOW);
                    }
                    if (n == 0) line(cc, "Aucun.", ChatFormatting.GRAY);
                    return 1;
                })));
    }

    private static int spawnPrimordial(CommandContext<CommandSourceStack> c, FactionRuntime rt, ServerPlayer owner) {
        if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return fail(c, "Commande réservée aux joueurs.");
        Primordial.Kind k = primordialKind(c);
        Entity d;
        try { d = Primordial.spawn(p, k, owner); }
        catch (IllegalStateException | IllegalArgumentException e) { return fail(c, e.getMessage()); }
        rt.bonds().sync(d);
        return ok(c, k.name() + " invoqué" + (owner == null ? " (sauvage)" : " pour " + owner.getGameProfile().getName()) + ".");
    }

    private static LiteralArgumentBuilder<CommandSourceStack> grades() {
        return literal("grades").then(argument("faction", StringArgumentType.word()).suggests(FACTIONS)
                .executes(c -> run(c, (cc, rt) -> {
                    Faction f = rt.service().requireFaction(StringArgumentType.getString(cc, "faction"));
                    line(cc, "== Grades de " + f.name() + " (du plus haut au plus bas) ==", ChatFormatting.GOLD);
                    int i = 1;
                    for (Grade g : f.grades()) {
                        StringJoiner sj = new StringJoiner(", ");
                        for (FactionPermission p : g.perms()) sj.add(p.name());
                        line(cc, (i++) + ". " + g.name() + " [" + g.id() + "] : " + (sj.length() == 0 ? "aucun droit" : sj), ChatFormatting.YELLOW);
                    }
                    return 1;
                }))
                .then(literal("creer").then(argument("nom", StringArgumentType.greedyString()).executes(c -> run(c, (cc, rt) -> {
                    Grade g = rt.service().createGrade(StringArgumentType.getString(cc, "faction"), StringArgumentType.getString(cc, "nom"));
                    return ok(cc, "Grade « " + g.name() + " » créé [" + g.id() + "], placé en bas de la hiérarchie.");
                }))))
                .then(literal("renommer").then(argument("grade", StringArgumentType.word()).suggests(GRADES)
                        .then(argument("nom", StringArgumentType.greedyString()).executes(c -> run(c, (cc, rt) -> {
                            rt.service().renameGrade(StringArgumentType.getString(cc, "faction"), StringArgumentType.getString(cc, "grade"), StringArgumentType.getString(cc, "nom"));
                            return ok(cc, "Grade renommé.");
                        })))))
                .then(literal("supprimer").then(argument("grade", StringArgumentType.word()).suggests(GRADES).executes(c -> run(c, (cc, rt) -> {
                    rt.service().deleteGrade(StringArgumentType.getString(cc, "faction"), StringArgumentType.getString(cc, "grade"));
                    return ok(cc, "Grade supprimé ; ses membres sont passés au grade suivant.");
                }))))
                .then(literal("monter").then(argument("grade", StringArgumentType.word()).suggests(GRADES).executes(c -> run(c, (cc, rt) -> {
                    rt.service().moveGrade(StringArgumentType.getString(cc, "faction"), StringArgumentType.getString(cc, "grade"), true);
                    return ok(cc, "Grade monté d'un rang.");
                }))))
                .then(literal("descendre").then(argument("grade", StringArgumentType.word()).suggests(GRADES).executes(c -> run(c, (cc, rt) -> {
                    rt.service().moveGrade(StringArgumentType.getString(cc, "faction"), StringArgumentType.getString(cc, "grade"), false);
                    return ok(cc, "Grade descendu d'un rang.");
                }))))
                .then(literal("permission").then(argument("grade", StringArgumentType.word()).suggests(GRADES)
                        .then(argument("permission", StringArgumentType.word()).suggests(PERMS)
                                .then(argument("active", BoolArgumentType.bool()).executes(c -> run(c, (cc, rt) -> {
                                    FactionPermission p = FactionPermission.parse(StringArgumentType.getString(cc, "permission"));
                                    boolean on = BoolArgumentType.getBool(cc, "active");
                                    rt.service().setPermission(StringArgumentType.getString(cc, "faction"), StringArgumentType.getString(cc, "grade"), p, on);
                                    return ok(cc, "Permission « " + p.label() + " » " + (on ? "accordée." : "retirée."));
                                })))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> emblems() {
        return literal("emblemes")
                .executes(c -> run(c, (cc, rt) -> {
                    line(cc, "== Emblèmes ==", ChatFormatting.GOLD);
                    for (Emblem e : rt.service().emblems())
                        line(cc, e.name() + " [" + e.id() + "] — " + e.shape().label() + ", " + e.pattern().label()
                                + ", #" + Emblem.hex(e.primary()) + " / #" + Emblem.hex(e.secondary()) + (e.symbol().isEmpty() ? "" : ", « " + e.symbol() + " »"), ChatFormatting.YELLOW);
                    return 1;
                }))
                .then(literal("creer").then(argument("id", StringArgumentType.word()).then(argument("nom", StringArgumentType.greedyString())
                        .executes(c -> run(c, (cc, rt) -> {
                            Emblem e = rt.service().createEmblem(StringArgumentType.getString(cc, "id"), StringArgumentType.getString(cc, "nom"));
                            return ok(cc, "Emblème « " + e.name() + " » créé. Personnalisez-le dans /factions admin (Emblèmes) ou avec les sous-commandes.");
                        })))))
                .then(literal("couleurs").then(argument("embleme", StringArgumentType.word()).suggests(EMBLEMS)
                        .then(argument("principale", StringArgumentType.word()).then(argument("secondaire", StringArgumentType.word())
                                .executes(c -> run(c, (cc, rt) -> {
                                    rt.service().editEmblem(StringArgumentType.getString(cc, "embleme"), null, null, null,
                                            StringArgumentType.getString(cc, "principale"), StringArgumentType.getString(cc, "secondaire"), null);
                                    return ok(cc, "Couleurs modifiées.");
                                }))))))
                .then(literal("forme").then(argument("embleme", StringArgumentType.word()).suggests(EMBLEMS)
                        .then(argument("forme", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(Emblem.Shape.values()), b))
                                .executes(c -> run(c, (cc, rt) -> {
                                    rt.service().editEmblem(StringArgumentType.getString(cc, "embleme"), null, StringArgumentType.getString(cc, "forme"), null, null, null, null);
                                    return ok(cc, "Forme modifiée.");
                                })))))
                .then(literal("motif").then(argument("embleme", StringArgumentType.word()).suggests(EMBLEMS)
                        .then(argument("motif", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(Emblem.Pattern.values()), b))
                                .executes(c -> run(c, (cc, rt) -> {
                                    rt.service().editEmblem(StringArgumentType.getString(cc, "embleme"), null, null, StringArgumentType.getString(cc, "motif"), null, null, null);
                                    return ok(cc, "Motif modifié.");
                                })))))
                .then(literal("symbole").then(argument("embleme", StringArgumentType.word()).suggests(EMBLEMS)
                        .then(argument("symbole", StringArgumentType.word()).executes(c -> run(c, (cc, rt) -> {
                            rt.service().editEmblem(StringArgumentType.getString(cc, "embleme"), null, null, null, null, null, StringArgumentType.getString(cc, "symbole"));
                            return ok(cc, "Symbole modifié.");
                        })))))
                .then(literal("supprimer").then(argument("embleme", StringArgumentType.word()).suggests(EMBLEMS).executes(c -> run(c, (cc, rt) -> {
                    rt.service().deleteEmblem(StringArgumentType.getString(cc, "embleme"));
                    return ok(cc, "Emblème supprimé.");
                }))));
    }

    // ================================================================== utilitaires

    private static int add(CommandContext<CommandSourceStack> c, FactionRuntime rt, String grade) {
        UiEnv.PlayerRef ref = player(c, rt);
        Faction f = rt.service().requireFaction(StringArgumentType.getString(c, "faction"));
        Member m = rt.service().addMember(f.id(), ref.uuid(), ref.name(), grade);
        notify(rt, ref, "Vous avez été ajouté à la faction « " + f.name() + " ».");
        return ok(c, m.name() + " ajouté à « " + f.name() + " » (" + f.grade(m.gradeId()).name() + ").");
    }

    private static UiEnv.PlayerRef player(CommandContext<CommandSourceStack> c, FactionRuntime rt) {
        String name = StringArgumentType.getString(c, "joueur");
        UiEnv.PlayerRef ref = rt.resolve(name);
        if (ref == null) throw new FactionException("Joueur inconnu : « " + name + " » (il doit s'être déjà connecté au serveur).");
        return ref;
    }

    private static void notify(FactionRuntime rt, UiEnv.PlayerRef ref, String msg) {
        ServerPlayer p = rt.server().getPlayerList().getPlayer(ref.uuid());
        if (p != null) rt.message(p, msg);
    }

    private static int dragonInfo(CommandContext<CommandSourceStack> c, FactionRuntime rt, DragonRecord d) {
        line(c, "== " + d.displayName() + " ==", ChatFormatting.GOLD);
        line(c, "UUID : " + d.uuid(), ChatFormatting.GRAY);
        line(c, d.speciesLabel() + " — " + d.variant() + (d.corrupted() ? " (CORROMPU)" : " (non corrompu)") + " — stade " + d.stage(), ChatFormatting.YELLOW);
        if (!d.primordial().isBlank()) line(c, "★ " + Primordial.label(d.primordial()) + " (faction " + d.primordial() + ")", ChatFormatting.LIGHT_PURPLE);
        line(c, "Propriétaire : " + (d.owner() == null ? "aucun (sauvage)" : rt.nameOf(d.owner())), ChatFormatting.YELLOW);
        line(c, "Position : " + d.dimension() + " " + d.x() + " " + d.y() + " " + d.z() + " (" + date(d.lastSeen()) + ")", ChatFormatting.GRAY);
        if (d.bonds().isEmpty()) line(c, "Aucun lien.", ChatFormatting.GRAY);
        for (Map.Entry<UUID, Bond> e : d.bonds().entrySet()) {
            double v = BondMath.effective(e.getValue(), System.currentTimeMillis(), Config.bond());
            line(c, " - " + rt.nameOf(e.getKey()) + " : " + BondMath.percent(v) + " (" + BondMath.tier(v) + "), dernière monte " + date(e.getValue().lastRide()),
                    ChatFormatting.YELLOW);
        }
        return 1;
    }

    private static String dragonText(DragonRecord d, UUID player) {
        double v = BondMath.effective(d.bond(player), System.currentTimeMillis(), Config.bond());
        return d.displayName() + (d.corrupted() ? " (corrompu)" : "") + " — lien " + BondMath.percent(v) + " — " + d.uuid();
    }

    /** Dragon monte, ou dragon le plus proche dans l'axe du regard (24 blocs). */
    private static Entity lookedDragon(ServerPlayer p) {
        if (p.getVehicle() != null && DragonBridge.isDragon(p.getVehicle())) return p.getVehicle();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(24), DragonBridge::isDragon)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist < 0.01) continue;
            double dot = to.normalize().dot(look);
            if (dot < 0.9) continue;
            double score = dist * (2 - dot);
            if (score < bestScore) { bestScore = score; best = e; }
        }
        return best;
    }

    private static List<String> names(Enum<?>[] values) {
        List<String> l = new ArrayList<>();
        for (Enum<?> v : values) l.add(v.name());
        return l;
    }

    private static String date(long ms) {
        if (ms <= 0) return "—";
        return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(java.time.ZoneId.of("Europe/Paris")).format(java.time.Instant.ofEpochMilli(ms));
    }

    private static int open(CommandContext<CommandSourceStack> c, String view) {
        if (!(c.getSource().getEntity() instanceof ServerPlayer p)) return fail(c, "Commande réservée aux joueurs.");
        FactionRuntime rt = FactionRuntime.current();
        if (rt == null) return fail(c, "Les factions ne sont pas disponibles.");
        rt.open(p, view, Map.of());
        return 1;
    }

    private static int run(CommandContext<CommandSourceStack> c, Body body) {
        FactionRuntime rt = FactionRuntime.current();
        if (rt == null) return fail(c, "Les factions ne sont pas disponibles.");
        try {
            return body.run(c, rt);
        } catch (FactionException e) {
            return fail(c, e.getMessage());
        }
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static void line(CommandContext<CommandSourceStack> c, String msg, ChatFormatting color) {
        c.getSource().sendSuccess(() -> Component.literal(msg).withStyle(color), false);
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }
}
