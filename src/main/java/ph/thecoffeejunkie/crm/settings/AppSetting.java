package ph.thecoffeejunkie.crm.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "app_setting")
class AppSetting {

    @Id
    @Column(name = "key")
    String key;
    /** Number or string as stored in jsonb. */
    @JdbcTypeCode(SqlTypes.JSON)
    Object value;
    // updated_by stays null until users exist (identity task); the FK targets app_user.
    Instant updatedAt = Instant.now();
}
