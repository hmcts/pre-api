package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(Region.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class Region_ extends uk.gov.hmcts.reform.preapi.entities.base.BaseEntity_ {

	public static final String NAME = "name";
	public static final String COURTS = "courts";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Region#name
	 **/
	public static volatile SingularAttribute<Region, String> name;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Region#courts
	 **/
	public static volatile SetAttribute<Region, Court> courts;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Region
	 **/
	public static volatile EntityType<Region> class_;

}

