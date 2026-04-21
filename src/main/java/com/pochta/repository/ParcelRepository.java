package com.pochta.repository;

import com.pochta.model.Parcel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParcelRepository extends JpaRepository<Parcel, Long> {
    List<Parcel> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user"})
    List<Parcel> findAll();

    @EntityGraph(attributePaths = {"user"})
    List<Parcel> findByParcelNumberContainingIgnoreCase(String parcelNumber);
    List<Parcel> findByFromBranchAndToBranchAndStatus(String from, String to, com.pochta.model.ParcelStatus status);
}