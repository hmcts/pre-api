package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;

@StaticMetamodel(Booking.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class Booking_ extends uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity_ {

	public static final String SHARES = "shares";
	public static final String DELETED_AT = "deletedAt";
	public static final String CASE_ID = "caseId";
	public static final String SCHEDULED_FOR = "scheduledFor";
	public static final String CAPTURE_SESSIONS = "captureSessions";
	public static final String PARTICIPANTS = "participants";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Booking#shares
	 **/
	public static volatile SetAttribute<Booking, ShareBooking> shares;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Booking#deletedAt
	 **/
	public static volatile SingularAttribute<Booking, Timestamp> deletedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Booking#caseId
	 **/
	public static volatile SingularAttribute<Booking, Case> caseId;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Booking#scheduledFor
	 **/
	public static volatile SingularAttribute<Booking, Timestamp> scheduledFor;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Booking#captureSessions
	 **/
	public static volatile SetAttribute<Booking, CaptureSession> captureSessions;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Booking
	 **/
	public static volatile EntityType<Booking> class_;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Booking#participants
	 **/
	public static volatile SetAttribute<Booking, Participant> participants;

}

