package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;

@StaticMetamodel(AppAccess.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class AppAccess_ extends uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity_ {

	public static final String DELETED_AT = "deletedAt";
	public static final String ROLE = "role";
	public static final String ACTIVE = "active";
	public static final String LAST_ACCESS = "lastAccess";
	public static final String DEFAULT_COURT = "defaultCourt";
	public static final String COURT = "court";
	public static final String USER = "user";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.AppAccess#deletedAt
	 **/
	public static volatile SingularAttribute<AppAccess, Timestamp> deletedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.AppAccess#role
	 **/
	public static volatile SingularAttribute<AppAccess, Role> role;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.AppAccess#active
	 **/
	public static volatile SingularAttribute<AppAccess, Boolean> active;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.AppAccess#lastAccess
	 **/
	public static volatile SingularAttribute<AppAccess, Timestamp> lastAccess;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.AppAccess#defaultCourt
	 **/
	public static volatile SingularAttribute<AppAccess, Boolean> defaultCourt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.AppAccess#court
	 **/
	public static volatile SingularAttribute<AppAccess, Court> court;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.AppAccess
	 **/
	public static volatile EntityType<AppAccess> class_;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.AppAccess#user
	 **/
	public static volatile SingularAttribute<AppAccess, User> user;

}

