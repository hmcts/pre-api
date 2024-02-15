package uk.gov.hmcts.reform.preapi.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.reform.preapi.controllers.InviteController;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.InviteDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.InviteService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InviteController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class InviteControllerTest {

    @Autowired
    private transient MockMvc mockMvc;

    @MockBean
    private InviteService inviteService;

    @MockBean
    private UserAuthenticationService userAuthenticationService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEST_URL = "http://localhost";

    @DisplayName("Should get invite by user id with 200 response code")
    @Test
    void getInviteByUserIdSuccess() throws Exception {
        var userId = UUID.randomUUID();
        var mockInvite = new InviteDTO();
        mockInvite.setUserId(userId);

        when(inviteService.findByUserId(userId)).thenReturn(mockInvite);


        mockMvc.perform(get("/invites/" + userId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.user_id").value(userId.toString()));
    }

    @DisplayName("Should return 404 when trying to get an invite for a non-existing user")
    @Test
    void getInviteByUserIdNotFound() throws Exception {
        var userId = UUID.randomUUID();
        var mockInvite = new InviteDTO();
        mockInvite.setUserId(userId);

        doThrow(new NotFoundException("Invite: " + userId)).when(inviteService).findByUserId(userId);

        mockMvc.perform(get("/invites/" + userId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: Invite: " + userId));
    }

    @DisplayName("Should return a list of invites with 200 response code")
    @Test
    void getInvitesSuccess() throws Exception {
        var userId = UUID.randomUUID();
        var mockInvite = new InviteDTO();
        mockInvite.setUserId(userId);
        var inviteList = new PageImpl<>(List.of(mockInvite));
        when(inviteService.findAllBy(isNull(), isNull(), isNull(), isNull(), any()))
            .thenReturn(inviteList);

        mockMvc.perform(get("/invites"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.inviteDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.inviteDTOList[0].user_id").value(userId.toString()));
    }

    @DisplayName("Should delete invite with 200 response code")
    @Test
    void deleteInviteByUserIdSuccess() throws Exception {
        var userId = UUID.randomUUID();
        doNothing().when(inviteService).deleteByUserId(userId);

        mockMvc.perform(delete("/invites/" + userId)
            .with(csrf()))
            .andExpect(status().isOk());
    }

    @DisplayName("Should return 404 when deleting invite that doesn't exist")
    @Test
    void deleteInviteByUserIdNotFound() throws Exception {
        var userId = UUID.randomUUID();
        doThrow(new NotFoundException("Invite: " + userId))
            .when(inviteService).deleteByUserId(userId);

        mockMvc.perform(delete("/invites/" + userId)
            .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
            .value("Not found: Invite: " + userId));
    }

    @DisplayName("Should return 400 when user id is not a uuid")
    @Test
    void getInviteByUserIdBadRequest() throws Exception {
        mockMvc.perform(get("/invites/12345678")
            .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
            .value("Invalid UUID string: 12345678"));
    }

    @DisplayName("Requested page out of range")
    @Test
    void getInvitesRequestedPageOutOfRange() throws Exception {
        var userId = UUID.randomUUID();
        var mockInvite = new InviteDTO();
        mockInvite.setUserId(userId);
        var inviteList = new PageImpl<>(List.of(mockInvite));
        when(inviteService.findAllBy(isNull(), isNull(), isNull(), isNull(), any()))
            .thenReturn(inviteList);

        mockMvc.perform(get("/invites?page=5"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Requested page {5} is out of range. Max page is {1}"));
    }

    @DisplayName("Should fail to create/update an invite with 400 response code userId mismatch")
    @Test
    void createUserIdMismatch() throws Exception {
        var userId = UUID.randomUUID();
        var invite =  new CreateInviteDTO();
        invite.setUserId(userId);

        MvcResult response = mockMvc.perform(put("/invites/" + UUID.randomUUID())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(invite))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Path userId does not match payload property createInviteDTO.userId\"}");
    }

    @DisplayName("Should create an invite with 201 response code")
    @Test
    void createUserCreated() throws Exception {
        var userId = UUID.randomUUID();
        var invite =  new CreateInviteDTO();
        invite.setUserId(userId);

        when(inviteService.upsert(invite)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put("/invites/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(invite))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + "/invites/" + userId
        );
    }

    @DisplayName("Should redeem an invite with 204 response code")
    @Test
    void redeemInvite() throws Exception {
        var email = "example@example.com";
        var inviteCode = "ABCDEF";

        when(inviteService.redeemInvite(email, inviteCode)).thenReturn(UpsertResult.UPDATED);

        MvcResult response = mockMvc.perform(get("/invites/redeem" + "?email=" + email + "&inviteCode=" + inviteCode)
                                                 .with(csrf())
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(response.getResponse().getHeaderValue("Location"))
            .isEqualTo(TEST_URL + "/invites/redeem" + "?email=" + email + "&inviteCode=" + inviteCode);
    }
}
