package ph.thecoffeejunkie.crm.attachments;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import ph.thecoffeejunkie.crm.audit.AuditAction;
import ph.thecoffeejunkie.crm.audit.AuditLog;
import ph.thecoffeejunkie.crm.files.StorageService;
import ph.thecoffeejunkie.crm.identity.Users;
import ph.thecoffeejunkie.crm.shared.DomainException;
import ph.thecoffeejunkie.crm.shared.ErrorCode;
import ph.thecoffeejunkie.crm.shared.Scope;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Files on customers, orders and invoices (spec 2026-11-05 attachments). */
@Service
class AttachmentService {

    static final long MAX_BYTES = 20L * 1024 * 1024;
    static final int MAX_FILES = 10;

    private record Checked(String fileName, byte[] bytes, StorageService.Detected type) {
    }

    private final AttachmentRepository repo;
    private final AttachmentAccess access;
    private final StorageService storage;
    private final Users users;
    private final AuditLog audit;

    AttachmentService(AttachmentRepository repo, AttachmentAccess access, StorageService storage, Users users,
                      AuditLog audit) {
        this.repo = repo;
        this.access = access;
        this.storage = storage;
        this.users = users;
        this.audit = audit;
    }

    /** All or nothing: every file is checked before any is stored, and stored objects are removed on failure. */
    @Transactional
    List<AttachmentResponse> upload(Scope s, SubjectType type, long subjectId, String note, List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > MAX_FILES) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Choose 1 to " + MAX_FILES + " files.",
                    Map.of("field", "files"));
        }
        if (note != null && note.length() > 200) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Keep the note under 200 characters.",
                    Map.of("field", "note"));
        }
        access.requireWritable(s, type, subjectId);
        List<Checked> checked = new ArrayList<>();
        for (MultipartFile f : files) {
            String raw = f.getOriginalFilename();
            if (f.getSize() > MAX_BYTES) {
                throw new DomainException(ErrorCode.ATTACHMENT_TOO_LARGE, raw + ": over 20 MB.");
            }
            byte[] bytes = read(f);
            StorageService.Detected t = StorageService.detect(bytes, raw)
                    .orElseThrow(() -> new DomainException(ErrorCode.ATTACHMENT_TYPE, raw + ": this type isn't allowed. "
                            + "Use PDF, JPEG, PNG, WebP, Word (.docx), Excel (.xlsx) or CSV."));
            checked.add(new Checked(StorageService.cleanFileName(raw, t.ext()), bytes, t));
        }

        List<String> stored = new ArrayList<>();
        try {
            for (Checked c : checked) {
                String key = "attachments/" + type.keySegment() + "/" + subjectId + "/" + UUID.randomUUID() + c.type().ext();
                storage.put(key, c.bytes(), c.type().contentType());
                stored.add(key);
            }
        } catch (RuntimeException e) {
            stored.forEach(storage::deleteQuietly);
            throw e;
        }
        // A later rollback (a failing audit or insert) must not leave orphans either.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    stored.forEach(storage::deleteQuietly);
                }
            }
        });

        List<Attachment> rows = new ArrayList<>();
        for (int i = 0; i < checked.size(); i++) {
            Checked c = checked.get(i);
            Attachment a = new Attachment();
            a.subjectType = type;
            a.subjectId = subjectId;
            a.objectKey = stored.get(i);
            a.fileName = c.fileName();
            a.contentType = c.type().contentType();
            a.sizeBytes = c.bytes().length;
            a.note = note == null || note.isBlank() ? null : note.trim();
            a.uploadedBy = s.userId();
            repo.save(a);
            rows.add(a);
            audit.record(AuditAction.ATTACHMENT_ADDED, type.name(), subjectId, "Attached \"" + a.fileName + "\"", null);
        }
        repo.flush();
        return responses(s, rows);
    }

    @Transactional(readOnly = true)
    List<AttachmentResponse> list(Scope s, SubjectType type, long subjectId) {
        requireVisible(s, type, subjectId);
        return responses(s, repo.findBySubjectTypeAndSubjectIdInAndArchivedAtIsNullOrderByUploadedAtDescIdDesc(type,
                List.of(subjectId)));
    }

    /** The order page: the order's files and its invoices', newest first. */
    @Transactional(readOnly = true)
    List<AttachmentResponse> listForOrder(Scope s, long orderId) {
        requireVisible(s, SubjectType.ORDER, orderId);
        List<Attachment> rows = new ArrayList<>(repo.findBySubjectTypeAndSubjectIdInAndArchivedAtIsNullOrderByUploadedAtDescIdDesc(
                SubjectType.ORDER, List.of(orderId)));
        List<Long> invoices = access.invoiceIdsOfOrder(orderId);
        if (!invoices.isEmpty()) {
            rows.addAll(repo.findBySubjectTypeAndSubjectIdInAndArchivedAtIsNullOrderByUploadedAtDescIdDesc(
                    SubjectType.INVOICE, invoices));
        }
        rows.sort(Comparator.comparing((Attachment a) -> a.uploadedAt).thenComparing(a -> a.id).reversed());
        return responses(s, rows);
    }

    /** Archives; the uploader or an admin, on a writable record. Archiving twice is a no-op. */
    @Transactional
    void archive(Scope s, long id) {
        Attachment a = findVisible(s, id);
        if (a.archivedAt != null) {
            return;
        }
        access.requireWritable(s, a.subjectType, a.subjectId);
        if (!s.isAdmin() && a.uploadedBy != s.userId()) {
            throw new DomainException(ErrorCode.ATTACHMENT_NOT_YOURS, "Only the person who added this file, or an admin, "
                    + "can remove it.");
        }
        a.archivedAt = Instant.now();
        a.archivedBy = s.userId();
        audit.record(AuditAction.ATTACHMENT_REMOVED, a.subjectType.name(), a.subjectId, "Removed \"" + a.fileName + "\"",
                null);
    }

    /** Archived files stay downloadable by id, for the audit trail. */
    @Transactional(readOnly = true)
    String downloadUrl(Scope s, long id) {
        Attachment a = findVisible(s, id);
        return storage.presignedDownloadUrl(a.objectKey, a.fileName);
    }

    @Transactional(readOnly = true)
    String thumbnailUrl(Scope s, long id) {
        Attachment a = findVisible(s, id);
        if (!a.contentType.startsWith("image/")) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Not found");
        }
        return storage.presignedUrl(a.objectKey);
    }

    private Attachment findVisible(Scope s, long id) {
        return repo.findById(id).filter(a -> access.canSee(s, a.subjectType, a.subjectId))
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "Not found"));
    }

    private void requireVisible(Scope s, SubjectType type, long id) {
        if (!access.canSee(s, type, id)) {
            throw new DomainException(ErrorCode.NOT_FOUND, "Not found");
        }
    }

    private List<AttachmentResponse> responses(Scope s, List<Attachment> rows) {
        Map<Long, String> names = users.displayNames(rows.stream().map(a -> a.uploadedBy).distinct().toList());
        Map<String, Boolean> writable = new java.util.HashMap<>();
        return rows.stream().map(a -> {
            boolean mayRemove = (s.isAdmin() || a.uploadedBy == s.userId()) && writable.computeIfAbsent(
                    a.subjectType + ":" + a.subjectId, k -> isWritable(s, a.subjectType, a.subjectId));
            return new AttachmentResponse(a.id, a.subjectType, a.subjectId, a.fileName, a.contentType, a.sizeBytes, a.note,
                    a.uploadedBy, names.get(a.uploadedBy), a.uploadedAt, mayRemove);
        }).toList();
    }

    private boolean isWritable(Scope s, SubjectType type, long id) {
        try {
            access.requireWritable(s, type, id);
            return true;
        } catch (DomainException e) {
            return false;
        }
    }

    private static byte[] read(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file", e);
        }
    }

    Optional<Attachment> find(long id) {
        return repo.findById(id);
    }
}
