package ph.thecoffeejunkie.crm.warehouses;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    List<Warehouse> findByArchivedAtIsNullOrderByCodeAsc();

    Optional<Warehouse> findByIsDefaultTrueAndArchivedAtIsNull();

    long countByIdInAndArchivedAtIsNull(Collection<Long> ids);

    @Query("select w from Warehouse w where lower(w.code) = lower(:code) and w.archivedAt is null")
    Optional<Warehouse> findActiveByCode(String code);
}
