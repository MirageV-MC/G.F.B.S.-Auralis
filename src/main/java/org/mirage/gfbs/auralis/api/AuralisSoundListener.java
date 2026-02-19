package org.mirage.gfbs.auralis.api;

@FunctionalInterface
public interface AuralisSoundListener {
    void onSoundEvent(AuralisSoundInstance instance, AuralisSoundEvent event);
}