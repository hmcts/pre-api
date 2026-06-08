package uk.gov.hmcts.reform.preapi.entities.base;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.MappedSuperclassType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.util.UUID;

@StaticMetamodel(BaseEntity.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class BaseEntity_ {

	public static final String ID = "id";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.base.BaseEntity#id
	 **/
	public static volatile SingularAttribute<BaseEntity, UUID> id;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.base.BaseEntity
	 **/
	public static volatile MappedSuperclassType<BaseEntity> class_;

}

