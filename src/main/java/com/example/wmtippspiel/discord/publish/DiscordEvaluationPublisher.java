package com.example.wmtippspiel.discord.publish;

import java.util.List;

import com.example.wmtippspiel.discord.render.EvaluationEmbed;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.evaluation.EvaluationPublisher;
import com.example.wmtippspiel.evaluation.ScoredTip;

import org.springframework.stereotype.Component;

/** Discord-Umsetzung der Auswertungs-Veröffentlichung (postet ins Announce-Channel). */
@Component
public class DiscordEvaluationPublisher implements EvaluationPublisher {

    private final AnnounceChannel announceChannel;
    private final EvaluationEmbed evaluationEmbed;

    public DiscordEvaluationPublisher(AnnounceChannel announceChannel, EvaluationEmbed evaluationEmbed) {
        this.announceChannel = announceChannel;
        this.evaluationEmbed = evaluationEmbed;
    }

    @Override
    public void publishEvaluation(Match match, List<ScoredTip> scoredTips, boolean correction) {
        announceChannel.post(evaluationEmbed.build(match, scoredTips, correction));
    }
}
