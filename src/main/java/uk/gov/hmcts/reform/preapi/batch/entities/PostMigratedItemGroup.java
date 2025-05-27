package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostMigratedItemGroup {
    private List<CreateShareBookingDTO> shareBookings;
    private List<CreateInviteDTO> invites;

    @Override
    public String toString() {
        return "PostMigratedItemGroup{" 
            + "shareBookings=" + (shareBookings != null ? shareBookings.toString() : "null") 
            + ", invites=" + (invites != null ? invites.toString() : "null") 
            + '}';
    }
}
