package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;

@StaticMetamodel(ShareBooking.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class ShareBooking_ extends uk.gov.hmcts.reform.preapi.entities.base.BaseEntity_ {

	public static final String CREATED_AT = "createdAt";
	public static final String BOOKING = "booking";
	public static final String DELETED_AT = "deletedAt";
	public static final String SHARED_BY = "sharedBy";
	public static final String SHARED_WITH = "sharedWith";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.ShareBooking#createdAt
	 **/
	public static volatile SingularAttribute<ShareBooking, Timestamp> createdAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.ShareBooking#booking
	 **/
	public static volatile SingularAttribute<ShareBooking, Booking> booking;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.ShareBooking#deletedAt
	 **/
	public static volatile SingularAttribute<ShareBooking, Timestamp> deletedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.ShareBooking#sharedBy
	 **/
	public static volatile SingularAttribute<ShareBooking, User> sharedBy;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.ShareBooking#sharedWith
	 **/
	public static volatile SingularAttribute<ShareBooking, User> sharedWith;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.ShareBooking
	 **/
	public static volatile EntityType<ShareBooking> class_;

}

