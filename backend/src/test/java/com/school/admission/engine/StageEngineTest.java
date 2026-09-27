package com.school.admission.engine;

import com.school.admission.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the engine in isolation: repositories are mocked, so these run without a database
 * and pin down the rules that matter most (all failures reported together, roles enforced, reasons
 * required for side stages, stale versions rejected).
 */
class StageEngineTest {

    private final AdmissionApplicationRepository apps = mock(AdmissionApplicationRepository.class);
    private final StageDefinitionRepository stages = mock(StageDefinitionRepository.class);
    private final StageTransitionRepository transitions = mock(StageTransitionRepository.class);
    private final StageHistoryRepository history = mock(StageHistoryRepository.class);

    private final Long workflowId = 1L;
    private final Long appId = 100L;
    private AdmissionApplication app;
    private StageDefinition registration;
    private StageDefinition initiated;
    private StageDefinition waitlisted;
    private final List<String> actionLog = new ArrayList<>();

    @BeforeEach
    void setUp() {
        registration = stage(10L, "REGISTRATION", "Registration", StageDefinition.Category.OPEN);
        initiated = stage(20L, "INITIATED", "Admission initiated", StageDefinition.Category.OPEN);
        waitlisted = stage(30L, "WAITLISTED", "Waitlisted", StageDefinition.Category.HOLD);

        app = new AdmissionApplication();
        app.setId(appId);
        app.setApplicationNo("APP-1");
        app.setWorkflowId(workflowId);
        app.setClassConfigId(1L);
        app.setStageId(registration.getId());

        when(apps.findByIdForUpdate(appId)).thenReturn(Optional.of(app));
        when(stages.findById(registration.getId())).thenReturn(Optional.of(registration));
        when(stages.findByWorkflowIdAndCode(workflowId, "INITIATED")).thenReturn(Optional.of(initiated));
        when(stages.findByWorkflowIdAndCode(workflowId, "WAITLISTED")).thenReturn(Optional.of(waitlisted));
        when(apps.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void movesStageWritesHistoryAndRunsActionsInConfiguredOrder() {
        givenEdge(initiated, "COMMITTEE", "PASS", "FIRST,SECOND");

        StageEngine.Result r = engine(guard("PASS", GuardResult.ok())).transition(
                appId, "INITIATED", actor("COMMITTEE"), null, null);

        assertThat(r.fromStage()).isEqualTo("REGISTRATION");
        assertThat(r.toStage()).isEqualTo("INITIATED");
        assertThat(app.getStageId()).isEqualTo(initiated.getId());
        assertThat(actionLog).containsExactly("FIRST", "SECOND");
        verify(history).save(any(StageHistory.class));
    }

    @Test
    void reportsEveryFailedGuardTogetherAndChangesNothing() {
        givenEdge(initiated, "COMMITTEE", "A,B,C", "FIRST");

        var eng = engine(
                guard("A", GuardResult.fail("Score not recorded.")),
                guard("B", GuardResult.ok()),
                guard("C", GuardResult.fail("No seats left.")));

        assertThatThrownBy(() -> eng.transition(appId, "INITIATED", actor("COMMITTEE"), null, null))
                .isInstanceOfSatisfying(TransitionRejectedException.class, e ->
                        assertThat(e.getFailures()).containsExactly("Score not recorded.", "No seats left."));

        assertThat(app.getStageId()).isEqualTo(registration.getId());
        assertThat(actionLog).isEmpty();
        verify(history, never()).save(any());
    }

    @Test
    void refusesARoleThatIsNotAllowedOnTheEdge() {
        givenEdge(initiated, "COMMITTEE", "", "");

        assertThatThrownBy(() -> engine().transition(appId, "INITIATED", actor("GUARDIAN"), null, null))
                .isInstanceOf(TransitionNotAllowedException.class);
        assertThat(app.getStageId()).isEqualTo(registration.getId());
    }

    @Test
    void refusesAMoveThatIsNotInTheWorkflow() {
        when(transitions.findByWorkflowIdAndFromStageIdAndToStageId(workflowId, registration.getId(), initiated.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> engine().transition(appId, "INITIATED", actor("ADMIN"), null, null))
                .isInstanceOfSatisfying(TransitionRejectedException.class, e ->
                        assertThat(e.getFailures().get(0)).contains("cannot move"));
    }

    @Test
    void requiresAReasonForWaitlistingAndRemembersThePreviousStage() {
        givenEdge(waitlisted, "ADMIN", "", "");

        assertThatThrownBy(() -> engine().transition(appId, "WAITLISTED", actor("ADMIN"), " ", null))
                .isInstanceOf(TransitionRejectedException.class);

        engine().transition(appId, "WAITLISTED", actor("ADMIN"), "No seats in class", null);
        assertThat(app.getStageId()).isEqualTo(waitlisted.getId());
        assertThat(app.getPrevStageId()).isEqualTo(registration.getId());
    }

    @Test
    void rejectsAStaleVersion() {
        givenEdge(initiated, "ADMIN", "", "");
        app.setVersion(5L);

        assertThatThrownBy(() -> engine().transition(appId, "INITIATED", actor("ADMIN"), null, 2L))
                .isInstanceOf(StaleApplicationException.class);
    }

    // ------------------------------------------------------------- helpers

    private void givenEdge(StageDefinition to, String roles, String guardKeys, String actionKeys) {
        StageTransition edge = new StageTransition();
        edge.setId(999L);
        edge.setWorkflowId(workflowId);
        edge.setFromStageId(registration.getId());
        edge.setToStageId(to.getId());
        edge.setAllowedRoles(roles);
        edge.setGuardKeys(guardKeys);
        edge.setActionKeys(actionKeys);
        when(transitions.findByWorkflowIdAndFromStageIdAndToStageId(workflowId, registration.getId(), to.getId()))
                .thenReturn(Optional.of(edge));
    }

    private StageEngine engine(TransitionGuard... guards) {
        List<TransitionAction> actionBeans = List.of(action("FIRST"), action("SECOND"));
        return new StageEngine(apps, stages, transitions, history, List.of(guards), actionBeans);
    }

    private TransitionGuard guard(String key, GuardResult result) {
        return new TransitionGuard() {
            public String key() { return key; }
            public String description() { return key; }
            public GuardResult check(TransitionContext ctx) { return result; }
        };
    }

    private TransitionAction action(String key) {
        return new TransitionAction() {
            public String key() { return key; }
            public void execute(TransitionContext ctx) { actionLog.add(key); }
        };
    }

    private Actor actor(String role) {
        return new Actor("u-1", Actor.Type.STAFF, Set.of(role));
    }

    private StageDefinition stage(Long id, String code, String name, StageDefinition.Category category) {
        StageDefinition s = new StageDefinition();
        s.setId(id);
        s.setWorkflowId(workflowId);
        s.setCode(code);
        s.setName(name);
        s.setSeqNo(10);
        s.setCategory(category);
        return s;
    }
}
