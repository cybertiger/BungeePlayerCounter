/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.Alphart.BungeePlayerCounter.Servers;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 *
 * @author antony
 */
@Getter
@ToString
public class PingResponse {
    private String description;
    private Players players;
    private Version version;
    private String favicon;
    @Setter
    private long time;
    private final Pinger outer;

    public PingResponse(final Pinger outer) {
        this.outer = outer;
    }

    public boolean isFull() {
        return players.max <= players.online;
    }

    @Getter
    @ToString
    public class Players {

        private int max;
        private int online;
        private List<Player> sample;

        @Getter
        public class Player {

            private String name;
            private String id;
        }
    }

    @Getter
    @ToString
    public class Version {

        private String name;
        private String protocol;
    }
    
}
