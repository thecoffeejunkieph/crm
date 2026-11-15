package ph.thecoffeejunkie.crm.attachments;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "attachment")
class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Enumerated(EnumType.STRING)
    SubjectType subjectType;
    long subjectId;
    String objectKey;
    String fileName;
    String contentType;
    long sizeBytes;
    String note;
    long uploadedBy;
    Instant uploadedAt = Instant.now();
    Instant archivedAt;
    Long archivedBy;
}
