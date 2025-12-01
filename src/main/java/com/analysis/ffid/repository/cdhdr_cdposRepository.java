package com.analysis.ffid.repository;

import com.analysis.ffid.model.cdhdr_cdpos;
import com.analysis.ffid.model.request_details;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface cdhdr_cdposRepository extends JpaRepository<cdhdr_cdpos, Long> {

    List<cdhdr_cdpos> findByRequestDetails(request_details requestDetails);

    List<cdhdr_cdpos> findByDocNumberAndRequestDetails(String doc_number, request_details requestDetails);

    @Query("SELECT c FROM cdhdr_cdpos c WHERE c.requestDetails = :request AND c.tableName IS NOT NULL")
    List<cdhdr_cdpos> findRecordsWithCdposData(@Param("request") request_details request);

    @Query("SELECT c FROM cdhdr_cdpos c WHERE c.requestDetails = :request AND c.tableName IS NULL")
    List<cdhdr_cdpos> findRecordsWithOnlyCdhdrData(@Param("request") request_details request);

    long countByRequestDetails(request_details requestDetails);

    @Query("SELECT COUNT(c) FROM cdhdr_cdpos c WHERE c.requestDetails = :request AND c.tableName IS NOT NULL")
    long countRecordsWithCdposData(@Param("request") request_details request);

    @Modifying
    @Query("DELETE FROM cdhdr_cdpos c WHERE c.requestDetails = :request")
    void deleteByRequestDetails(@Param("request") request_details request);

    List<cdhdr_cdpos> findByDocNumber(String doc_number);

    boolean existsByRequestDetails(request_details requestDetails);

    @Query("SELECT c FROM cdhdr_cdpos c WHERE c.requestDetails = :request AND c.docNumber IN :docNumbers")
    List<cdhdr_cdpos> findByRequestDetailsAndDocNumberIn(
            @Param("request") request_details request,
            @Param("docNumbers") List<String> docNumbers);

    @Query("SELECT DISTINCT c.docNumber FROM cdhdr_cdpos c WHERE c.requestDetails = :request")
    List<String> findDistinctDocNumbersByRequestDetails(@Param("request") request_details request);
}


