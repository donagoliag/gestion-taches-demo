package com.priscille.gestiontaches.rdf;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.stereotype.Component;

@Component
public class OntologyLoader {

    private final Model model;
    private static final String ONTO_PATH = "rdf/ontologie.ttl";
    private static final String DATA_PATH = "rdf/data.ttl";

    public OntologyLoader() {
        model = ModelFactory.createDefaultModel();
        // Charger ontologie
        RDFDataMgr.read(model, ONTO_PATH);
        // Charger données si présentes
        try {
            RDFDataMgr.read(model, DATA_PATH);
        } catch (Exception ignored) {}
    }

    public Model getModel() {
        return model;
    }
}
