package com.school.admission.service;

import com.school.admission.domain.AdmissionApplicationRepository;
import org.springframework.stereotype.Component;

/** APP<YY>-###### using the current row count. Fine for a demo; swap for a DB sequence under real concurrency. */
@Component
class ApplicationNumberGenerator {

    private final AdmissionApplicationRepository applications;

    ApplicationNumberGenerator(AdmissionApplicationRepository applications) {
        this.applications = applications;
    }

    String next(int academicYearStartYear) {
        long n = applications.count() + 1;
        return String.format("APP%02d-%06d", academicYearStartYear % 100, n);
    }
}
