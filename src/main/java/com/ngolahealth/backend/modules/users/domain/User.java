package com.ngolahealth.backend.modules.users.domain;

import com.ngolahealth.backend.security.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Conta de utilizador. Nunca é apagada (auditoria): desactiva-se. */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false, length = 36)
  private UUID id;

  /** Hospital do utilizador; null só para SUPER_ADMIN. */
  @Column(name = "hospital_id", length = 36)
  private UUID hospitalId;

  @Column(name = "full_name", nullable = false, length = 200)
  private String fullName;

  @Column(nullable = false, unique = true, length = 50, updatable = false)
  private String username;

  @Column(nullable = false, unique = true, length = 200)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(length = 20)
  private String phone;

  @Column(length = 100)
  private String specialty;

  @Column(name = "professional_card_number", length = 50)
  private String professionalCardNumber;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false, length = 20)
  private UserStatus status = UserStatus.ACTIVE;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "role", nullable = false, length = 30)
  private Set<Role> roles = EnumSet.noneOf(Role.class);

  @Column(name = "must_change_password", nullable = false)
  private boolean mustChangePassword = true;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "password_changed_at")
  private Instant passwordChangedAt;

  @Version
  @Column(nullable = false)
  private long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Cria uma conta com senha temporária: a troca é obrigatória no primeiro acesso. */
  public User(
      String username, String passwordHash, UserProfile profile, UUID hospitalId, Set<Role> roles) {
    this.username = username;
    this.passwordHash = passwordHash;
    this.hospitalId = hospitalId;
    this.roles = EnumSet.copyOf(roles);
    apply(profile);
  }

  public void apply(UserProfile profile) {
    this.fullName = profile.fullName();
    this.email = profile.email();
    this.phone = profile.phone();
    this.specialty = profile.specialty();
    this.professionalCardNumber = profile.professionalCardNumber();
  }

  public void assign(UUID hospitalId, Set<Role> roles) {
    this.hospitalId = hospitalId;
    this.roles.clear();
    this.roles.addAll(roles);
  }

  public Set<Role> getRoles() {
    return roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
  }

  public boolean hasRole(Role role) {
    return roles.contains(role);
  }

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  public boolean isLocked(Instant now) {
    return lockedUntil != null && lockedUntil.isAfter(now);
  }

  public void activate() {
    this.status = UserStatus.ACTIVE;
  }

  public void deactivate() {
    this.status = UserStatus.INACTIVE;
  }

  /** Senha definida pelo próprio utilizador. */
  public void changePassword(String newHash, Instant now) {
    this.passwordHash = newHash;
    this.mustChangePassword = false;
    this.passwordChangedAt = now;
  }

  /** Senha temporária definida por um administrador: obriga a troca e desbloqueia a conta. */
  public void resetPassword(String temporaryHash, Instant now) {
    this.passwordHash = temporaryHash;
    this.mustChangePassword = true;
    this.passwordChangedAt = now;
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
  }
}
