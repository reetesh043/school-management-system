package com.school.admission.web;

import com.school.admission.domain.ApplicationDocument;
import com.school.admission.security.CurrentUser;
import com.school.admission.service.DocumentService;
import com.school.admission.support.DocTypes;
import com.school.admission.web.dto.Dtos.DocumentResponse;
import com.school.admission.web.dto.Dtos.VerifyDocumentRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@RestController
@Tag(name = "Documents", description = "Upload and verify admission documents")
class DocumentController {

    private final DocumentService documents;
    private final CurrentUser currentUser;

    DocumentController(DocumentService documents, CurrentUser currentUser) {
        this.documents = documents;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/v1/applications/{id}/documents")
    @PreAuthorize("isAuthenticated()")
    List<DocumentResponse> list(@PathVariable Long id) {
        return documents.forApplication(id).stream().map(DocumentController::toResponse).toList();
    }

    @PostMapping(value = "/api/v1/applications/{id}/documents", consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<DocumentResponse> upload(@PathVariable Long id, @RequestParam String docType,
                                            @RequestParam MultipartFile file) {
        try {
            ApplicationDocument doc = documents.store(id, docType, file.getOriginalFilename(),
                    file.getContentType(), file.getSize(), file.getInputStream());
            return ResponseEntity.status(201).body(toResponse(doc));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @PutMapping("/api/v1/documents/{documentId}/verification")
    @PreAuthorize("hasAnyRole('ADMIN','PRINCIPAL','FRONT_OFFICE','COUNSELLOR')")
    DocumentResponse verify(@PathVariable Long documentId, Authentication auth, @Valid @RequestBody VerifyDocumentRequest req) {
        Long verifiedBy = currentUser.user(auth).getId();
        ApplicationDocument doc = "VERIFIED".equalsIgnoreCase(req.status())
                ? documents.verify(documentId, verifiedBy)
                : documents.reject(documentId, verifiedBy, req.reason());
        return toResponse(doc);
    }

    private static DocumentResponse toResponse(ApplicationDocument d) {
        return new DocumentResponse(d.getId(), d.getDocType(), DocTypes.label(d.getDocType()), d.getFileName(),
                d.getVerificationStatus(), d.getRejectReason(), d.getUploadedAt());
    }
}
