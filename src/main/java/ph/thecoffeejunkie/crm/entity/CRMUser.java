package ph.thecoffeejunkie.crm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@ToString
@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "users")
public class CRMUser {

    @Id
    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "is_active")
    private Boolean active;

    @Column(name = "roles", nullable = false)
    private String roles;

    private String firstName;
    private String lastName;
    private String cellphoneNumber;
    private String address;
    private LocalDate birthday;

    private int rewardPoints;

    private String qrCodePath;
}