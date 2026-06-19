package org.example.chessserver.repository;

import org.example.chessserver.entity.EloRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EloRatingRepository extends JpaRepository<EloRating, Integer> {
    List<EloRating> findAllByOrderByRatingDesc();
}
