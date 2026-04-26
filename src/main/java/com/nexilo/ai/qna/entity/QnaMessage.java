package com.nexilo.ai.qna.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "qna_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QnaMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @PrePersist
    protected void onCreate() { this.createdAt = Instant.now(); }
    public enum Role { USER, ASSISTANT }
}
