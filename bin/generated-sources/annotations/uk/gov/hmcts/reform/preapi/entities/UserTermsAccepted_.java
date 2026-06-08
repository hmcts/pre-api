package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;

@StaticMetamodel(UserTermsAccepted.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class UserTermsAccepted_ extends uk.gov.hmcts.reform.preapi.entities.base.BaseEntity_ {

	public static final String USER = "user";
	public static final String TERMS_AND_CONDITIONS = "termsAndConditions";
	public static final String ACCEPTED_AT = "acceptedAt";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.UserTermsAccepted
	 **/
	public static volatile EntityType<UserTermsAccepted> class_;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.UserTermsAccepted#user
	 **/
	public static volatile SingularAttribute<UserTermsAccepted, User> user;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.UserTermsAccepted#termsAndConditions
	 **/
	public static volatile SingularAttribute<UserTermsAccepted, TermsAndConditions> termsAndConditions;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.UserTermsAccepted#acceptedAt
	 **/
	public static volatile SingularAttribute<UserTermsAccepted, Timestamp> acceptedAt;

}

