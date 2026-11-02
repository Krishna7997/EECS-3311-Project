package com.maplecfo.planning;

/** The debt payoff methods the user can choose (F08). */
public enum PayoffMethod {
    AVALANCHE,
    SNOWBALL;

    public PayoffStrategy toStrategy() {
        return this == AVALANCHE ? new AvalancheStrategy() : new SnowballStrategy();
    }
}
