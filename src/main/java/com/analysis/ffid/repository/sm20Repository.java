package com.analysis.ffid.repository;
import com.analysis.ffid.model.request_details;
import com.analysis.ffid.model.sm20;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface sm20Repository extends JpaRepository<sm20, Long> {
        List<sm20> findByRequestDetails(request_details requestDetails);
    }
