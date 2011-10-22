package edu.umich.eac;

public enum PrefetchStrategyType {
    AGGRESSIVE,
    CONSERVATIVE,
    /* ... */
    ADAPTIVE, // this is ours
    SIZE_LIMIT,
    /* ... */
    NUM_STRATEGIES
}
