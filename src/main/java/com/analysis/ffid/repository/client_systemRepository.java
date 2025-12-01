package com.analysis.ffid.repository;

import com.analysis.ffid.model.client_system;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface client_systemRepository extends JpaRepository<client_system, String> {
    Optional<client_system> findByClientAndSystem(String client, String system);
}