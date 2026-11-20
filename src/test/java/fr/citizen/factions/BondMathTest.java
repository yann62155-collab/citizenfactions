package fr.citizen.factions;

import fr.citizen.factions.core.Bond;
import fr.citizen.factions.core.BondMath;
import fr.citizen.factions.core.BondSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BondMathTest {
    final BondSettings s = BondSettings.DEFAULT; // 0,75 / min, -1 / h apres 12 h
    final long H = 3_600_000L;

    @Test
    void ridingIncreasesAndCapsAt100() {
        Bond b = Bond.NONE;
        long t = 1_000_000L;
        for (int i = 0; i < 60; i++) { t += 1000; b = BondMath.ride(b, t, 1000, s); }
        assertEquals(0.75, b.value(), 1e-6);
        b = new Bond(99.9, t, t);
        b = BondMath.ride(b, t + 60_000, 60_000, s);
        assertEquals(100, b.value(), 1e-9);
    }

    @Test
    void abandonDecaysAfterGraceAndSurvivesTime() {
        long t = 10 * H;
        Bond b = new Bond(80, t, t);
        assertEquals(80, BondMath.effective(b, t + 11 * H, s), 1e-9);   // dans le delai de grace
        assertEquals(78, BondMath.effective(b, t + 14 * H, s), 1e-9);   // 2 h apres la grace
        assertEquals(0, BondMath.effective(b, t + 500 * H, s), 1e-9);   // jamais negatif
        Bond again = BondMath.ride(b, t + 14 * H, 0, s);                // remonter repart de la valeur reelle
        assertEquals(78, again.value(), 1e-9);
    }

    @Test
    void behaviourIsProgressive() {
        assertTrue(BondMath.refuseChance(0, s) > BondMath.refuseChance(30, s));
        assertEquals(0, BondMath.refuseChance(60, s));
        assertTrue(BondMath.jerkChancePerSecond(5) > BondMath.jerkChancePerSecond(40));
        assertEquals(0, BondMath.jerkChancePerSecond(80));
        assertTrue(BondMath.speedModifier(0) < 0 && BondMath.speedModifier(100) > 0);
        assertEquals(0, BondMath.regenPerSecond(50));
        assertTrue(BondMath.regenPerSecond(100) > 0);
        assertEquals("Lié à vie", BondMath.tier(100));
    }
}
