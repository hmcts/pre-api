package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;

@StaticMetamodel(User.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class User_ extends uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity_ {

	public static final String FIRST_NAME = "firstName";
	public static final String LAST_NAME = "lastName";
	public static final String DELETED_AT = "deletedAt";
	public static final String PHONE = "phone";
	public static final String USER_TERMS_ACCEPTED = "userTermsAccepted";
	public static final String PORTAL_ACCESS = "portalAccess";
	public static final String ORGANISATION = "organisation";
	public static final String APP_ACCESS = "appAccess";
	public static final String EMAIL = "email";
	public static final String ALTERNATIVE_EMAIL = "alternativeEmail";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.User#firstName
	 **/
	public static volatile SingularAttribute<User, String> firstName;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.User#lastName
	 **/
	public static volatile SingularAttribute<User, String> lastName;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.User#deletedAt
	 **/
	public static volatile SingularAttribute<User, Timestamp> deletedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.User#phone
	 **/
	public static volatile SingularAttribute<User, String> phone;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.User#userTermsAccepted
	 **/
	public static volatile SetAttribute<User, UserTermsAccepted> userTermsAccepted;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.User#portalAccess
	 **/
	public static volatile SetAttribute<User, PortalAccess> portalAccess;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.User#organisation
	 **/
	public static volatile SingularAttribute<User, String> organisation;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.User
	 **/
	public static volatile EntityType<User> class_;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.User#appAccess
	 **/
	public static volatile SetAttribute<User, AppAccess> appAccess;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.User#email
	 **/
	public static volatile SingularAttribute<User, String> email;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.User#alternativeEmail
	 **/
	public static volatile SingularAttribute<User, String> alternativeEmail;

}

