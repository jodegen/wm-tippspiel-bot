package com.example.wmtippspiel.kickoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.example.wmtippspiel.discord.publish.AnnounceChannel;
import com.example.wmtippspiel.discord.render.KickoffNoticeEmbed;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;
import com.example.wmtippspiel.persistence.KickoffNoticeLogRepository;
import com.example.wmtippspiel.persistence.MatchRepository;

import net.dv8tion.jda.api.entities.MessageEmbed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Tests des Anpfiff-Hinweises: genau einmal pro Spiel, nur für noch nicht gestartete Spiele. */
class KickoffNoticeServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-14T20:50:00Z");

    private MatchRepository matches;
    private KickoffNoticeLogRepository noticeLog;
    private KickoffNoticeEmbed embed;
    private AnnounceChannel announceChannel;
    private KickoffNoticeService service;

    @BeforeEach
    void setUp() {
        matches = Mockito.mock(MatchRepository.class);
        noticeLog = Mockito.mock(KickoffNoticeLogRepository.class);
        embed = Mockito.mock(KickoffNoticeEmbed.class);
        announceChannel = Mockito.mock(AnnounceChannel.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new KickoffNoticeService(matches, noticeLog, embed, announceChannel, clock, 10);
        when(embed.build(any())).thenReturn(Mockito.mock(MessageEmbed.class));
    }

    @Test
    @DisplayName("Postet einen Hinweis für ein bald anstehendes, noch nicht gemeldetes Spiel")
    void postsNoticeForUpcomingMatch() {
        Match soon = match(1L, NOW.plusSeconds(300), MatchStatus.SCHEDULED); // in 5 Min
        when(matches.findBetween(eq(NOW), eq(NOW.plusSeconds(600)))).thenReturn(List.of(soon));
        when(noticeLog.wasNotified(1L)).thenReturn(false);

        assertThat(service.notifyUpcomingKickoffs()).isEqualTo(1);
        verify(announceChannel).post(any());
        verify(noticeLog).markNotified(eq(1L), eq(NOW));
    }

    @Test
    @DisplayName("Postet nicht erneut, wenn der Hinweis schon raus ist (Idempotenz)")
    void doesNotPostTwice() {
        Match soon = match(1L, NOW.plusSeconds(300), MatchStatus.SCHEDULED);
        when(matches.findBetween(eq(NOW), eq(NOW.plusSeconds(600)))).thenReturn(List.of(soon));
        when(noticeLog.wasNotified(1L)).thenReturn(true);

        assertThat(service.notifyUpcomingKickoffs()).isZero();
        verify(announceChannel, never()).post(any());
        verify(noticeLog, never()).markNotified(anyLong(), any());
    }

    @Test
    @DisplayName("Ignoriert bereits laufende Spiele im Fenster")
    void ignoresInPlayMatches() {
        Match running = match(1L, NOW.plusSeconds(60), MatchStatus.IN_PLAY);
        when(matches.findBetween(eq(NOW), eq(NOW.plusSeconds(600)))).thenReturn(List.of(running));

        assertThat(service.notifyUpcomingKickoffs()).isZero();
        verify(announceChannel, never()).post(any());
    }

    private static Match match(long id, Instant kickoff, MatchStatus status) {
        return new Match(id, "Team A", "Team B", kickoff, Stage.GROUP_STAGE, "A", null,
                null, null, null, null, null, status, false, false);
    }
}
