package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

@StaticMetamodel(Court.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class Court_ extends uk.gov.hmcts.reform.preapi.entities.base.BaseEntity_ {

	public static final String COURT_TYPE = "courtType";
	public static final String GROUP_EMAIL = "groupEmail";
	public static final String REGIONS = "regions";
	public static final String NAME = "name";
	public static final String COUNTY = "county";
	public static final String POSTCODE = "postcode";
	public static final String LOCATION_CODE = "locationCode";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Court#courtType
	 **/
	public static volatile SingularAttribute<Court, CourtType> courtType;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Court#groupEmail
	 **/
	public static volatile SingularAttribute<Court, String> groupEmail;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Court#regions
	 **/
	public static volatile SetAttribute<Court, Region> regions;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Court#name
	 **/
	public static volatile SingularAttribute<Court, String> name;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Court#county
	 **/
	public static volatile SingularAttribute<Court, String> county;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Court#postcode
	 **/
	public static volatile SingularAttribute<Court, String> postcode;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Court#locationCode
	 **/
	public static volatile SingularAttribute<Court, String> locationCode;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Court
	 **/
	public static volatile EntityType<Court> class_;

}

