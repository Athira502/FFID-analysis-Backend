package com.analysis.ffid.repository;

import com.analysis.ffid.model.analysis_result;
import com.analysis.ffid.model.request_details;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface analysis_resultRepository extends JpaRepository<analysis_result, Long> {

    List<analysis_result> findByRequestDetails_AnalysisID(String analysisId);
    List<analysis_result> findByRequestDetails(request_details requestDetails);

    analysis_result findTopByRequestDetails_AnalysisIDOrderByResultIDDesc(String analysisId);
}