package com.school.admission.service;

import com.school.admission.domain.Assessment;
import com.school.admission.domain.AssessmentRepository;
import com.school.admission.domain.ClassConfig;
import com.school.admission.domain.ClassConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Records a single overall score against the class pass mark (a full rubric can be added the same way). */
@Service
public class AssessmentService {

    private final AssessmentRepository assessments;
    private final ClassConfigRepository classes;

    public AssessmentService(AssessmentRepository assessments, ClassConfigRepository classes) {
        this.assessments = assessments;
        this.classes = classes;
    }

    @Transactional
    public Assessment record(Long applicationId, Long classConfigId, String kind, BigDecimal score, String remarks, Long evaluatedBy) {
        ClassConfig cls = classes.findById(classConfigId).orElseThrow();
        Assessment a = new Assessment();
        a.setApplicationId(applicationId);
        a.setKind(kind);
        a.setScore(score);
        a.setPassed(score.compareTo(cls.getPassMark()) >= 0);
        a.setRemarks(remarks);
        a.setEvaluatedById(evaluatedBy);
        return assessments.save(a);
    }
}
