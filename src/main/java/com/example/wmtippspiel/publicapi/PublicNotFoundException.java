package com.example.wmtippspiel.publicapi;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Signalisiert eine unbekannte öffentliche Ressource (Spiel/Profil). Wird vom
 * Web-Layer als HTTP 404 ohne interne Details beantwortet (FR-019). Die Nachricht
 * ist bewusst neutral gehalten und gibt keine internen IDs preis.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PublicNotFoundException extends RuntimeException {

    public PublicNotFoundException() {
        super("not found");
    }
}
