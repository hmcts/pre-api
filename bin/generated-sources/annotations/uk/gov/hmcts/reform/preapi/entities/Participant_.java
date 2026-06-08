package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;

@StaticMetamodel(Participant.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class Participant_ extends uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity_ {

	public static final String FIRST_NAME = "firstName";
	public static final String LAST_NAME = "lastName";
	public static final String DELETED_AT = "deletedAt";
	public static final String CASE_ID = "caseId";
	public static final String PARTICIPANT_TYPE = "participantType";
	public static final String BOOKINGS = "bookings";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Participant#firstName
	 **/
	public static volatile SingularAttribute<Participant, String> firstName;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Participant#lastName
	 **/
	public static volatile SingularAttribute<Participant, String> lastName;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Participant#deletedAt
	 **/
	public static volatile SingularAttribute<Participant, Timestamp> deletedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Participant#caseId
	 **/
	public static volatile SingularAttribute<Participant, Case> caseId;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Participant#participantType
	 **/
	public static volatile SingularAttribute<Participant, ParticipantType> participantType;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Participant#bookings
	 **/
	public static volatile SetAttribute<Participant, Booking> bookings;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Participant
	 **/
	public static volatile EntityType<Participant> class_;

}

