package com.sentinelscan.scan;

/** Statut vrai d'une vérification réellement enregistrée : running, finding, clean ou not_checked (+ raison). */
public record ScanCheckRow(String checkKey, String status, String reason) {
}
