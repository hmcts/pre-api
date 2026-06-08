package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;

@StaticMetamodel(PortalAccess.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class PortalAccess_ extends uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity_ {

	public static final String DELETED_AT = "deletedAt";
	public static final String REGISTERED_AT = "registeredAt";
	public static final String LAST_ACCESS = "lastAccess";
	public static final String USER = "user";
	public static final String STATUS = "status";
	public static final String INVITED_AT = "invitedAt";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.PortalAccess#deletedAt
	 **/
	public static volatile SingularAttribute<PortalAccess, Timestamp> deletedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.PortalAccess#registeredAt
	 **/
	public static volatile SingularAttribute<PortalAccess, Timestamp> registeredAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.PortalAccess#lastAccess
	 **/
	public static volatile SingularAttribute<PortalAccess, Timestamp> lastAccess;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.PortalAccess
	 **/
	public static volatile EntityType<PortalAccess> class_;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.PortalAccess#user
	 **/
	public static volatile SingularAttribute<PortalAccess, User> user;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.PortalAccess#status
	 **/
	public static volatile SingularAttribute<PortalAccess, AccessStatus> status;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.PortalAccess#invitedAt
	 **/
	public static volatile SingularAttribute<PortalAccess, Timestamp> invitedAt;

}

