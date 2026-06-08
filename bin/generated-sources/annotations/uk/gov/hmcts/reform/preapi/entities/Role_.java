package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(Role.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class Role_ extends uk.gov.hmcts.reform.preapi.entities.base.BaseEntity_ {

	public static final String NAME = "name";
	public static final String DESCRIPTION = "description";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Role#name
	 **/
	public static volatile SingularAttribute<Role, String> name;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Role#description
	 **/
	public static volatile SingularAttribute<Role, String> description;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Role
	 **/
	public static volatile EntityType<Role> class_;

}

