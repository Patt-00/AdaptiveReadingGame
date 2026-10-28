package com.adaptivereadinggame.model;

/** Cosmetic servant appearances; both use the same story and scoring. */
public enum PlayerAppearance {
    A("Servant_A"), B("Servant_B");

    private final String assetKey;
    PlayerAppearance(String assetKey) { this.assetKey = assetKey; }
    public String assetKey() { return assetKey; }
}
