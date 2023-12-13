package uk.gov.hmcts.reform.preapi.repositories;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

@SuppressWarnings({"checkstyle:InterfaceTypeParameterName","PMD.GenericsNaming"})
@NoRepositoryBean
public interface SoftDeleteRepository<T,ID> extends JpaRepository<T, ID> {

    @Override
    @Query("update #{#entityName} e set e.deletedAt=CURRENT_TIMESTAMP where e.id=:id")
    @Modifying
    @Transactional
    void deleteById(ID id);
}
