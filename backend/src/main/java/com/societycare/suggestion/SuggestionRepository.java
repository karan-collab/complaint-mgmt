package com.societycare.suggestion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    /**
     * The admin list shows the resident's name, flat and phone on every row, so
     * the resident is fetched with the suggestion. Left lazy it would be one
     * extra query per row.
     */
    // The id breaks ties on created_at. Two suggestions written in the same
    // millisecond would otherwise come back in whatever order the database felt
    // like, which is a real possibility on a fast machine and makes "newest
    // first" untestable.
    @Query("select s from Suggestion s join fetch s.resident "
            + "order by s.createdAt desc, s.suggestionId desc")
    List<Suggestion> findAllNewestFirst();

    /** Everything raised in a half-open window, for the management report. */
    @Query("select s from Suggestion s join fetch s.resident "
            + "where s.createdAt >= :from and s.createdAt < :toExclusive "
            + "order by s.createdAt asc, s.suggestionId asc")
    List<Suggestion> findRaisedBetween(@Param("from") OffsetDateTime from,
                                       @Param("toExclusive") OffsetDateTime toExclusive);

    /** Used when an admin deletes a resident: their suggestions go with them. */
    void deleteByResident_ResidentId(Long residentId);
}
