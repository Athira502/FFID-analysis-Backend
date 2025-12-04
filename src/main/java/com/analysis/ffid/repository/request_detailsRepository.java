package com.analysis.ffid.repository;

import com.analysis.ffid.model.request_details;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface request_detailsRepository extends JpaRepository<request_details, String> {

    @Query("SELECT r FROM request_details r " +
            "LEFT JOIN FETCH r.sm20Entries " +
            "LEFT JOIN FETCH r.cdhdrEntries " +
            "LEFT JOIN FETCH r.transactionUsages " +
            "WHERE r.analysisID = :analysisID")
    Optional<request_details> findByIdWithRelations(String analysisID);
}

