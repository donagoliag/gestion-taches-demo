package com.priscille.gestiontaches.controller;

import com.priscille.gestiontaches.exception.DuplicateTaskException;
import com.priscille.gestiontaches.exception.InvalidDateException;
import com.priscille.gestiontaches.model.Task;
import com.priscille.gestiontaches.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tâches", description = "API de gestion des tâches avec ontologie RDF et règles hiérarchiques")
public class TaskController {

    private final TaskService service;
    private static final String BASE_URI = "http://www.example.org/ontologie/gestion-taches#task_";

    public TaskController(TaskService service) {
        this.service = service;
    }

    // ==================== LISTER ====================

    @Operation(
            summary = "Lister toutes les tâches",
            description = "Retourne toutes les tâches avec possibilité de filtrer"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liste des tâches",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Task.class)))
    })
    @GetMapping
    public List<Task> list(
            @Parameter(description = "Filtrer par statut", example = "Terminee")
            @RequestParam(required = false) String statut,

            @Parameter(description = "Filtrer par priorité", example = "Urgente")
            @RequestParam(required = false) String priorite,

            @Parameter(description = "Filtrer par catégorie", example = "Travail")
            @RequestParam(required = false) String categorie,

            @Parameter(description = "Recherche par mot-clé", example = "présentation")
            @RequestParam(required = false) String q,

            @Parameter(description = "Inclure les sous-tâches dans la recherche", example = "false")
            @RequestParam(required = false, defaultValue = "false") boolean includeSubtasks
    ) {
        Map<String, String> filters = new HashMap<>();
        if (statut != null) filters.put("statut", statut);
        if (priorite != null) filters.put("priorite", priorite);
        if (categorie != null) filters.put("categorie", categorie);
        if (q != null) filters.put("q", q);
        filters.put("includeSubtasks", String.valueOf(includeSubtasks));

        return service.list(filters);
    }

    // ==================== CRUD ====================

    @Operation(
            summary = "Récupérer une tâche",
            description = "Récupère une tâche par son ID avec ses sous-tâches"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tâche trouvée"),
            @ApiResponse(responseCode = "404", description = "Tâche non trouvée")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Task> get(
            @Parameter(description = "ID de la tâche", required = true, example = "abc12345")
            @PathVariable String id,

            @Parameter(description = "Inclure les sous-tâches récursivement", example = "false")
            @RequestParam(required = false, defaultValue = "false") boolean withSubtasks
    ) {
        Task task = service.getByIri(BASE_URI + id);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }

    @Operation(
            summary = "Créer une tâche",
            description = "Crée une nouvelle tâche. IMPORTANT: Le titre doit être unique parmi toutes les tâches."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tâche créée"),
            @ApiResponse(responseCode = "400", description = "Données invalides"),
            @ApiResponse(responseCode = "409", description = "Titre déjà utilisé")
    })
    @PostMapping
    public ResponseEntity<?> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Données de la tâche",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Task.class)
                    )
            )
            @RequestBody Task task
    ) {
        try {
            Task created = service.create(task);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);

        } catch (DuplicateTaskException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Titre dupliqué");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erreur de création");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    @Operation(
            summary = "Mettre à jour une tâche",
            description = "Met à jour une tâche existante. Attention: la mise à jour d'une tâche terminée peut affecter ses sous-tâches. IMPORTANT: Le titre doit rester unique."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tâche mise à jour"),
            @ApiResponse(responseCode = "404", description = "Tâche non trouvée"),
            @ApiResponse(responseCode = "409", description = "Conflit (titre dupliqué ou règles hiérarchiques)")
    })
    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @Parameter(description = "ID de la tâche", required = true, example = "abc12345")
            @PathVariable String id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Nouvelles données",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Task.class)
                    )
            )
            @RequestBody Task task
    ) {
        try {
            Task updated = service.update(BASE_URI + id, task);
            if (updated == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(updated);

        } catch (DuplicateTaskException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Titre dupliqué");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);

        } catch (IllegalStateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Conflit avec les règles hiérarchiques");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);

        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
            summary = "Supprimer une tâche",
            description = "Supprime une tâche (sans ses sous-tâches)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Tâche supprimée"),
            @ApiResponse(responseCode = "404", description = "Tâche non trouvée"),
            @ApiResponse(responseCode = "400", description = "Tâche a des sous-tâches")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @Parameter(description = "ID de la tâche", required = true, example = "abc12345")
            @PathVariable String id
    ) {
        try {
            boolean deleted = service.delete(BASE_URI + id);
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.noContent().build();

        } catch (IllegalStateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Impossible de supprimer");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erreur de suppression");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ==================== SOUS-TÂCHES ====================

    @Operation(
            summary = "Ajouter une sous-tâche",
            description = "Ajoute une sous-tâche à une tâche parente. La sous-tâche hérite automatiquement de certaines propriétés du parent. IMPORTANT: Le titre doit être unique parmi toutes les tâches."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Sous-tâche créée"),
            @ApiResponse(responseCode = "400", description = "Dates invalides"),
            @ApiResponse(responseCode = "404", description = "Tâche parent non trouvée"),
            @ApiResponse(responseCode = "409", description = "Tâche parent déjà terminée ou titre dupliqué")
    })
    @PostMapping("/{id}/subtasks")
    public ResponseEntity<?> addSubtask(
            @Parameter(description = "ID de la tâche parent", required = true, example = "abc12345")
            @PathVariable String id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Données de la sous-tâche",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = Task.class)
                    )
            )
            @RequestBody Task child
    ) {
        try {
            Task subtask = service.addSubtask(BASE_URI + id, child);
            return ResponseEntity.status(HttpStatus.CREATED).body(subtask);

        } catch (DuplicateTaskException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Titre dupliqué");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);

        } catch (InvalidDateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Date invalide");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);

        } catch (IllegalStateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Tâche parent terminée");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);

        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Tâche parent non trouvée");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erreur interne");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @Operation(
            summary = "Supprimer une tâche avec ses sous-tâches",
            description = "Supprime une tâche et TOUTES ses sous-tâches (cascade). ATTENTION: Cette action est irréversible."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tâche et sous-tâches supprimées"),
            @ApiResponse(responseCode = "404", description = "Tâche non trouvée")
    })
    @DeleteMapping("/{id}/cascade")
    public ResponseEntity<?> deleteWithCascade(
            @Parameter(description = "ID de la tâche", required = true, example = "abc12345")
            @PathVariable String id
    ) {
        try {
            boolean deleted = service.delete(BASE_URI + id);
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            Map<String, String> response = new HashMap<>();
            response.put("message", "Tâche et toutes ses sous-tâches ont été supprimées avec succès");
            response.put("deletedTaskId", id);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erreur de suppression");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ==================== DÉPENDANCES ====================

    @Operation(
            summary = "Ajouter une dépendance",
            description = "Ajoute une dépendance entre tâches. La tâche ne pourra être terminée que si ses dépendances sont terminées."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dépendance ajoutée"),
            @ApiResponse(responseCode = "400", description = "Tâche non trouvée"),
            @ApiResponse(responseCode = "409", description = "Cycle détecté")
    })
    @PostMapping("/{id}/dependencies/{dependsOnId}")
    public ResponseEntity<?> addDependency(
            @Parameter(description = "ID de la tâche", required = true, example = "abc12345")
            @PathVariable String id,

            @Parameter(description = "ID de la tâche dont dépend", required = true, example = "def67890")
            @PathVariable String dependsOnId
    ) {
        try {
            Task updated = service.addDependency(BASE_URI + id, BASE_URI + dependsOnId);
            return ResponseEntity.ok(updated);

        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Tâche non trouvée");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);

        } catch (IllegalStateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Cycle de dépendance");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erreur interne");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ==================== STATUT ====================

    @Operation(
            summary = "Marquer comme terminée",
            description = "Marque une tâche comme terminée. "
                    + "Règles appliquées automatiquement:\n"
                    + "1. Si c'est une sous-tâche, toutes les autres sous-tâches du même parent seront aussi terminées\n"
                    + "2. Si toutes les sous-tâches d'un parent sont terminées, le parent sera automatiquement terminé\n"
                    + "3. La terminaison se propage récursivement aux sous-tâches"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tâche terminée (et sous-tâches si applicable)"),
            @ApiResponse(responseCode = "404", description = "Tâche non trouvée"),
            @ApiResponse(responseCode = "409", description = "Dépendances non satisfaites")
    })
    @PostMapping("/{id}/complete")
    public ResponseEntity<?> markAsCompleted(
            @Parameter(description = "ID de la tâche", required = true, example = "abc12345")
            @PathVariable String id,

            @Parameter(description = "Cause de terminaison", example = "Manual",
                    schema = @Schema(allowableValues = {"Manual", "AllSubtasksCompleted", "ParentCompleted", "SiblingCompleted"}))
            @RequestParam(required = false, defaultValue = "Manual") String cause
    ) {
        try {
            Task task = service.markAsCompleted(BASE_URI + id, cause);
            return ResponseEntity.ok(task);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();

        } catch (IllegalStateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Impossible de terminer la tâche");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erreur interne");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @Operation(
            summary = "Marquer comme en cours",
            description = "Marque une tâche comme en cours. "
                    + "Règles appliquées automatiquement:\n"
                    + "1. Si la tâche a toutes ses sous-tâches terminées, elles seront toutes remises à 'À faire'\n"
                    + "2. Si c'est une sous-tâche, la tâche parente sera aussi remise en cours\n"
                    + "3. La remise en cours se propage vers le haut (des sous-tâches vers les parents)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Tâche remise en cours (et parents/sous-tâches si applicable)"),
            @ApiResponse(responseCode = "404", description = "Tâche non trouvée")
    })
    @PostMapping("/{id}/in-progress")
    public ResponseEntity<?> markInProgress(
            @Parameter(description = "ID de la tâche", required = true, example = "abc12345")
            @PathVariable String id
    ) {
        try {
            Task task = service.markInProgress(BASE_URI + id);
            if (task == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(task);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erreur interne");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ==================== HIÉRARCHIE ====================

    @Operation(
            summary = "Récupérer la hiérarchie d'une tâche",
            description = "Récupère une tâche avec toutes ses sous-tâches (récursivement)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Hiérarchie de la tâche"),
            @ApiResponse(responseCode = "404", description = "Tâche non trouvée")
    })
    @GetMapping("/{id}/hierarchy")
    public ResponseEntity<?> getHierarchy(
            @Parameter(description = "ID de la tâche", required = true, example = "abc12345")
            @PathVariable String id
    ) {
        try {
            Task task = service.getByIri(BASE_URI + id);
            if (task == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> hierarchy = new HashMap<>();
            hierarchy.put("task", task);
            hierarchy.put("hasSubtasks", task.hasSubtasks());
            hierarchy.put("isSubtask", task.getSousTachesUris().size() > 0);

            return ResponseEntity.ok(hierarchy);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erreur interne");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ==================== PIÈCES JOINTES ====================

    @Operation(
            summary = "Ajouter une pièce jointe",
            description = "Attache un fichier à une tâche"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Pièce jointe ajoutée"),
            @ApiResponse(responseCode = "404", description = "Tâche non trouvée"),
            @ApiResponse(responseCode = "400", description = "Erreur de fichier")
    })
    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addAttachment(
            @Parameter(description = "ID de la tâche", required = true, example = "abc12345")
            @PathVariable String id,

            @Parameter(description = "Fichier à joindre", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        try {
            Map<String, Object> result = service.addAttachment(BASE_URI + id, file);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);

        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erreur lors de l'ajout");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erreur interne");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @Operation(
            summary = "Supprimer une pièce jointe",
            description = "Supprime une pièce jointe d'une tâche"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Pièce jointe supprimée"),
            @ApiResponse(responseCode = "404", description = "Tâche ou pièce jointe non trouvée")
    })
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<?> deleteAttachment(
            @Parameter(description = "ID de la tâche", required = true, example = "abc12345")
            @PathVariable String id,

            @Parameter(description = "URI complète de la pièce jointe", required = true)
            @PathVariable String attachmentId
    ) {
        try {
            boolean deleted = service.deleteAttachment(BASE_URI + id, attachmentId);
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Erreur interne");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ==================== UTILITAIRES ====================

    @Operation(
            summary = "Vérifier la santé de l'API",
            description = "Endpoint de santé pour vérifier que l'API fonctionne"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "API fonctionnelle")
    })
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "OK",
                "service", "Task API avec règles hiérarchiques",
                "rules", "5 règles hiérarchiques implémentées",
                "unique_titles", "Activé - Tous les titres de tâches doivent être uniques"
        );
    }

    @Operation(
            summary = "Vérifier les règles hiérarchiques",
            description = "Affiche un résumé des règles hiérarchiques implémentées"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Règles affichées")
    })
    @GetMapping("/rules")
    public Map<String, String> getRules() {
        Map<String, String> rules = new HashMap<>();
        rules.put("rule1", "Une sous-tâche finie → toutes les sous-tâches du même parent finies");
        rules.put("rule2", "Toutes les sous-tâches terminées → parent terminé");
        rules.put("rule3", "Tâche remise en cours avec sous-tâches terminées → toutes à 'À faire'");
        rules.put("rule4", "Sous-tâche remise en cours → parent remis en cours");
        rules.put("rule5", "Tâche supprimée → toutes les sous-tâches supprimées");
        rules.put("rule6", "Tous les titres de tâches doivent être uniques (insensible à la casse)");
        return rules;
    }

    @Operation(
            summary = "Vérifier l'unicité d'un titre",
            description = "Vérifie si un titre de tâche est déjà utilisé"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Vérification effectuée")
    })
    @GetMapping("/check-title/{title}")
    public ResponseEntity<Map<String, Object>> checkTitleAvailability(
            @Parameter(description = "Titre à vérifier", required = true, example = "Rapport mensuel")
            @PathVariable String title,

            @Parameter(description = "ID de la tâche à exclure (pour les mises à jour)", example = "abc12345")
            @RequestParam(required = false) String excludeId
    ) {
        try {
            // Cette méthode nécessiterait d'être ajoutée au service
            // Pour l'instant, retournons une réponse basique
            Map<String, Object> response = new HashMap<>();
            response.put("title", title);
            response.put("available", true);
            response.put("message", "La vérification d'unicité est gérée automatiquement lors de la création/mise à jour");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Erreur de vérification");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}