package com.example.wmtippspiel.kickoff;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.example.wmtippspiel.discord.publish.AnnounceChannel;
import com.example.wmtippspiel.discord.render.KickoffNoticeEmbed;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.persistence.KickoffNoticeLogRepository;
import com.example.wmtippspiel.persistence.MatchRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Postet kurz vor Anpfiff (Standard 10 Min) einen Hinweis in den Announce-Channel
 * und pingt dabei die WM-Notify-Rolle (über {@link AnnounceChannel}). Pro Spiel
 * genau einmal (Idempotenz über {@link KickoffNoticeLogRepository}).
 */
@Service
public class KickoffNoticeService {

    private static final Logger log = LoggerFactory.getLogger(KickoffNoticeService.class);

    private final MatchRepository matches;
    private final KickoffNoticeLogRepository noticeLog;
    private final KickoffNoticeEmbed embed;
    private final AnnounceChannel announceChannel;
    private final Clock clock;
    private final long leadMinutes;

    public KickoffNoticeService(MatchRepository matches,
                                KickoffNoticeLogRepository noticeLog,
                                KickoffNoticeEmbed embed,
                                AnnounceChannel announceChannel,
                                Clock clock,
                                @Value("${app.kickoff-notice.lead-minutes:10}") long leadMinutes) {
        this.matches = matches;
        this.noticeLog = noticeLog;
        this.embed = embed;
        this.announceChannel = announceChannel;
        this.clock = clock;
        this.leadMinutes = leadMinutes;
    }

    /** Postet fällige Anpfiff-Hinweise und gibt deren Anzahl zurück. */
    public int notifyUpcomingKickoffs() {
        Instant now = clock.instant();
        Instant until = now.plus(Duration.ofMinutes(leadMinutes));
        int posted = 0;
        for (Match match : matches.findBetween(now, until)) {
            if (!isUpcoming(match, now) || noticeLog.wasNotified(match.id())) {
                continue;
            }
            announceChannel.post(embed.build(match));
            noticeLog.markNotified(match.id(), now);
            posted++;
            log.info("Anpfiff-Hinweis für Spiel {} gepostet", match.id());
        }
        return posted;
    }

    private boolean isUpcoming(Match match, Instant now) {
        return match.kickoff().isAfter(now)
                && (match.status() == MatchStatus.SCHEDULED || match.status() == MatchStatus.POSTPONED);
    }
}
