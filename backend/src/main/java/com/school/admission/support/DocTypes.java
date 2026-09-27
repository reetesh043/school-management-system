package com.school.admission.support;

import com.school.admission.domain.ApplicationDocument;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class DocTypes {

    private DocTypes() { }

    /** Human-friendly label for a document type code. */
    public static String label(String docType) {
        return switch (docType) {
            case "BIRTH_CERT" -> "Birth certificate";
            case "ADDRESS_PROOF" -> "Address proof";
            case "REPORT_CARD" -> "Previous report card";
            case "TRANSFER_CERT" -> "Transfer certificate";
            default -> {
                String s = docType.toLowerCase(Locale.ROOT).replace('_', ' ');
                yield Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
        };
    }

    /** The newest upload wins, so a re-upload replaces an earlier rejected or verified file. */
    public static Map<String, ApplicationDocument> latestByType(Collection<ApplicationDocument> docs) {
        Map<String, ApplicationDocument> latest = new HashMap<>();
        docs.stream()
                .sorted(Comparator.comparing(ApplicationDocument::getId))
                .forEach(d -> latest.put(d.getDocType(), d));
        return latest;
    }
}
