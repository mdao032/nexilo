package com.nexilo.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "nexilo_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    /** Plan d'abonnement actif. Défaut : FREE. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserPlan plan = UserPlan.FREE;

    /** Date d'expiration du plan (null = pas d'expiration / gratuit permanent). */
    @Column(name = "plan_expires_at")
    private LocalDateTime planExpiresAt;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.plan == null) this.plan = UserPlan.FREE;
    }
}
