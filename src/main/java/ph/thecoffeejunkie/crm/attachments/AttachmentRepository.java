package ph.thecoffeejunkie.crm.attachments;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findBySubjectTypeAndSubjectIdInAndArchivedAtIsNullOrderByUploadedAtDescIdDesc(
            SubjectType type, Collection<Long> ids);
}
