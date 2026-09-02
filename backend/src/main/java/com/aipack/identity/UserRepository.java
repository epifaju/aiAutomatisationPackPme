package com.aipack.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u JOIN FETCH u.company WHERE u.id = :id")
    Optional<User> findByIdWithCompany(@Param("id") UUID id);

    @Query("SELECT u FROM User u JOIN FETCH u.company WHERE lower(u.email) = lower(:email)")
    List<User> findAllByEmailIgnoreCase(@Param("email") String email);
}
