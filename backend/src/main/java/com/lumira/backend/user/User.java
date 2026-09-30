package com.lumira.backend.user;

import com.lumira.backend.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Internal user identity entity (AD-019/AD-048).
 *
 * <p>Stable {@code userId} decoupled from any auth-provider identity.
 * The full token/session architecture is explicitly deferred per the
 * red-team reconciliation in {@code docs/DECISIONS.md}; this entity
 * provides the minimum identity needed to attribute artifacts to a user.
 */
@Entity
@Table(name = "\"user\"")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    protected User() {
        // JPA
    }

    public User(String email, String displayName) {
        Objects.requireNonNull(email, "email cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        this.email = email.strip().toLowerCase();
        this.displayName = displayName.strip();
    }

    /** Test/restore constructor — sets id directly. */
    User(UUID id, String email, String displayName) {
        super(id);
        this.email = email.strip().toLowerCase();
        this.displayName = displayName.strip();
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        Objects.requireNonNull(displayName, "displayName cannot be null");
        this.displayName = displayName.strip();
    }
}
