package com.school.admission.engine;

import com.school.admission.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Table-driven workflow engine.
 *
 * <pre>
 *   lock the application row
 *     -> find the configured transition (current stage to requested stage)
 *     -> check the caller's role
 *     -> run every guard and collect ALL failures, so the user can fix everything in one go
 *     -> move the stage and append history
 *     -> run actions (they write to the outbox in this same transaction)
 * </pre>
 * All of it happens in one transaction: either the whole transition applies or none of it does.
 */
@Service
public class StageEngine {

    private final AdmissionApplicationRepository applications;
    private final StageDefinitionRepository stages;
    private final StageTransitionRepository transitions;
    private final StageHistoryRepository history;
    private final Map<String, TransitionGuard> guards;
    private final Map<String, TransitionAction> actions;

    public StageEngine(AdmissionApplicationRepository applications,
                       StageDefinitionRepository stages,
                       StageTransitionRepository transitions,
                       StageHistoryRepository history,
                       List<TransitionGuard> guardBeans,
                       List<TransitionAction> actionBeans) {
        this.applications = applications;
        this.stages = stages;
        this.transitions = transitions;
        this.history = history;
        this.guards = guardBeans.stream().collect(Collectors.toMap(TransitionGuard::key, Function.identity()));
        this.actions = actionBeans.stream().collect(Collectors.toMap(TransitionAction::key, Function.identity()));
    }

    public record Result(Long applicationId, String fromStage, String toStage, long version, List<String> actionsRun) { }

    public record GuardStatus(String key, String description, boolean passed, String message) { }

    public record TransitionOption(String toStage, String toStageName, boolean roleAllowed, List<GuardStatus> guards) { }

    @Transactional
    public Result transition(Long applicationId, String toStageCode, Actor actor, String reason, Long expectedVersion) {
        AdmissionApplication app = applications.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new NoSuchElementException("Application not found"));

        if (expectedVersion != null && !expectedVersion.equals(app.getVersion())) {
            throw new StaleApplicationException(expectedVersion, app.getVersion());
        }

        StageDefinition from = stages.findById(app.getStageId()).orElseThrow();
        StageDefinition to = stages.findByWorkflowIdAndCode(app.getWorkflowId(), toStageCode)
                .orElseThrow(() -> new TransitionRejectedException(
                        List.of("Stage " + toStageCode + " does not exist in this workflow.")));

        StageTransition edge = transitions
                .findByWorkflowIdAndFromStageIdAndToStageId(app.getWorkflowId(), from.getId(), to.getId())
                .orElseThrow(() -> new TransitionRejectedException(
                        List.of("An application in " + from.getName() + " cannot move to " + to.getName() + ".")));

        if (!actor.hasAnyRole(edge.roles())) {
            throw new TransitionNotAllowedException(
                    "Your role cannot move an application from " + from.getName() + " to " + to.getName() + ".");
        }

        if (to.isSideStage() && (reason == null || reason.isBlank())) {
            throw new TransitionRejectedException(List.of("A reason is required to move to " + to.getName() + "."));
        }

        TransitionContext ctx = new TransitionContext(app, edge, from, to, actor, reason);

        List<String> failures = new ArrayList<>();
        for (String key : edge.guards()) {
            GuardResult r = guard(key).check(ctx);
            if (!r.passed()) {
                failures.add(r.message());
            }
        }
        if (!failures.isEmpty()) {
            throw new TransitionRejectedException(failures);
        }

        app.moveTo(to.getId(), to.isSideStage());
        history.save(StageHistory.of(app.getId(), from.getId(), to.getId(), actor.type().name(), actor.id(), reason));

        for (String key : edge.actions()) {
            action(key).execute(ctx);
        }

        applications.saveAndFlush(app);    // flush so the new @Version is visible in the result
        return new Result(app.getId(), from.getCode(), to.getCode(), app.getVersion(), edge.actions());
    }

    /**
     * What could this actor do next, and what is stopping each move right now? Read-only, so a screen can show
     * a live checklist next to each button.
     */
    @Transactional(readOnly = true)
    public List<TransitionOption> available(Long applicationId, Actor actor) {
        AdmissionApplication app = applications.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("Application not found"));
        StageDefinition from = stages.findById(app.getStageId()).orElseThrow();
        Map<Long, StageDefinition> byId = stages.findByWorkflowIdOrderBySeqNo(app.getWorkflowId()).stream()
                .collect(Collectors.toMap(StageDefinition::getId, Function.identity()));

        List<TransitionOption> options = new ArrayList<>();
        for (StageTransition edge : transitions.findByWorkflowIdAndFromStageId(app.getWorkflowId(), from.getId())) {
            StageDefinition to = byId.get(edge.getToStageId());
            TransitionContext ctx = new TransitionContext(app, edge, from, to, actor, null);
            List<GuardStatus> statuses = new ArrayList<>();
            for (String key : edge.guards()) {
                TransitionGuard g = guard(key);
                GuardResult r = g.check(ctx);
                statuses.add(new GuardStatus(key, g.description(), r.passed(), r.message()));
            }
            options.add(new TransitionOption(to.getCode(), to.getName(), actor.hasAnyRole(edge.roles()), statuses));
        }
        Map<String, Integer> order = byId.values().stream()
                .collect(Collectors.toMap(StageDefinition::getCode, StageDefinition::getSeqNo));
        options.sort(java.util.Comparator.comparing(o -> order.get(o.toStage())));
        return options;
    }

    public Set<String> knownGuardKeys() {
        return new TreeSet<>(guards.keySet());
    }

    public Set<String> knownActionKeys() {
        return new TreeSet<>(actions.keySet());
    }

    public Map<String, String> guardDescriptions() {
        return guards.values().stream().collect(Collectors.toMap(TransitionGuard::key, TransitionGuard::description));
    }

    private TransitionGuard guard(String key) {
        TransitionGuard g = guards.get(key);
        if (g == null) {
            throw new IllegalStateException("The workflow refers to an unknown guard: " + key);
        }
        return g;
    }

    private TransitionAction action(String key) {
        TransitionAction a = actions.get(key);
        if (a == null) {
            throw new IllegalStateException("The workflow refers to an unknown action: " + key);
        }
        return a;
    }
}
