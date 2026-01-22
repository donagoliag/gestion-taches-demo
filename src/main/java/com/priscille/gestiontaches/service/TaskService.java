package com.priscille.gestiontaches.service;

import com.priscille.gestiontaches.exception.DuplicateTaskException;
import com.priscille.gestiontaches.exception.InvalidDateException;
import com.priscille.gestiontaches.model.Task;
import com.priscille.gestiontaches.rdf.OntologyLoader;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskService {

    private final Model model;
    private static final String NS = "http://www.example.org/ontologie/gestion-taches#";

    public TaskService(OntologyLoader loader) {
        this.model = loader.getModel();
    }

    // ==================== HELPERS RDF ====================
    private Property p(String local) { return model.createProperty(NS, local); }
    private Resource r(String local) { return model.createResource(NS + local); }
    private Resource taskRes(String id) { return model.createResource(NS + "task_" + id); }
    private Resource attachmentRes(String id) { return model.createResource(NS + "Attachment_" + id); }

    private String opt(String v, String def) { return v == null ? def : v; }

    private String optStmt(Resource t, String propLocal) {
        Statement st = t.getProperty(p(propLocal));
        return st == null ? null : st.getString();
    }

    private Resource optResource(Resource t, String propLocal) {
        Statement st = t.getProperty(p(propLocal));
        return st != null && st.getObject().isResource() ? st.getResource() : null;
    }

    private void replaceFunctional(Resource s, String propLocal, RDFNode obj) {
        model.removeAll(s, p(propLocal), null);
        model.add(s, p(propLocal), obj);
    }

    private void replaceFunctional(Resource s, String propLocal, String literal) {
        model.removeAll(s, p(propLocal), null);
        model.add(s, p(propLocal), literal);
    }

    // ==================== MÉTHODES D'UNICITÉ ====================

    /**
     * Vérifie si une tâche avec le même titre existe déjà (insensible à la casse)
     * @param titre Le titre à vérifier
     * @param excludeTaskId L'ID de la tâche à exclure (pour les mises à jour)
     * @return true si une tâche avec ce titre existe déjà
     */
    private boolean taskTitleExists(String titre, String excludeTaskId) {
        if (titre == null || titre.trim().isEmpty()) {
            return false;
        }

        String normalizedTitle = titre.trim().toLowerCase();

        return model.listSubjectsWithProperty(p("titre"))
                .toList().stream()
                .filter(res -> {
                    // Exclure la tâche en cours de modification
                    if (excludeTaskId != null) {
                        String taskId = extractTaskIdFromUri(res.getURI());
                        if (taskId != null && taskId.equals(excludeTaskId)) {
                            return false;
                        }
                    }

                    String existingTitle = optStmt(res, "titre");
                    return existingTitle != null &&
                            existingTitle.trim().toLowerCase().equals(normalizedTitle);
                })
                .findFirst()
                .isPresent();
    }

    /**
     * Extrait l'ID d'une URI de tâche
     * Ex: "http://.../task_abc123" → "abc123"
     */
    private String extractTaskIdFromUri(String uri) {
        if (uri == null) return null;
        if (uri.contains("task_")) {
            return uri.substring(uri.lastIndexOf("task_") + 5);
        }
        return null;
    }

    /**
     * Vérifie l'unicité du titre lors de la création
     * @throws DuplicateTaskException si le titre existe déjà
     */
    private void validateUniqueTitleForCreation(String titre) throws DuplicateTaskException {
        if (titre == null || titre.trim().isEmpty()) {
            return;
        }

        if (taskTitleExists(titre, null)) {
            throw new DuplicateTaskException(
                    "Une tâche avec le titre '" + titre + "' existe déjà. " +
                            "Veuillez choisir un titre unique."
            );
        }
    }

    /**
     * Vérifie l'unicité du titre lors de la mise à jour
     * @throws DuplicateTaskException si le titre existe déjà
     */
    private void validateUniqueTitleForUpdate(String currentIri, String newTitle) throws DuplicateTaskException {
        if (newTitle == null || newTitle.trim().isEmpty()) {
            return;
        }

        String currentId = extractTaskIdFromUri(currentIri);
        String currentTitle = null;

        // Récupérer le titre actuel
        Resource currentTask = model.getResource(currentIri);
        if (currentTask != null) {
            currentTitle = optStmt(currentTask, "titre");
        }

        // Vérifier uniquement si le titre change
        if (currentTitle != null && newTitle.trim().equalsIgnoreCase(currentTitle.trim())) {
            return; // Même titre, pas de vérification
        }

        if (taskTitleExists(newTitle, currentId)) {
            throw new DuplicateTaskException(
                    "Une autre tâche avec le titre '" + newTitle + "' existe déjà. " +
                            "Veuillez choisir un titre unique."
            );
        }
    }

    // ==================== CRUD PRINCIPAL ====================

    public Task create(Task input) throws DuplicateTaskException {
        // Vérifier l'unicité du titre
        validateUniqueTitleForCreation(input.getTitre());

        String id = UUID.randomUUID().toString().substring(0,8);
        Resource t = taskRes(id);

        t.addProperty(p("titre"), opt(input.getTitre(), "Sans titre"));
        t.addProperty(p("description"), opt(input.getDescription(), ""));
        t.addProperty(p("dateCreation"), LocalDateTime.now().toString());
        t.addProperty(p("estTerminee"), "false");

        if (input.getDeadline() != null) {
            t.addProperty(p("deadline"), input.getDeadline().toString());
        }

        if (input.getStatutUri() != null) {
            replaceFunctional(t, "aStatut", model.createResource(input.getStatutUri()));
        }
        if (input.getPrioriteUri() != null) {
            replaceFunctional(t, "aPriorite", model.createResource(input.getPrioriteUri()));
        }
        if (input.getCategorieUri() != null) {
            replaceFunctional(t, "appartientA", model.createResource(input.getCategorieUri()));
        }
        if (input.getAssigneAUri() != null) {
            replaceFunctional(t, "assigneA", model.createResource(input.getAssigneAUri()));
        }
        if (input.getCreeParUri() != null) {
            replaceFunctional(t, "creePar", model.createResource(input.getCreeParUri()));
        }

        autoAssignPriorityAndStatus(t);
        saveData();
        return toTask(t);
    }

    public Task getByIri(String iri) {
        Resource t = model.getResource(iri);
        if (t == null || t.getProperty(p("titre")) == null) return null;
        return toTask(t);
    }

    public List<Task> list(Map<String,String> filters) {
        List<Resource> tasks = model.listSubjectsWithProperty(p("titre")).toList();
        return tasks.stream()
                .map(this::toTask)
                .filter(t -> filterMatch(t, filters))
                .collect(Collectors.toList());
    }

    public Task update(String iri, Task patch) throws DuplicateTaskException {
        Resource t = model.getResource(iri);
        if (t == null || t.getProperty(p("titre")) == null) return null;

        // Vérifier l'unicité du titre si modifié
        if (patch.getTitre() != null) {
            validateUniqueTitleForUpdate(iri, patch.getTitre());
            replaceFunctional(t, "titre", patch.getTitre());
        }

        if (patch.getDescription() != null) replaceFunctional(t, "description", patch.getDescription());
        if (patch.getDeadline() != null) replaceFunctional(t, "deadline", patch.getDeadline().toString());
        if (patch.getWarning() != null) replaceFunctional(t, "warning", patch.getWarning());
        if (patch.getUrgence() != null) replaceFunctional(t, "urgence", patch.getUrgence());

        if (patch.getStatutUri() != null) {
            replaceFunctional(t, "aStatut", model.createResource(patch.getStatutUri()));
        }
        if (patch.getPrioriteUri() != null) {
            replaceFunctional(t, "aPriorite", model.createResource(patch.getPrioriteUri()));
        }
        if (patch.getCategorieUri() != null) {
            replaceFunctional(t, "appartientA", model.createResource(patch.getCategorieUri()));
        }
        if (patch.getAssigneAUri() != null) {
            replaceFunctional(t, "assigneA", model.createResource(patch.getAssigneAUri()));
        }

        replaceFunctional(t, "estTerminee", String.valueOf(patch.isEstTerminee()));
        if (patch.isEstTerminee() && patch.getTerminationCause() != null) {
            replaceFunctional(t, "terminationCause", patch.getTerminationCause());
            if (patch.getDateFin() != null) {
                replaceFunctional(t, "dateFin", patch.getDateFin().toString());
            }
        }

        autoAssignPriorityAndStatus(t);
        saveData();
        return toTask(t);
    }

    public boolean delete(String iri) {
        Resource t = model.getResource(iri);
        if (t == null || t.getProperty(p("titre")) == null) return false;

        // Règle 5: Une tâche supprimée entraîne la suppression de toutes ses sous-tâches
        deleteSubtasksCascade(t);

        // Supprimer les références à cette tâche
        removeTaskReferences(t);

        // Supprimer la tâche elle-même
        model.removeAll(t, null, null);
        model.removeAll(null, null, t);

        saveData();
        return true;
    }

    // ==================== GESTION DES SOUS-TÂCHES ====================

    public Task addSubtask(String parentIri, Task childInput) throws InvalidDateException, DuplicateTaskException {
        Resource parent = model.getResource(parentIri);
        if (parent == null || parent.getProperty(p("titre")) == null) {
            throw new IllegalArgumentException("Tâche parente non trouvée: " + parentIri);
        }

        if ("true".equals(optStmt(parent, "estTerminee"))) {
            throw new IllegalStateException(
                    "Impossible d'ajouter une sous-tâche à une tâche parente déjà terminée"
            );
        }

        // Vérifier l'unicité du titre pour la sous-tâche
        validateUniqueTitleForCreation(childInput.getTitre());

        validateDates(parent, childInput);

        String id = UUID.randomUUID().toString().substring(0,8);
        Resource child = taskRes(id);

        child.addProperty(p("titre"), opt(childInput.getTitre(), "Sous-tâche"));
        child.addProperty(p("description"), opt(childInput.getDescription(), ""));
        child.addProperty(p("dateCreation"), LocalDateTime.now().toString());
        child.addProperty(p("estTerminee"), "false");

        // Règle 7: Si la sous-tâche n'a pas de deadline, on lui donne celle du parent
        if (childInput.getDeadline() != null) {
            child.addProperty(p("deadline"), childInput.getDeadline().toString());
        } else {
            // Prendre la deadline du parent si disponible
            String parentDeadline = optStmt(parent, "deadline");
            if (parentDeadline != null) {
                child.addProperty(p("deadline"), parentDeadline);
            }
        }

        inheritFromParent(parent, child);
        parent.addProperty(p("aSousTache"), child);
        autoAssignPriorityAndStatus(child);
        saveData();

        return toTask(child);
    }

    private void validateDates(Resource parent, Task childInput) throws InvalidDateException {
        String parentCreationStr = optStmt(parent, "dateCreation");
        String parentDeadlineStr = optStmt(parent, "deadline");

        LocalDateTime parentCreation = parseDate(parentCreationStr);
        LocalDateTime parentDeadline = parseDate(parentDeadlineStr);

        if (childInput.getDeadline() != null) {
            LocalDateTime childDeadline = childInput.getDeadline();

            if (parentCreation != null && childDeadline.isBefore(parentCreation)) {
                throw new InvalidDateException(
                        "La sous-tâche ne peut pas avoir une deadline (" + childDeadline +
                                ") antérieure à la création de la tâche parente (" + parentCreation + ")"
                );
            }

            if (parentDeadline != null && childDeadline.isAfter(parentDeadline)) {
                throw new InvalidDateException(
                        "La sous-tâche ne peut pas avoir une deadline (" + childDeadline +
                                ") postérieure à la deadline de la tâche parente (" + parentDeadline + ")"
                );
            }

            if (parentDeadline != null && childDeadline.isAfter(parentDeadline.minusDays(1))) {
                parent.addProperty(p("warning"),
                        "Sous-tâche avec deadline très proche de celle du parent (" + childDeadline + ")"
                );
            }
        }
    }

    private void inheritFromParent(Resource parent, Resource child) {
        Resource parentCat = optResource(parent, "appartientA");
        if (parentCat != null) {
            replaceFunctional(child, "appartientA", parentCat);
        }

        Resource parentPriority = optResource(parent, "aPriorite");
        if (parentPriority != null) {
            replaceFunctional(child, "aPriorite", parentPriority);
        }
    }

    // ==================== GESTION DES DÉPENDANCES ====================

    public Task addDependency(String taskIri, String dependsOnIri) {
        Resource task = model.getResource(taskIri);
        Resource dependsOn = model.getResource(dependsOnIri);

        if (task == null || dependsOn == null) {
            throw new IllegalArgumentException("Tâche non trouvée");
        }

        if (createsCycle(task, dependsOn)) {
            throw new IllegalStateException("La dépendance créerait un cycle");
        }

        task.addProperty(p("dependDe"), dependsOn);
        saveData();
        return toTask(task);
    }

    private boolean createsCycle(Resource task, Resource dependsOn) {
        Set<String> visited = new HashSet<>();
        return hasPath(dependsOn, task.getURI(), visited);
    }

    private boolean hasPath(Resource start, String targetUri, Set<String> visited) {
        if (visited.contains(start.getURI())) return false;
        visited.add(start.getURI());

        StmtIterator deps = start.listProperties(p("dependDe"));
        while (deps.hasNext()) {
            Resource dep = deps.next().getResource();
            if (dep.getURI().equals(targetUri)) {
                return true;
            }
            if (hasPath(dep, targetUri, visited)) {
                return true;
            }
        }
        return false;
    }

    // ==================== GESTION DU STATUT AVEC RÈGLES HIÉRARCHIQUES ====================

    public Task markAsCompleted(String taskIri, String cause) {
        Resource task = model.getResource(taskIri);
        if (task == null || task.getProperty(p("titre")) == null) {
            throw new IllegalArgumentException("Tâche non trouvée: " + taskIri);
        }

        // Vérifier si la tâche a des dépendances non satisfaites
        checkDependencies(task);

        // Marquer cette tâche comme terminée (avec propagation aux sous-tâches)
        markTaskCompletedInternal(task, cause);

        // Règle 2: Si toutes les sous-tâches sont terminées, le parent aussi
        Resource parent = findParentOf(task);
        if (parent != null) {
            checkAndMarkParentAsComplete(parent);
        }

        saveData();
        return toTask(task);
    }

    /**
     * Vérifie que toutes les dépendances sont terminées
     */
    private void checkDependencies(Resource task) {
        List<Resource> dependencies = model.listObjectsOfProperty(task, p("dependDe"))
                .toList().stream()
                .filter(RDFNode::isResource)
                .map(RDFNode::asResource)
                .collect(Collectors.toList());

        for (Resource dep : dependencies) {
            if (!"true".equals(optStmt(dep, "estTerminee"))) {
                throw new IllegalStateException(
                        "La tâche dépend de '" + optStmt(dep, "titre") + "' qui n'est pas terminée"
                );
            }
        }
    }

    /**
     * Marquer une tâche comme terminée et propager à toutes ses sous-tâches
     */
    private void markTaskCompletedInternal(Resource task, String cause) {
        replaceFunctional(task, "estTerminee", "true");
        replaceFunctional(task, "aStatut", r("Terminee"));
        replaceFunctional(task, "terminationCause", cause);
        replaceFunctional(task, "dateFin", LocalDateTime.now().toString());

        // Règle 6: Si la tâche est terminée, TOUTES ses sous-tâches aussi (propagation automatique)
        markAllSubtasksAsCompleted(task, "ParentCompleted: " + cause);
    }

    /**
     * Règle 6: Marque récursivement toutes les sous-tâches comme terminées
     */
    private void markAllSubtasksAsCompleted(Resource parent, String cause) {
        model.listObjectsOfProperty(parent, p("aSousTache"))
                .toList().stream()
                .filter(RDFNode::isResource)
                .map(RDFNode::asResource)
                .forEach(subtask -> {
                    // Ne marquer que si pas déjà terminée
                    if (!"true".equals(optStmt(subtask, "estTerminee"))) {
                        replaceFunctional(subtask, "estTerminee", "true");
                        replaceFunctional(subtask, "aStatut", r("Terminee"));
                        replaceFunctional(subtask, "terminationCause", cause);
                        replaceFunctional(subtask, "dateFin", LocalDateTime.now().toString());

                        // Propagation récursive vers les sous-sous-tâches
                        markAllSubtasksAsCompleted(subtask, "GrandParentCompleted: " + cause);
                    }
                });
    }

    /**
     * Règle 2: Vérifie si toutes les sous-tâches sont terminées et marque le parent si oui
     */
    private void checkAndMarkParentAsComplete(Resource parent) {
        if (parent == null) return;

        boolean allSubtasksCompleted = model.listObjectsOfProperty(parent, p("aSousTache"))
                .toList().stream()
                .filter(RDFNode::isResource)
                .map(RDFNode::asResource)
                .allMatch(sub -> "true".equals(optStmt(sub, "estTerminee")));

        if (allSubtasksCompleted && !"true".equals(optStmt(parent, "estTerminee"))) {
            // Marquer le parent comme terminé
            replaceFunctional(parent, "estTerminee", "true");
            replaceFunctional(parent, "aStatut", r("Terminee"));
            replaceFunctional(parent, "terminationCause", "AllSubtasksCompleted");
            replaceFunctional(parent, "dateFin", LocalDateTime.now().toString());

            // Propagation récursive vers les grands-parents
            checkAndMarkParentAsComplete(findParentOf(parent));
        }
    }

    private Resource findParentOf(Resource child) {
        return model.listSubjectsWithProperty(p("aSousTache"), child)
                .nextOptional()
                .orElse(null);
    }

    public Task markInProgress(String iri) {
        Resource t = model.getResource(iri);
        if (t == null || t.getProperty(p("titre")) == null) return null;

        // Règle 3: Si la tâche est remise en cours et toutes ses sous-tâches sont terminées,
        // alors toutes les sous-tâches passent à "À faire"
        boolean hasSubtasks = model.contains(t, p("aSousTache"));
        boolean isCompleted = "true".equals(optStmt(t, "estTerminee"));

        if (hasSubtasks && isCompleted) {
            // Vérifier si toutes les sous-tâches sont terminées
            boolean allSubtasksCompleted = model.listObjectsOfProperty(t, p("aSousTache"))
                    .toList().stream()
                    .filter(RDFNode::isResource)
                    .map(RDFNode::asResource)
                    .allMatch(sub -> "true".equals(optStmt(sub, "estTerminee")));

            if (allSubtasksCompleted) {
                // Règle 3: Remettre toutes les sous-tâches à "À faire"
                model.listObjectsOfProperty(t, p("aSousTache"))
                        .toList().stream()
                        .filter(RDFNode::isResource)
                        .map(RDFNode::asResource)
                        .forEach(this::reopenSubtask);
            }
        }

        // Règle 4: Si c'est une sous-tâche remise en cours, la tâche parente aussi
        Resource parent = findParentOf(t);
        if (parent != null && "true".equals(optStmt(parent, "estTerminee"))) {
            reopenParent(parent);
        }

        // Mettre la tâche courante en cours
        reopenTask(t);

        autoAssignPriorityAndStatus(t);
        saveData();
        return toTask(t);
    }

    private void reopenTask(Resource task) {
        replaceFunctional(task, "estTerminee", "false");
        replaceFunctional(task, "aStatut", r("AFaire"));
        if (optStmt(task, "terminationCause") != null) {
            task.removeAll(p("terminationCause"));
        }
        if (optStmt(task, "dateFin") != null) {
            task.removeAll(p("dateFin"));
        }
    }

    private void reopenSubtask(Resource subtask) {
        replaceFunctional(subtask, "estTerminee", "false");
        replaceFunctional(subtask, "aStatut", r("AFaire"));
        if (optStmt(subtask, "terminationCause") != null) {
            subtask.removeAll(p("terminationCause"));
        }
        if (optStmt(subtask, "dateFin") != null) {
            subtask.removeAll(p("dateFin"));
        }
    }

    private void reopenParent(Resource parent) {
        // Règle 4: La tâche parente est remise en cours avec statut "À faire"
        reopenTask(parent);

        // Propagation vers les grands-parents
        Resource grandParent = findParentOf(parent);
        if (grandParent != null && "true".equals(optStmt(grandParent, "estTerminee"))) {
            reopenParent(grandParent);
        }
    }

    // ==================== SUPPRESSION EN CASCADE ====================

    private void deleteSubtasksCascade(Resource parent) {
        // Récupérer toutes les sous-tâches (récursivement)
        List<Resource> allSubtasks = getAllSubtasksRecursive(parent);

        // Supprimer chaque sous-tâche
        for (Resource subtask : allSubtasks) {
            removeTaskReferences(subtask);
            model.removeAll(subtask, null, null);
            model.removeAll(null, null, subtask);
        }

        // Supprimer la liste des sous-tâches du parent
        parent.removeAll(p("aSousTache"));
    }

    private List<Resource> getAllSubtasksRecursive(Resource parent) {
        List<Resource> allSubtasks = new ArrayList<>();
        collectAllSubtasks(parent, allSubtasks);
        return allSubtasks;
    }

    private void collectAllSubtasks(Resource parent, List<Resource> allSubtasks) {
        model.listObjectsOfProperty(parent, p("aSousTache"))
                .toList().stream()
                .filter(RDFNode::isResource)
                .map(RDFNode::asResource)
                .forEach(child -> {
                    allSubtasks.add(child);
                    collectAllSubtasks(child, allSubtasks);
                });
    }

    // ==================== PIÈCES JOINTES ====================

    public Map<String, Object> addAttachment(String taskIri, MultipartFile file) {
        Resource taskNode = model.getResource(taskIri);

        if (taskNode == null || taskNode.getProperty(p("titre")) == null) {
            throw new RuntimeException("Tâche non trouvée");
        }

        try {
            Path uploadsDir = Paths.get("uploads");
            if (!Files.exists(uploadsDir)) {
                Files.createDirectories(uploadsDir);
            }

            String attachmentId = UUID.randomUUID().toString().substring(0, 8);
            String filename = file.getOriginalFilename();

            if (filename == null || filename.trim().isEmpty()) {
                filename = "sans_nom_" + attachmentId;
            }

            String savedFilename = attachmentId + "_" + filename;
            Path filePath = uploadsDir.resolve(savedFilename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            Resource attachmentNode = attachmentRes(attachmentId);

            attachmentNode.addProperty(p("nomFichier"), filename);
            attachmentNode.addProperty(p("cheminFichier"), filePath.toString());

            taskNode.addProperty(p("aPieceJointe"), attachmentNode);

            saveData();

            return Map.of(
                    "message", "Pièce jointe ajoutée",
                    "attachment", Map.of(
                            "id", attachmentNode.getURI(),
                            "filename", filename
                    )
            );

        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de la sauvegarde du fichier: " + e.getMessage());
        }
    }

    public boolean deleteAttachment(String taskIri, String attachmentId) {
        Resource taskNode = model.getResource(taskIri);
        Resource attachmentNode = model.getResource(attachmentId);

        if (taskNode == null || taskNode.getProperty(p("titre")) == null) {
            return false;
        }

        Statement pathStmt = attachmentNode.getProperty(p("cheminFichier"));
        if (pathStmt != null) {
            String filePath = pathStmt.getString();
            try {
                Path path = Paths.get(filePath);
                Files.deleteIfExists(path);
            } catch (IOException e) {
                System.err.println("Erreur lors de la suppression du fichier: " + e.getMessage());
            }
        }

        model.removeAll(taskNode, p("aPieceJointe"), attachmentNode);
        model.removeAll(attachmentNode, null, null);

        saveData();
        return true;
    }

    // ==================== HELPERS MÉTIER ====================

    private void autoAssignPriorityAndStatus(Resource t) {
        boolean estTerminee = "true".equals(optStmt(t, "estTerminee"));
        String deadlineStr = optStmt(t, "deadline");

        if (optResource(t, "aPriorite") == null) {
            String priorite = "Moyenne";
            if (deadlineStr != null) {
                try {
                    LocalDateTime d = LocalDateTime.parse(deadlineStr);
                    long daysLeft = Duration.between(LocalDateTime.now(), d).toDays();

                    if (daysLeft <= 1) priorite = "Urgente";
                    else if (daysLeft <= 3) priorite = "Haute";
                    else if (daysLeft <= 7) priorite = "Moyenne";
                    else priorite = "Basse";

                    String urgence;
                    if (daysLeft <= 1) urgence = "élevée";
                    else if (daysLeft <= 3) urgence = "moyenne";
                    else urgence = "faible";

                    replaceFunctional(t, "urgence", urgence);

                } catch (Exception ignored) {}
            }
            replaceFunctional(t, "aPriorite", r(priorite));
        }

        if (optResource(t, "aStatut") == null) {
            String statut = "AFaire";
            if (estTerminee) {
                statut = "Terminee";
            } else if (deadlineStr != null) {
                try {
                    LocalDateTime d = LocalDateTime.parse(deadlineStr);
                    if (LocalDateTime.now().isAfter(d)) {
                        statut = "EnRetard";
                    }
                } catch (Exception ignored) {}
            }
            replaceFunctional(t, "aStatut", r(statut));
        }
    }

    private Task toTask(Resource t) {
        Task task = new Task();
        task.setId(t.getURI());
        task.setTitre(optStmt(t, "titre"));
        task.setDescription(optStmt(t, "description"));

        String dc = optStmt(t, "dateCreation");
        task.setDateCreation(dc != null ? LocalDateTime.parse(dc) : null);

        String dl = optStmt(t, "deadline");
        task.setDeadline(dl != null ? LocalDateTime.parse(dl) : null);

        String df = optStmt(t, "dateFin");
        task.setDateFin(df != null ? LocalDateTime.parse(df) : null);

        task.setEstTerminee("true".equals(optStmt(t, "estTerminee")));
        task.setTerminationCause(optStmt(t, "terminationCause"));
        task.setWarning(optStmt(t, "warning"));
        task.setUrgence(optStmt(t, "urgence"));

        Resource statutRes = optResource(t, "aStatut");
        task.setStatutUri(statutRes != null ? statutRes.getURI() : null);

        Resource prioriteRes = optResource(t, "aPriorite");
        task.setPrioriteUri(prioriteRes != null ? prioriteRes.getURI() : null);

        Resource categorieRes = optResource(t, "appartientA");
        task.setCategorieUri(categorieRes != null ? categorieRes.getURI() : null);

        Resource assigneARes = optResource(t, "assigneA");
        task.setAssigneAUri(assigneARes != null ? assigneARes.getURI() : null);

        Resource creeParRes = optResource(t, "creePar");
        task.setCreeParUri(creeParRes != null ? creeParRes.getURI() : null);

        List<String> subs = model.listObjectsOfProperty(t, p("aSousTache"))
                .toList().stream()
                .filter(RDFNode::isResource)
                .map(n -> n.asResource().getURI())
                .collect(Collectors.toList());
        task.setSousTachesUris(subs);

        List<String> deps = model.listObjectsOfProperty(t, p("dependDe"))
                .toList().stream()
                .filter(RDFNode::isResource)
                .map(n -> n.asResource().getURI())
                .collect(Collectors.toList());
        task.setDependDeUris(deps);

        List<Map<String, String>> attachments = model.listObjectsOfProperty(t, p("aPieceJointe"))
                .toList().stream()
                .filter(RDFNode::isResource)
                .map(n -> {
                    Resource att = n.asResource();
                    Map<String, String> attMap = new HashMap<>();
                    attMap.put("id", att.getURI());
                    Statement fnStmt = att.getProperty(p("nomFichier"));
                    Statement fpStmt = att.getProperty(p("cheminFichier"));
                    if (fnStmt != null) attMap.put("filename", fnStmt.getString());
                    if (fpStmt != null) attMap.put("path", fpStmt.getString());
                    return attMap;
                })
                .collect(Collectors.toList());
        task.setAttachments(attachments);

        task.updateWarning();
        return task;
    }

    // ==================== UTILITAIRES ====================

    private LocalDateTime parseDate(String dateStr) {
        return dateStr != null ? LocalDateTime.parse(dateStr) : null;
    }

    private void removeTaskReferences(Resource task) {
        model.listSubjectsWithProperty(p("aSousTache"), task)
                .toList()
                .forEach(parent -> parent.removeAll(p("aSousTache")));

        model.listSubjectsWithProperty(p("dependDe"), task)
                .toList()
                .forEach(dependent -> dependent.removeAll(p("dependDe")));
    }

    private void saveData() {
        // MODE DÉMO RENDER : Pas de sauvegarde sur disque
        // Log uniquement pour le debug
        System.out.println("✅ [RENDER DEMO] Données mises à jour en mémoire");

        // EN DÉMO : Ne pas écrire sur disque (Render bloque l'écriture)
        // Les données restent en mémoire uniquement
    }

    private boolean filterMatch(Task t, Map<String,String> filters) {
        String statut = filters.get("statut");
        String priorite = filters.get("priorite");
        String categorie = filters.get("categorie");
        String q = filters.get("q");

        if (statut != null && !statut.equals(t.getStatutLabel())) return false;
        if (priorite != null && !priorite.equals(t.getPrioriteLabel())) return false;
        if (categorie != null && !categorie.equals(t.getCategorieLabel())) return false;

        if (q != null) {
            String title = Optional.ofNullable(t.getTitre()).orElse("").toLowerCase();
            String desc = Optional.ofNullable(t.getDescription()).orElse("").toLowerCase();
            if (!title.contains(q.toLowerCase()) && !desc.contains(q.toLowerCase())) return false;
        }

        return true;
    }


}