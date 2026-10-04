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
 * <p>The stable Vision UUID remains the domain identity and is distinct from
 * the optional Firebase UID mapped to this account by AD-082.
 */
@Entity
@Table(name = "app_user")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "firebase_uid", unique = true, length = 128)
    private String firebaseUid;

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

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public void setDisplayName(String displayName) {
        Objects.requireNonNull(displayName, "displayName cannot be null");
        this.displayName = displayName.strip();
    }
}
