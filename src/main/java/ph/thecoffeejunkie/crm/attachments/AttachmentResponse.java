package ph.thecoffeejunkie.crm.attachments;

import java.time.Instant;

record AttachmentResponse(long id, SubjectType subjectType, long subjectId, String fileName, String contentType,
                          long sizeBytes, String note, long uploadedBy, String uploadedByName, Instant uploadedAt,
                          boolean canRemove) {
}
