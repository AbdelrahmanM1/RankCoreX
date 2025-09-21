package dev.abdelrahman.rankcorex.models;

import dev.abdelrahman.rankcorex.utils.TimeUtils;

import java.util.UUID;

public class PlayerRankData {

    private final UUID playerId;
    private final String playerName;
    private final String rankName;
    private final String timeGiven;
    private final String timeExpires;

    public PlayerRankData(UUID playerId, String playerName, String rankName, String timeGiven, String timeExpires) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.rankName = rankName;
        this.timeGiven = timeGiven;
        this.timeExpires = timeExpires;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getRankName() {
        return rankName;
    }

    public String getTimeGiven() {
        return timeGiven;
    }

    public String getTimeExpires() {
        return timeExpires;
    }

    public boolean isPermanent() {
        return timeExpires == null || timeExpires.isEmpty();
    }

    public boolean hasExpired() {
        return TimeUtils.hasExpired(timeExpires);
    }

    public String getTimeRemaining() {
        return TimeUtils.getTimeRemaining(timeExpires);
    }

    public String getTimeSinceGiven() {
        return TimeUtils.getTimeSince(timeGiven);
    }

    @Override
    public String toString() {
        return "PlayerRankData{" +
                "playerId=" + playerId +
                ", playerName='" + playerName + '\'' +
                ", rankName='" + rankName + '\'' +
                ", permanent=" + isPermanent() +
                ", expired=" + hasExpired() +
                '}';
    }
}