package GAGYELOL.repository;

import GAGYELOL.entity.Form;
import GAGYELOL.entity.UserGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormRepository extends JpaRepository<Form, Long> {
    List<Form> findByGroup(UserGroup group);
    List<Form> findByGroupAndPaymentTypeIn(UserGroup group, List<String> paymentTypes);
}
