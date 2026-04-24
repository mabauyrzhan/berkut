package kz.berkut.simulator.fleet;

import java.util.List;
import java.util.Random;

public final class DriverNames {

    private static final List<String> FIRST_NAMES = List.of(
            "Adlet", "Aibek", "Akhat", "Aldiyar", "Alibek", "Almas", "Alpamys", "Anuar",
            "Arman", "Askar", "Azamat", "Berik", "Bolat", "Daniyar", "Dauren", "Dias",
            "Dosjan", "Erlan", "Galymzhan", "Ilyas", "Kairat", "Kanat", "Kasym",
            "Marat", "Maxat", "Medet", "Nurlan", "Nurzhan", "Olzhas", "Rashid",
            "Ruslan", "Sabit", "Saken", "Samat", "Serik", "Talgat", "Tanat",
            "Temirlan", "Timur", "Yerassyl", "Yerbol", "Yerlan", "Zhandos"
    );

    private static final List<String> LAST_NAMES = List.of(
            "Abdrakhmanov", "Akhmetov", "Aliyev", "Amangeldy", "Bayazitov", "Bekenov",
            "Beysenov", "Daribaev", "Eskaliev", "Ermekov", "Iskakov", "Issayev",
            "Kabdrakhmanov", "Karibaev", "Kasymov", "Kenzhebekov", "Kerimov",
            "Khassenov", "Kuanyshev", "Kudaibergenov", "Kulibayev", "Kurmanov",
            "Maksutov", "Mukanov", "Murzin", "Nazarov", "Nurpeisov", "Omarov",
            "Orazbayev", "Rakhimov", "Sagatov", "Sapayev", "Satpayev", "Seitov",
            "Sultanov", "Suleimenov", "Tashkentbayev", "Toleubekov", "Turlybekov",
            "Yerzhanov", "Zharkynbekov", "Zhumagaliyev"
    );

    private DriverNames() {}

    public static String random(Random random) {
        return FIRST_NAMES.get(random.nextInt(FIRST_NAMES.size())) + " "
                + LAST_NAMES.get(random.nextInt(LAST_NAMES.size()));
    }
}
