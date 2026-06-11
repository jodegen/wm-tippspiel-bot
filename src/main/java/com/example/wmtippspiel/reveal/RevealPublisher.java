package com.example.wmtippspiel.reveal;

import java.util.List;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.Tip;

/**
 * Veröffentlicht die Offenlegung der Tipps eines Spiels. Als Interface gehalten,
 * damit die Reveal-Kernlogik ohne Discord-Abhängigkeit test-first geprüft werden
 * kann (Verfassung Prinzip III).
 */
public interface RevealPublisher {

    void publishReveal(Match match, List<Tip> tips);
}
