package GAGYELOL.repository;

import GAGYELOL.entity.Evidence;
import GAGYELOL.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceRepository extends JpaRepository<Evidence, Long> {
    List<Evidence> findByGroupOrderByCreatedAtDesc(UserGroup group);
}
