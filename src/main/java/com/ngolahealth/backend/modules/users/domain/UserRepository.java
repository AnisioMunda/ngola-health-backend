package com.ngolahealth.backend.modules.users.domain;

import com.ngolahealth.backend.security.Role;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, UUID> {

  @Query("SELECT u FROM User u WHERE u.username = :login OR u.email = :login")
  Optional<User> findByLogin(@Param("login") String login);

  Optional<User> findByUsername(String username);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  boolean existsByEmailAndIdNot(String email, UUID id);

  /**
   * Pesquisa paginada. {@code hospitalId} null = todos os hospitais (só para SUPER_ADMIN); {@code
   * pattern} segue a mesma convenção da pesquisa de hospitais (minúsculas, escapado, com %).
   */
  @Query(
      value =
          """
          SELECT u FROM User u
          WHERE (:hospitalId IS NULL OR u.hospitalId = :hospitalId)
            AND (:status IS NULL OR u.status = :status)
            AND (:role IS NULL OR :role MEMBER OF u.roles)
            AND (:pattern IS NULL
                 OR LOWER(u.fullName) LIKE :pattern ESCAPE '\\'
                 OR LOWER(u.username) LIKE :pattern ESCAPE '\\'
                 OR LOWER(u.email) LIKE :pattern ESCAPE '\\')
          """)
  Page<User> search(
      @Param("hospitalId") UUID hospitalId,
      @Param("status") UserStatus status,
      @Param("role") Role role,
      @Param("pattern") String pattern,
      Pageable pageable);

  /**
   * Regista uma tentativa falhada fora da transacção do login (que falha), para não se perder. Não
   * incrementa a versão: não é uma edição do utilizador.
   */
  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      "UPDATE User u SET u.failedLoginAttempts = :attempts, u.lockedUntil = :lockedUntil"
          + " WHERE u.id = :id")
  void recordFailedLogin(
      @Param("id") UUID id,
      @Param("attempts") int attempts,
      @Param("lockedUntil") Instant lockedUntil);

  @Modifying(clearAutomatically = true)
  @Transactional
  @Query(
      "UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = NULL, u.lastLoginAt = :now"
          + " WHERE u.id = :id")
  void recordSuccessfulLogin(@Param("id") UUID id, @Param("now") Instant now);
}
