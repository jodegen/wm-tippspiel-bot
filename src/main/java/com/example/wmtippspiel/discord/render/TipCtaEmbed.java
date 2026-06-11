package com.example.wmtippspiel.discord.render;

import java.awt.Color;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/** Kurzes Call-to-Action-Embed für den Tipp-Channel (begleitet den "Jetzt tippen"-Button). */
@Component
public class TipCtaEmbed {

    public MessageEmbed build() {
        return new EmbedBuilder()
                .setColor(new Color(0x57F287))
                .setTitle("⚽  Jetzt tippen!")
                .setDescription("""
                        Klick auf **„⚽ Jetzt tippen"**, wähle ein Spiel und gib dein Ergebnis ein.

                        🔒 Nur **du** siehst deine Eingabe — bis zum Anpfiff beliebig änderbar.
                        🎯 **3** exakt · ✅ **1** Tendenz · ❌ **0** daneben""")
                .build();
    }
}
