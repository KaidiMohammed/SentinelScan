package com.sentinelscan.worker;

/**
 * Une vérification de sécurité réelle. Phase 1 : aucune n'est enregistrée.
 * Une implémentation doit exécuter une vraie requête contre la cible et ne jamais simuler de résultat.
 */
public interface ScanCheck {
    String key();

    void run(java.util.UUID jobId, String targetUrl) throws Exception;
}
