package ph.thecoffeejunkie.crm.approvals;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

interface ApprovalRequestRepository
        extends JpaRepository<ApprovalRequest, Long>, JpaSpecificationExecutor<ApprovalRequest> {

    Optional<ApprovalRequest> findByTypeAndSubjectTypeAndSubjectIdAndSubjectVersionAndStatus(
            ApprovalType type, String subjectType, long subjectId, long subjectVersion, ApprovalStatus status);

    boolean existsByTypeAndSubjectTypeAndSubjectIdAndSubjectVersionAndStatus(
            ApprovalType type, String subjectType, long subjectId, long subjectVersion, ApprovalStatus status);
}
