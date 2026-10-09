package ph.thecoffeejunkie.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ph.thecoffeejunkie.crm.entity.CRMUser;

import java.util.List;
import java.util.Optional;

@Repository
public interface CRMUserRepository extends JpaRepository<CRMUser, Long> {
    Optional<CRMUser> findByEmail(String email);

    // roles is a comma-separated string; ADMIN, SALES and WAREHOUSE never contain one another.
    List<CRMUser> findByActiveTrueAndRolesContaining(String role);
}
