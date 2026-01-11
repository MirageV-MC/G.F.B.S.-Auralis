package org.mirage.gfbs_auralis.api;

@FunctionalInterface
public interface AuralisSoundListener {
    void onSoundEvent(AuralisSoundInstance instance, AuralisSoundEvent event);
}