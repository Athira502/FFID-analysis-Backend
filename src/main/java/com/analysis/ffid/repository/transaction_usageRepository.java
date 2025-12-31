package com.analysis.ffid.repository;

import com.analysis.ffid.model.request_details;
import com.analysis.ffid.model.transaction_usage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface transaction_usageRepository extends JpaRepository<transaction_usage, Long> {
    List<transaction_usage> findByRequestDetails(request_details requestDetails);
}