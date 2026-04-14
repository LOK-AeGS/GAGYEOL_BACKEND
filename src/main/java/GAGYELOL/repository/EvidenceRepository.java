package GAGYELOL.repository;

import GAGYELOL.entity.Evidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {

    @Modifying
    @Query("UPDATE Evidence e SET e.selectedFormIds = :formIds WHERE e.id = :id")
    void updateSelectedFormIds(@Param("id") Long id, @Param("formIds") String formIds);
}
