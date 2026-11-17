package ph.thecoffeejunkie.crm.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "product_category")
class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String name;
    Instant archivedAt;
    Instant createdAt = Instant.now();
    Instant updatedAt = Instant.now();
    @Version
    long version;
}
