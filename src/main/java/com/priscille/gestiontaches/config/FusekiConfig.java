package com.priscille.gestiontaches.config;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FusekiConfig {

    @Bean
    public FusekiServer fusekiServer() {
        System.out.println("🚀 Démarrage de Fuseki en mode DÉMO");
        System.out.println("📌 IMPORTANT : Mode mémoire uniquement pour Render");

        // 1. Créer un dataset EN MÉMOIRE (pas de fichier)
        Dataset dataset = DatasetFactory.createTxnMem();

        // 2. Configurer le serveur Fuseki
        FusekiServer server = FusekiServer.create()
                .add("/ds", dataset)  // Endpoint SPARQL
                .port(3030)           // Port interne
                .build();

        // 3. Démarrer le serveur
        server.start();

        System.out.println("✅ Fuseki démarré sur le port 3030");
        System.out.println("📊 Dataset : En mémoire (éphémère)");
        System.out.println("🔗 Endpoint : http://localhost:3030/ds");

        return server;
    }
}