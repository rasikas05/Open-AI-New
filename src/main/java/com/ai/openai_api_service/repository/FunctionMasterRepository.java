package com.ai.openai_api_service.repository;

import com.ai.openai_api_service.entity.FunctionMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface FunctionMasterRepository extends JpaRepository<FunctionMaster, Long> {
    Optional<FunctionMaster> findByMnidAndMnvrAndFnid(String mnid, String mnvr, String fnid);
    boolean existsByMnidAndMnvrAndFnid(String mnid, String mnvr, String fnid);
    List<FunctionMaster> findByFnidIn(List<String> fnids);
}
