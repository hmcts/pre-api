Temporary notes for @Ruth.Bovell to write edit request functional tests

# Edit request journey:

####
# Still manual but we're about to make it automated. Need functional tests to cover this half as they are non-existent.
####

## DRAFT
Being worked on by advocate.
Current: Sent by email to court group email address.
Will become: form submitted on portal

## SUBMITTED
Gets to court clerk.

Email is forwarded by court clerk to judge. (This will not change - judges will not be on PRE.)

Court clerk awaits judgement.

Email notification should be received by court group email and by shared-with users.

## APPROVED or REJECTED
This is the judge's decision

Current: Court clerk emails the editor email address. This used to be Colin Clixby and is now 2LS.

Will become: Court clerk enters the decision via Power Platform.

Email notification should be received by court group email and by shared-with users.


####
# This bit is automated. Entry point: portal form to submit CSV. Some functional tests exist but we need extra regression tests for S28-5018.
####

## PENDING
Waiting for cron job to pick it up.

I'm not sure how it gets from APPROVED to PENDING - need to check the code.

Current: 2LS submit the CSV form via the Portal which hits the temporary API endpoint. This creates the edit requests as PENDING.

## PROCESSING
Cron job is creating an edited recording

## COMPLETE or ERROR
Edits have been successfully or unsuccessfully applied



