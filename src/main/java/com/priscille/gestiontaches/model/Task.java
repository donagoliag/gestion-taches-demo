package com.priscille.gestiontaches.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Task {
    // URI complète de la ressource RDF (ex: "http://www.example.org/ontologie/gestion-taches#task_abc123")
    private String id;

    // Propriétés simples (DatatypeProperties)
    private String titre;
    private String description;
    private LocalDateTime dateCreation;
    private LocalDateTime deadline;
    private LocalDateTime dateFin;           // AJOUT pour tm:dateFin
    private boolean estTerminee;
    private String terminationCause;        // AJOUT pour tm:terminationCause
    private String warning;                 // AJOUT pour tm:warning (anciennement alert)

    // Références à d'autres ressources (ObjectProperties) - stockées comme URIs
    private String statutUri;               // Référence à tm:AFaire, tm:EnCours, etc.
    private String prioriteUri;             // Référence à tm:Urgente, tm:Haute, etc.
    private String categorieUri;            // Référence à tm:Travail, tm:Etudes, etc.
    private String assigneAUri;             // Référence à un tm:Utilisateur
    private String creeParUri;              // Référence à un tm:Utilisateur

    // Relations hiérarchiques
    private List<String> sousTachesUris;    // Liste d'URIs des sous-tâches (tm:aSousTache)
    private List<String> dependDeUris;      // Liste d'URIs des dépendances (tm:dependDe)

    // Données supplémentaires
    private List<Map<String, String>> attachments;
    private String urgence;                 // AJOUT pour tm:urgence

    // Constantes pour les statuts (ajoutées)
    public static final String STATUT_A_FAIRE = "http://www.example.org/ontologie/gestion-taches#AFaire";
    public static final String STATUT_EN_COURS = "http://www.example.org/ontologie/gestion-taches#EnCours";
    public static final String STATUT_TERMINEE = "http://www.example.org/ontologie/gestion-taches#Terminee";
    public static final String STATUT_EN_RETARD = "http://www.example.org/ontologie/gestion-taches#EnRetard";

    // ==================== CONSTRUCTEURS ====================
    public Task() {
        this.sousTachesUris = new ArrayList<>();
        this.dependDeUris = new ArrayList<>();
        this.attachments = new ArrayList<>();
        this.dateCreation = LocalDateTime.now();
        this.estTerminee = false;
    }

    public Task(String titre) {
        this();
        this.titre = titre;
    }

    // ==================== GETTERS & SETTERS ====================

    // --- Propriétés de base ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public LocalDateTime getDateFin() { return dateFin; }
    public void setDateFin(LocalDateTime dateFin) { this.dateFin = dateFin; }

    public boolean isEstTerminee() { return estTerminee; }
    public void setEstTerminee(boolean estTerminee) { this.estTerminee = estTerminee; }

    public String getTerminationCause() { return terminationCause; }
    public void setTerminationCause(String terminationCause) { this.terminationCause = terminationCause; }

    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }

    public String getUrgence() { return urgence; }
    public void setUrgence(String urgence) { this.urgence = urgence; }

    // --- Références par URI ---
    public String getStatutUri() { return statutUri; }
    public void setStatutUri(String statutUri) { this.statutUri = statutUri; }

    public String getPrioriteUri() { return prioriteUri; }
    public void setPrioriteUri(String prioriteUri) { this.prioriteUri = prioriteUri; }

    public String getCategorieUri() { return categorieUri; }
    public void setCategorieUri(String categorieUri) { this.categorieUri = categorieUri; }

    public String getAssigneAUri() { return assigneAUri; }
    public void setAssigneAUri(String assigneAUri) { this.assigneAUri = assigneAUri; }

    public String getCreeParUri() { return creeParUri; }
    public void setCreeParUri(String creeParUri) { this.creeParUri = creeParUri; }

    // --- Collections ---
    public List<String> getSousTachesUris() { return sousTachesUris; }
    public void setSousTachesUris(List<String> sousTachesUris) { this.sousTachesUris = sousTachesUris; }

    public List<String> getDependDeUris() { return dependDeUris; }
    public void setDependDeUris(List<String> dependDeUris) { this.dependDeUris = dependDeUris; }

    public List<Map<String, String>> getAttachments() { return attachments; }
    public void setAttachments(List<Map<String, String>> attachments) { this.attachments = attachments; }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Extrait le nom court d'une URI (après le #)
     * Ex: "http://.../Terminee" → "Terminee"
     */
    private String extractShortName(String uri) {
        if (uri == null) return null;
        return uri.contains("#") ?
                uri.substring(uri.lastIndexOf("#") + 1) :
                uri;
    }

    /**
     * Extrait l'ID de la tâche depuis son URI
     * Ex: "http://.../task_abc123" → "abc123"
     */
    public String getShortId() {
        if (id == null) return null;
        String shortName = extractShortName(id);
        return shortName.startsWith("task_") ?
                shortName.substring(5) : shortName;
    }

    // Getters pour les labels (noms courts)
    public String getStatutLabel() { return extractShortName(statutUri); }
    public String getPrioriteLabel() { return extractShortName(prioriteUri); }
    public String getCategorieLabel() { return extractShortName(categorieUri); }
    public String getAssigneALabel() { return extractShortName(assigneAUri); }
    public String getCreeParLabel() { return extractShortName(creeParUri); }

    /**
     * Vérifie si la tâche a des sous-tâches
     */
    public boolean hasSubtasks() {
        return sousTachesUris != null && !sousTachesUris.isEmpty();
    }

    /**
     * Vérifie si la tâche a des dépendances
     */
    public boolean hasDependencies() {
        return dependDeUris != null && !dependDeUris.isEmpty();
    }

    /**
     * Ajoute une sous-tâche par son URI
     */
    public void addSubtask(String subtaskUri) {
        if (sousTachesUris == null) {
            sousTachesUris = new ArrayList<>();
        }
        if (!sousTachesUris.contains(subtaskUri)) {
            sousTachesUris.add(subtaskUri);
        }
    }

    /**
     * Ajoute une dépendance par son URI
     */
    public void addDependency(String dependencyUri) {
        if (dependDeUris == null) {
            dependDeUris = new ArrayList<>();
        }
        if (!dependDeUris.contains(dependencyUri)) {
            dependDeUris.add(dependencyUri);
        }
    }

    /**
     * Marque la tâche comme terminée avec une cause
     */
    public void markAsCompleted(String cause) {
        this.estTerminee = true;
        this.terminationCause = cause;
        this.dateFin = LocalDateTime.now();

        // Mettre à jour le statut si nécessaire (utilise la constante)
        if (this.statutUri == null || !this.statutUri.contains("Terminee")) {
            this.statutUri = STATUT_TERMINEE;
        }
    }

    /**
     * Remet la tâche en cours (non terminée)
     * Ajouté pour gérer la règle de remise en cours
     */
    public void reopen() {
        this.estTerminee = false;
        this.terminationCause = null;
        this.dateFin = null;

        // Remettre le statut à "À faire" par défaut
        if (STATUT_TERMINEE.equals(this.statutUri)) {
            this.statutUri = STATUT_A_FAIRE;
        }
    }

    /**
     * Vérifie si la tâche est en retard
     */
    public boolean isOverdue() {
        if (deadline == null || estTerminee) {
            return false;
        }
        return LocalDateTime.now().isAfter(deadline);
    }

    /**
     * Met à jour l'alerte/warning selon l'état
     */
    public void updateWarning() {
        if (isOverdue()) {
            warning = "Tâche en retard";
        } else if (deadline != null &&
                LocalDateTime.now().plusDays(2).isAfter(deadline)) {
            warning = "Échéance proche";
        } else {
            warning = null;
        }
    }

    /**
     * Vérifie si toutes les sous-tâches sont terminées
     * Note: Cette méthode vérifie seulement les URI locales.
     * La vérification réelle doit être faite dans le service avec les objets complets.
     */
    public boolean areAllSubtasksCompleted(List<Task> subtasks) {
        if (subtasks == null || subtasks.isEmpty()) {
            return true;
        }

        return subtasks.stream().allMatch(Task::isEstTerminee);
    }

    /**
     * Prépare la suppression en cascade (nettoie les références)
     * Note: La suppression réelle doit être faite dans le service
     */
    public void prepareCascadeDelete() {
        // Réinitialiser les liens pour éviter les références circulaires
        if (sousTachesUris != null) {
            sousTachesUris.clear();
        }
        if (dependDeUris != null) {
            dependDeUris.clear();
        }
    }

    @Override
    public String toString() {
        return String.format("Task{id='%s', titre='%s', statut=%s, terminée=%s}",
                getShortId(), titre, getStatutLabel(), estTerminee);
    }
}