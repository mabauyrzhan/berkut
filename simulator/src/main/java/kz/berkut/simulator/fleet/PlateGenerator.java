package kz.berkut.simulator.fleet;

import java.util.Random;

/**
 * Generates KZ-style plates: NNN-LLL-RR (3 digits, 3 letters, region code).
 * Region 02 = Almaty city. Letter set restricted to glyphs that look identical in
 * Cyrillic and Latin (the same set used on real plates).
 */
public class PlateGenerator {
    private static final char[] LETTERS = {'A', 'B', 'C', 'E', 'H', 'K', 'M', 'O', 'P', 'T', 'X', 'Y'};
    private static final String ALMATY_REGION = "02";

    private final Random random;

    public PlateGenerator(Random random) {
        this.random = random;
    }

    public String next() {
        int digits = 1 + random.nextInt(999);  // 001..999
        char[] letters = new char[3];
        for (int i = 0; i < 3; i++) {
            letters[i] = LETTERS[random.nextInt(LETTERS.length)];
        }
        return String.format("%03d-%s-%s", digits, new String(letters), ALMATY_REGION);
    }
}
