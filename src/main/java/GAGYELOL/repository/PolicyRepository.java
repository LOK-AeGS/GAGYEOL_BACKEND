package GAGYELOL.repository;

import GAGYELOL.entity.Policy;
import GAGYELOL.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    List<Policy> findByGroup(UserGroup group);
}
