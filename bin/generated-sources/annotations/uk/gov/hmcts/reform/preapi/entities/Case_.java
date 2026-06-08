package uk.gov.hmcts.reform.preapi.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SetAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;
import java.sql.Timestamp;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;

@StaticMetamodel(Case.class)
@Generated("org.hibernate.processor.HibernateProcessor")
public abstract class Case_ extends uk.gov.hmcts.reform.preapi.entities.base.CreatedModifiedAtEntity_ {

	public static final String REFERENCE = "reference";
	public static final String DELETED_AT = "deletedAt";
	public static final String TEST = "test";
	public static final String ORIGIN = "origin";
	public static final String STATE = "state";
	public static final String COURT = "court";
	public static final String CLOSED_AT = "closedAt";
	public static final String PARTICIPANTS = "participants";

	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Case#reference
	 **/
	public static volatile SingularAttribute<Case, String> reference;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Case#deletedAt
	 **/
	public static volatile SingularAttribute<Case, Timestamp> deletedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Case#test
	 **/
	public static volatile SingularAttribute<Case, Boolean> test;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Case#origin
	 **/
	public static volatile SingularAttribute<Case, RecordingOrigin> origin;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Case#state
	 **/
	public static volatile SingularAttribute<Case, CaseState> state;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Case#court
	 **/
	public static volatile SingularAttribute<Case, Court> court;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Case#closedAt
	 **/
	public static volatile SingularAttribute<Case, Timestamp> closedAt;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Case
	 **/
	public static volatile EntityType<Case> class_;
	
	/**
	 * @see uk.gov.hmcts.reform.preapi.entities.Case#participants
	 **/
	public static volatile SetAttribute<Case, Participant> participants;

}

