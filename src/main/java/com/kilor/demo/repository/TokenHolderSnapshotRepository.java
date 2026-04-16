package com.kilor.demo.repository;

import com.kilor.demo.entity.TokenHolderSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TokenHolderSnapshotRepository extends JpaRepository<TokenHolderSnapshot, Long> {

    List<TokenHolderSnapshot> findByContractAddressAndSnapshotDateBetween(
            String contractAddress, LocalDate startDate, LocalDate endDate);

    Optional<TokenHolderSnapshot> findTopByContractAddressOrderBySnapshotDateDesc(String contractAddress);

    List<TokenHolderSnapshot> findByContractAddressAndSnapshotDate(
            String contractAddress, LocalDate snapshotDate);
}
