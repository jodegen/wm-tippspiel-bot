package com.example.wmtippspiel.discord.components;

import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import org.springframework.stereotype.Component;

/**
 * Navigations-Komponente unter dem Board (F7, Stufe 2): ein Select-Menu mit
 * Filtern. Auswahl löst eine ephemerale Antwort aus (BoardFilterHandler), das
 * öffentliche Board bleibt unverändert (FR-025/026).
 */
@Component
public class BoardNavigation {

    public static final String FILTER_ID = "board:filter";

    public ActionRow actionRow() {
        StringSelectMenu.Builder menu = StringSelectMenu.create(FILTER_ID)
                .setPlaceholder("Ansicht filtern …")
                .addOption("Heute", "today")
                .addOption("Morgen", "tomorrow")
                .addOption("K.o.-Runde", "ko");
        for (char g = 'A'; g <= 'L'; g++) {
            menu.addOption("Gruppe " + g, "group:" + g);
        }
        return ActionRow.of(menu.build());
    }
}
