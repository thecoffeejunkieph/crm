package ph.thecoffeejunkie.crm.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmailAndArchivedAtIsNull(String email);
}
