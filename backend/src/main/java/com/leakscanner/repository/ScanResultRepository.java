package com.leakscanner.repository;

import com.leakscanner.model.Repository;
import com.leakscanner.model.ScanResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

@org.springframework.stereotype.Repository
public interface ScanResultRepository extends JpaRepository<ScanResult, Long> {
    List<ScanResult> findByRepositoryOrderByScanDateDesc(Repository repository);
    java.util.Optional<ScanResult> findTopByRepositoryAndScanStatusOrderByScanDateDesc(
            Repository repository, ScanResult.ScanStatus scanStatus);
}
