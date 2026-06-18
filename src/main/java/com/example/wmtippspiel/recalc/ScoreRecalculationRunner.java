package com.example.wmtippspiel.recalc;

import com.example.wmtippspiel.evaluation.RecalculationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stößt die rückwirkende Punkte-Neuberechnung einmalig beim App-Start an
 * (FR-008a) — ohne zusätzliche Bedienoberfläche. Da {@link RecalculationService}
 * idempotent ist, ist ein Lauf bei jedem Start unschädlich; abschaltbar über
 * {@code app.scoring.recalc-on-startup=false}.
 *
 * <p>Läuft als {@link ApplicationRunner} nach abgeschlossener Liquibase-Migration
 * und Context-Initialisierung.
 */
@Component
@ConditionalOnProperty(value = "app.scoring.recalc-on-startup", matchIfMissing = true)
public class ScoreRecalculationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScoreRecalculationRunner.class);

    private final RecalculationService recalculation;

    public ScoreRecalculationRunner(RecalculationService recalculation) {
        this.recalculation = recalculation;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starte rückwirkende Punkte-Neuberechnung nach aktuellem Schema …");
        recalculation.recalculateAll();
    }
}
