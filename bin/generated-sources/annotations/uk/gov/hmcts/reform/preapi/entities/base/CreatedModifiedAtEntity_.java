package uk.gov.hmcts.reform.preapi.entities.base;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.MappedSuperclassType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;

@StaticMetamodel(CreatedModifiedAtEntity.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class CreatedModifiedAtEntity_ extends uk.gov.hmcts.reform.preapi.entities.base.BaseEntity_ {

	public static final String CREATED_AT = "createdAt";
	public static final String MODIFIED_AT = "modifiedAt";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity#createdAt
	 **/
	public static volatile SingularAttribute<CreatedModifiedAtEntity, Timestamp> createdAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity#modifiedAt
	 **/
	public static volatile SingularAttribute<CreatedModifiedAtEntity, Timestamp> modifiedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity
	 **/
	public static volatile MappedSuperclassType<CreatedModifiedAtEntity> class_;

}

