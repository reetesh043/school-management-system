package com.school.admission.service;

import com.school.admission.config.AppProperties;
import com.school.admission.domain.ApplicationDocument;
import com.school.admission.domain.ApplicationDocumentRepository;
import com.school.admission.support.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Files are written to a local folder to keep the demo self-contained. In a real deployment this becomes an
 * S3 pre-signed upload, with a virus scan updating scan_status before verification is allowed.
 */
@Service
public class DocumentService {

    private static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final long MAX_BYTES = 5 * 1024 * 1024;

    private final ApplicationDocumentRepository documents;
    private final AppProperties props;

    public DocumentService(ApplicationDocumentRepository documents, AppProperties props) {
        this.documents = documents;
        this.props = props;
    }

    @Transactional
    public ApplicationDocument store(Long applicationId, String docType, String fileName, String contentType,
                                     long size, InputStream data) {
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessRuleException("Only PDF, JPEG or PNG files are accepted.");
        }
        if (size > MAX_BYTES) {
            throw new BusinessRuleException("Files must be 5 MB or smaller.");
        }
        try {
            Path dir = Path.of(props.uploadDir(), String.valueOf(applicationId));
            Files.createDirectories(dir);
            String storageKey = UUID.randomUUID() + "-" + fileName;
            Files.copy(data, dir.resolve(storageKey), StandardCopyOption.REPLACE_EXISTING);

            ApplicationDocument doc = new ApplicationDocument();
            doc.setApplicationId(applicationId);
            doc.setDocType(docType);
            doc.setFileName(fileName);
            doc.setContentType(contentType);
            doc.setSizeBytes(size);
            doc.setStorageKey(dir.resolve(storageKey).toString());
            doc.setVerificationStatus("UPLOADED");
            return documents.save(doc);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store the uploaded file", e);
        }
    }

    @Transactional
    public ApplicationDocument verify(Long documentId, Long verifiedBy) {
        ApplicationDocument doc = get(documentId);
        doc.setVerificationStatus("VERIFIED");
        doc.setVerifiedById(verifiedBy);
        doc.setVerifiedAt(Instant.now());
        doc.setRejectReason(null);
        return doc;
    }

    @Transactional
    public ApplicationDocument reject(Long documentId, Long verifiedBy, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("A reason is required to reject a document.");
        }
        ApplicationDocument doc = get(documentId);
        doc.setVerificationStatus("REJECTED");
        doc.setVerifiedById(verifiedBy);
        doc.setVerifiedAt(Instant.now());
        doc.setRejectReason(reason);
        return doc;
    }

    @Transactional(readOnly = true)
    public List<ApplicationDocument> forApplication(Long applicationId) {
        return documents.findByApplicationId(applicationId);
    }

    private ApplicationDocument get(Long id) {
        return documents.findById(id).orElseThrow(() -> new java.util.NoSuchElementException("Document not found"));
    }
}
